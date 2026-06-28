package com.los.core.service.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.AiLosOpenResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditControlService;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiLosIntegrationService {

    private static final String DEMO_AI_LOS_URL =
            "https://ai-los.billiontech.ai/loan-review/TEST001";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LoanApplicationRepository loanApplicationRepository;
    private final IntegrationProperties integrationProperties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final CreditControlService creditControlService;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        IntegrationProperties.AiLosProperties p = integrationProperties.getAiLos();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, p.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(2000, p.getReadTimeoutMs()));
        this.restTemplate = new RestTemplate(factory);
        log.info("AI LOS client initialized: endpoint={}, connectTimeoutMs={}, readTimeoutMs={}",
                p.getIngestUrl(), p.getConnectTimeoutMs(), p.getReadTimeoutMs());
    }

    public AiLosOpenResponse initiateOpen(UUID applicationId, String returnUrl, String modeRaw) {
        log.info("AI LOS initiateOpen: applicationId={}, returnUrl={}, mode={}", applicationId, returnUrl, modeRaw);

        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        String mode = normalizeMode(modeRaw);
        String loanRef = app.getApplicationNumber() != null ? app.getApplicationNumber() : app.getId().toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> effective = readEffectiveContext(app);
        String borrowerName = sanitizeBorrowerName(firstNonBlank(
                asString(app.getPersonalInfo(), "fullName"),
                joinNameParts(asString(app.getPersonalInfo(), "firstName"), asString(app.getPersonalInfo(), "lastName")),
                asString(app.getPersonalInfo(), "name"),
                asString(app.getPersonalInfo(), "borrowerName"),
                "UNKNOWN"), app);
        BigDecimal loanAmount = app.getSanctionedAmount() != null ? app.getSanctionedAmount()
                : app.getRequestedAmount() != null ? app.getRequestedAmount() : BigDecimal.ZERO;
        Integer creditScore = resolveCreditScore(app, effective);
        BigDecimal annualIncome = resolveAnnualIncome(app, effective);
        Integer existingEmi = resolveExistingEmi(app, effective);
        String originalEmploymentType = firstNonBlank(
                asString(effective, "incomeType"),
                asString(app.getPersonalInfo(), "employmentType"),
                asString(app.getBusinessInfo(), "employmentType"),
                "");
        String employmentType = normalizeEmploymentType(originalEmploymentType);
        log.info("AI LOS employment type mapped: original={}, final={}",
                originalEmploymentType, employmentType);
        Integer tenureMonths = app.getTenureMonths() != null ? app.getTenureMonths() : 0;
        BigDecimal propertyValue = resolvePropertyValue(app, effective);
        String purpose = firstNonBlank(
                asString(app.getPersonalInfo(), "purpose"),
                asString(app.getBusinessInfo(), "loanPurpose"),
                "");

        AiLosIngestRequest payload = AiLosIngestRequest.builder()
                .loanId(loanRef)
                .borrowerName(borrowerName.isBlank() ? "Borrower" : borrowerName)
                .loanAmount(loanAmount.compareTo(BigDecimal.ZERO) > 0 ? loanAmount : BigDecimal.valueOf(100000))
                .loanPurpose(purpose.isBlank() ? "Working Capital" : purpose)
                .creditScore(creditScore > 0 ? creditScore : 650)
                .annualIncome(annualIncome.compareTo(BigDecimal.ZERO) > 0 ? annualIncome : BigDecimal.valueOf(240000))
                .employmentType(employmentType)
                .loanTenureMonths(tenureMonths > 0 ? tenureMonths : 12)
                .existingEmi(existingEmi != null && existingEmi > 0 ? existingEmi : 15000)
                .propertyValue(propertyValue.compareTo(BigDecimal.ZERO) > 0 ? propertyValue : BigDecimal.valueOf(100000))
                .sourceLos(integrationProperties.getAiLos().getSourceLos())
                .sourceLoanRef(loanRef)
                .build();

        auditService.logEvent(
                applicationId,
                "AI_LOS",
                "AI_LOS_REQUEST_INITIATED",
                null,
                null,
                Map.of("loanRef", loanRef, "mode", mode),
                "AI LOS ingest initiated from underwriting"
        );

        IntegrationProperties.AiLosProperties p = integrationProperties.getAiLos();
        long startedAtMs = System.currentTimeMillis();
        try {
            JwtEnvelope jwtEnvelope = generateIntegrationJwt();
            log.info("AI LOS JWT generated successfully: algorithm=HS256, payloadSub={}, payloadRole={}, expiry={}, tokenPreview={}",
                    p.getJwtSubject(),
                    p.getJwtRole(),
                    jwtEnvelope.expiry(),
                    maskToken(jwtEnvelope.token()));

            String requestBody = objectMapper.writeValueAsString(payload);
            log.info("[AI_LOS_REQUEST_PAYLOAD] {}", requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtEnvelope.token());
            HttpEntity<AiLosIngestRequest> requestEntity = new HttpEntity<>(payload, headers);

            log.info("AI LOS request prepared: method=POST, url={}, connectTimeoutMs={}, readTimeoutMs={}, headers={}",
                    p.getIngestUrl(),
                    p.getConnectTimeoutMs(),
                    p.getReadTimeoutMs(),
                    Map.of(
                            "Content-Type", "application/json",
                            "Authorization", "Bearer " + maskToken(jwtEnvelope.token())));

            log.info("AI LOS HTTP request initiated for app {}", applicationId);
            ResponseEntity<String> response = restTemplate.exchange(
                    p.getIngestUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            long durationMs = System.currentTimeMillis() - startedAtMs;
            log.info("AI LOS response received: status={}, durationMs={}, headers={}, body={}",
                    response.getStatusCode().value(),
                    durationMs,
                    response.getHeaders(),
                    response.getBody());
            if (!response.getStatusCode().is2xxSuccessful()) {
                auditService.logEvent(
                        applicationId,
                        "AI_LOS",
                        "AI_LOS_REQUEST_FAILED",
                        null,
                        Map.of("mode", mode),
                        Map.of(
                                "statusCode", String.valueOf(response.getStatusCode().value()),
                                "responseBody", truncate(response.getBody(), 3000),
                                "durationMs", String.valueOf(durationMs)),
                        "AI LOS ingest failed with non-2xx response"
                );
                log.error("AI LOS non-2xx response: status={}, body={}, durationMs={}, appId={}",
                        response.getStatusCode().value(), response.getBody(), durationMs, applicationId);
                log.warn("AI LOS demo fallback activated due to API issue");
                log.info("Returning demo AI LOS URL: {}", DEMO_AI_LOS_URL);
                return buildDemoResponse(loanRef, mode);
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), MAP_TYPE);
            String status = String.valueOf(responseMap.getOrDefault("status", "unknown"));
            String message = String.valueOf(responseMap.getOrDefault("message", "Loan ingested."));
            String reviewUrl = String.valueOf(responseMap.getOrDefault("review_url", ""));

            auditService.logEvent(
                    applicationId,
                    "AI_LOS",
                    "AI_LOS_REQUEST_SUCCESS",
                    null,
                    Map.of("mode", mode),
                    Map.of("status", status, "loanRef", loanRef),
                    "AI LOS ingest succeeded"
            );

            String finalUrl;
            if (!reviewUrl.isBlank() && returnUrl != null && !returnUrl.isBlank()) {
                finalUrl = reviewUrl + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            } else if (!reviewUrl.isBlank()) {
                finalUrl = reviewUrl;
            } else {
                finalUrl = DEMO_AI_LOS_URL;
            }

            auditService.logEvent(
                    applicationId,
                    "AI_LOS",
                    "AI_LOS_REDIRECTED",
                    null,
                    Map.of("mode", mode),
                    Map.of("loanRef", loanRef, "finalUrl", finalUrl),
                    "AI LOS redirect URL generated"
            );
            log.info("AI LOS final redirect URL: {}", finalUrl);
            return AiLosOpenResponse.builder()
                    .loanId(loanRef)
                    .status(status)
                    .message(message)
                    .reviewUrl(reviewUrl.isBlank() ? DEMO_AI_LOS_URL : reviewUrl)
                    .finalRedirectUrl(finalUrl)
                    .mode(mode)
                    .build();
        } catch (BusinessRuleException e) {
            log.error("AI LOS business error for app {}: {}", applicationId, e.getMessage(), e);
            log.warn("AI LOS API failed. Using demo fallback URL.");
            log.info("Returning demo AI LOS URL: {}", DEMO_AI_LOS_URL);
            return buildDemoResponse(loanRef, mode);
        } catch (HttpStatusCodeException e) {
            long durationMs = System.currentTimeMillis() - startedAtMs;
            log.error("AI LOS HTTP status exception for app {}: status={}, headers={}, body={}, durationMs={}",
                    applicationId, e.getStatusCode().value(), e.getResponseHeaders(), e.getResponseBodyAsString(), durationMs, e);
            if (isExistingLoanConflict(e)) {
                log.info("AI LOS existing-loan conflict for loanId={}, building deep link", loanRef);
                String deepLink = buildExistingLoanDeepLink(loanRef, returnUrl);
                auditService.logEvent(
                        applicationId,
                        "AI_LOS",
                        "AI_LOS_REQUEST_SUCCESS",
                        null,
                        Map.of("mode", mode, "statusCode", String.valueOf(e.getStatusCode().value())),
                        Map.of("loanRef", loanRef, "fallback", "EXISTING_LOAN_DEEP_LINK"),
                        "AI LOS existing-loan conflict — deep linking to existing review"
                );
                log.info("AI LOS existing loan deep link: {}", deepLink);
                return AiLosOpenResponse.builder()
                        .loanId(loanRef)
                        .status("existing")
                        .message("Loan already ingested. Opening existing review.")
                        .reviewUrl(deepLink)
                        .finalRedirectUrl(deepLink)
                        .mode(mode)
                        .build();
            }
            auditService.logEvent(
                    applicationId,
                    "AI_LOS",
                    "AI_LOS_REQUEST_FAILED",
                    null,
                    Map.of("mode", mode),
                    Map.of(
                            "statusCode", String.valueOf(e.getStatusCode().value()),
                            "responseBody", truncate(e.getResponseBodyAsString(), 3000),
                            "errorType", e.getClass().getSimpleName()),
                    "AI LOS request failed with HTTP status exception"
            );
            log.warn("AI LOS API failed. Using demo fallback URL.");
            log.info("Returning demo AI LOS URL: {}", DEMO_AI_LOS_URL);
            return buildDemoResponse(loanRef, mode);
        } catch (ResourceAccessException e) {
            long durationMs = System.currentTimeMillis() - startedAtMs;
            log.error("AI LOS resource access exception for app {} after {} ms: {}", applicationId, durationMs, e.getMessage(), e);
            auditService.logEvent(
                    applicationId,
                    "AI_LOS",
                    "AI_LOS_REQUEST_FAILED",
                    null,
                    Map.of("mode", mode),
                    Map.of("errorType", "RESOURCE_ACCESS", "errorMessage", truncate(e.getMessage(), 500)),
                    "AI LOS request failed due to connectivity/timeout"
            );
            log.warn("AI LOS API failed. Using demo fallback URL.");
            log.info("Returning demo AI LOS URL: {}", DEMO_AI_LOS_URL);
            return buildDemoResponse(loanRef, mode);
        } catch (Exception e) {
            Throwable root = rootCause(e);
            log.error("AI LOS integration failed for app {}: {} | rootCause={} : {}",
                    applicationId,
                    e.getClass().getName(),
                    root.getClass().getName(),
                    root.getMessage(),
                    e);
            auditService.logEvent(
                    applicationId,
                    "AI_LOS",
                    "AI_LOS_REQUEST_FAILED",
                    null,
                    Map.of("mode", mode),
                    Map.of(
                            "error", "integration_error",
                            "errorType", e.getClass().getSimpleName(),
                            "rootCauseType", root.getClass().getSimpleName(),
                            "rootCauseMessage", truncate(root.getMessage(), 500)),
                    "AI LOS request failed"
            );
            log.warn("AI LOS API failed. Using demo fallback URL.");
            log.info("Returning demo AI LOS URL: {}", DEMO_AI_LOS_URL);
            return buildDemoResponse(loanRef, mode);
        }
    }

    private static AiLosOpenResponse buildDemoResponse(String loanRef, String mode) {
        return AiLosOpenResponse.builder()
                .loanId(loanRef)
                .status("demo")
                .message("AI LOS demo mode active.")
                .reviewUrl(DEMO_AI_LOS_URL)
                .finalRedirectUrl(DEMO_AI_LOS_URL)
                .mode(mode)
                .build();
    }

    private String buildFinalUrl(String mode, String reviewUrl, String loanRef, String returnUrl) {
        String safeReturnUrl = returnUrl != null ? returnUrl.trim() : "";
        if (safeReturnUrl.isBlank()) {
            throw new BusinessRuleException("Return URL is required to open AI LOS review");
        }
        String encodedReturn = URLEncoder.encode(safeReturnUrl, StandardCharsets.UTF_8);
        String base = "WHAT_IF".equals(mode) ? buildWhatIfBase(reviewUrl, loanRef) : reviewUrl;
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "returnUrl=" + encodedReturn;
    }

    private String buildExistingLoanDeepLink(String loanRef, String returnUrl) {
        String safeReturnUrl = returnUrl != null ? returnUrl.trim() : "";
        IntegrationProperties.AiLosProperties p = integrationProperties.getAiLos();
        String base = p.getUiBaseUrl() != null ? p.getUiBaseUrl().trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            base = "https://ai-los.billiontech.ai";
        }
        String url = base
                + "/loan-review?loanId="
                + URLEncoder.encode(loanRef, StandardCharsets.UTF_8);
        if (!safeReturnUrl.isBlank()) {
            url += "&returnUrl=" + URLEncoder.encode(safeReturnUrl, StandardCharsets.UTF_8);
        }
        return url;
    }

    private static boolean isExistingLoanConflict(HttpStatusCodeException e) {
        if (e.getStatusCode().value() != 409) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return false;
        }
        return body.toLowerCase().contains("loan_id already exists");
    }

    private String buildWhatIfBase(String reviewUrl, String loanRef) {
        IntegrationProperties.AiLosProperties p = integrationProperties.getAiLos();
        String configuredWhatIfPath = p.getWhatIfPath() != null ? p.getWhatIfPath().trim() : "";
        if (configuredWhatIfPath.isBlank()) {
            return reviewUrl;
        }
        URI reviewUri = URI.create(reviewUrl);
        String path = configuredWhatIfPath.startsWith("/") ? configuredWhatIfPath : "/" + configuredWhatIfPath;
        String host = reviewUri.getScheme() + "://" + reviewUri.getAuthority();
        return host + path + "?loanId=" + URLEncoder.encode(loanRef, StandardCharsets.UTF_8);
    }

    private String normalizeMode(String modeRaw) {
        if (modeRaw == null || modeRaw.isBlank()) {
            return "REVIEW";
        }
        return "WHAT_IF".equalsIgnoreCase(modeRaw.trim()) ? "WHAT_IF" : "REVIEW";
    }

    private JwtEnvelope generateIntegrationJwt() throws Exception {
        IntegrationProperties.AiLosProperties p = integrationProperties.getAiLos();
        String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "sub", p.getJwtSubject(),
                "role", p.getJwtRole()
        ));
        String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(p.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return new JwtEnvelope(signingInput + "." + signature, "N/A");
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "<empty>";
        }
        int len = token.length();
        if (len <= 12) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(len - 6);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "...(truncated)";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String asString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        Object value = map.get(key);
        return value != null ? String.valueOf(value).trim() : "";
    }

    private static String joinNameParts(String first, String last) {
        String f = first != null ? first.trim() : "";
        String l = last != null ? last.trim() : "";
        return (f + " " + l).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static BigDecimal resolveAnnualIncome(LoanApplication app, Map<String, Object> effective) {
        BigDecimal effectiveIncome = toBigDecimal(effective != null ? effective.get("effectiveIncome") : null);
        if (effectiveIncome != null && effectiveIncome.compareTo(BigDecimal.ZERO) > 0) {
            return effectiveIncome.multiply(BigDecimal.valueOf(12));
        }
        BigDecimal annual = decimalFromMaps(app.getFinancialInfo(), "annualIncome");
        if (annual != null && annual.compareTo(BigDecimal.ZERO) > 0) {
            return annual;
        }
        BigDecimal monthly = firstPositive(
                decimalFromMaps(app.getFinancialInfo(), "monthlyIncome"),
                decimalFromMaps(app.getPersonalInfo(), "monthlyNetIncome"),
                decimalFromMaps(app.getBusinessInfo(), "monthlyIncome")
        );
        return monthly != null ? monthly.multiply(BigDecimal.valueOf(12)) : BigDecimal.ZERO;
    }

    private static Integer resolveExistingEmi(LoanApplication app, Map<String, Object> effective) {
        BigDecimal effectiveObligation = toBigDecimal(effective != null ? effective.get("effectiveObligation") : null);
        if (effectiveObligation != null && effectiveObligation.compareTo(BigDecimal.ZERO) > 0) {
            return effectiveObligation.intValue();
        }
        BigDecimal obligation = firstPositive(
                decimalFromMaps(app.getFinancialInfo(), "emiObligation"),
                decimalFromMaps(app.getFinancialInfo(), "monthlyObligation"),
                decimalFromMaps(app.getFinancialInfo(), "existingEmi"),
                decimalFromMaps(app.getBusinessInfo(), "existingEmi")
        );
        return obligation != null ? obligation.intValue() : 0;
    }

    private static BigDecimal resolvePropertyValue(LoanApplication app, Map<String, Object> effective) {
        @SuppressWarnings("unchecked")
        Map<String, Object> scorecard = effective != null && effective.get("scorecard") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        BigDecimal scorecardProperty = toBigDecimal(scorecard.get("PROPERTY_VALUE"));
        if (scorecardProperty != null && scorecardProperty.compareTo(BigDecimal.ZERO) > 0) {
            return scorecardProperty;
        }
        BigDecimal fromCollateral = firstPositive(
                decimalFromMaps(app.getCollateralInfo(), "propertyValue"),
                decimalFromMaps(app.getCollateralInfo(), "estimatedValue")
        );
        return fromCollateral != null ? fromCollateral : BigDecimal.valueOf(100000);
    }

    private static int resolveCreditScore(LoanApplication app, Map<String, Object> effective) {
        BigDecimal effectiveScore = toBigDecimal(effective != null ? effective.get("effectiveBureauScore") : null);
        if (effectiveScore != null && effectiveScore.compareTo(BigDecimal.ZERO) > 0) {
            return effectiveScore.intValue();
        }
        if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
            return app.getManualBureauScore();
        }
        return app.getBureauScore() != null ? app.getBureauScore() : 0;
    }

    private Map<String, Object> readEffectiveContext(LoanApplication app) {
        Map<String, Object> view = creditControlService.buildReadView(app);
        if (view == null || !(view.get("effective") instanceof Map<?, ?> m)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> effective = (Map<String, Object>) m;
        return effective;
    }

    private static String sanitizeBorrowerName(String candidate, LoanApplication app) {
        if (!isPanLike(candidate)) {
            return candidate;
        }
        return firstNonBlank(
                joinNameParts(asString(app.getPersonalInfo(), "firstName"), asString(app.getPersonalInfo(), "lastName")),
                asString(app.getPersonalInfo(), "fullName"),
                "Borrower"
        );
    }

    private static boolean isPanLike(String value) {
        if (value == null) return false;
        return value.trim().toUpperCase().matches("^[A-Z]{5}[0-9]{4}[A-Z]$");
    }

    private static String normalizeEmploymentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "salaried";
        }
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "salaried":
            case "salary":
            case "employee":
                return "salaried";
            case "self employed":
            case "self_employed":
            case "self-employed":
            case "proprietor":
                return "self_employed";
            case "business":
            case "businessman":
            case "company":
                return "business";
            default:
                return "salaried";
        }
    }

    private static BigDecimal decimalFromMaps(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal firstPositive(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record JwtEnvelope(String token, String expiry) {}

    @Getter
    @Builder
    private static class AiLosIngestRequest {
        @JsonProperty("loan_id")
        private String loanId;
        @JsonProperty("borrower_name")
        private String borrowerName;
        @JsonProperty("loan_amount")
        private BigDecimal loanAmount;
        @JsonProperty("loan_purpose")
        private String loanPurpose;
        @JsonProperty("credit_score")
        private Integer creditScore;
        @JsonProperty("annual_income")
        private BigDecimal annualIncome;
        @JsonProperty("employment_type")
        private String employmentType;
        @JsonProperty("loan_tenure_months")
        private Integer loanTenureMonths;
        @JsonProperty("existing_emi")
        private Integer existingEmi;
        @JsonProperty("property_value")
        private BigDecimal propertyValue;
        @JsonProperty("source_los")
        private String sourceLos;
        @JsonProperty("source_loan_ref")
        private String sourceLoanRef;
    }
}
