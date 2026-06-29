package com.los.core.service.collateral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.CersaiRegistrationRequest;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.entity.CersaiRegistration;
import com.los.core.model.entity.CollateralValuation;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.CersaiRegistrationRepository;
import com.los.core.repository.CollateralValuationRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.loan.ApplicantIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CERSAI (Central Registry of Securitisation Asset Reconstruction and Security Interest) integration.
 * Supports charge search before sanction and security interest registration after sanction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CersaiService {

    private final CersaiRegistrationRepository cersaiRegistrationRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CollateralValuationRepository collateralValuationRepository;
    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CersaiRegistration searchCharges(String assetType, String assetIdentifier, UUID applicationId) {
        validateAssetType(assetType);
        if (assetIdentifier == null || assetIdentifier.isBlank()) {
            throw new IllegalArgumentException("Asset identifier is required for CERSAI search.");
        }

        LoanApplication application = requireApplication(applicationId);
        IntegrationProperties.CersaiProperties config = integrationProperties.getCersai();

        Map<String, Object> responseData = config.isSimulation() || !hasLiveConfig(config)
                ? simulatedSearchResponse(assetType, assetIdentifier)
                : callCersaiSearch(config, assetType, assetIdentifier, applicationId);

        CersaiRegistration registration = CersaiRegistration.builder()
                .applicationId(applicationId)
                .assetType(assetType.trim().toUpperCase(Locale.ROOT))
                .assetDescription("Search: " + assetIdentifier.trim())
                .registrationStatus("SEARCHED")
                .securityInterestType("SEARCH")
                .borrowerName(extractBorrowerName(application))
                .borrowerPan(ApplicantIdentityResolver.resolvePanNumber(application))
                .lenderName(defaultLenderName(config, null))
                .responseData(responseData)
                .build();

        registration = cersaiRegistrationRepository.save(registration);

        auditService.logEvent(applicationId, "CERSAI_SEARCH",
                Map.of(
                        "assetType", assetType,
                        "assetIdentifier", assetIdentifier,
                        "chargeCount", String.valueOf(responseData.getOrDefault("chargeCount", 0))
                ));

        log.info("[CERSAI] Search completed for application {} asset={} charges={}",
                applicationId, assetIdentifier, responseData.get("chargeCount"));
        return registration;
    }

    @Transactional
    public CersaiRegistration registerSecurityInterest(UUID applicationId,
                                                       UUID collateralValuationId,
                                                       CersaiRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CERSAI registration request is required.");
        }

        LoanApplication application = requireApplication(applicationId);
        CollateralValuation valuation = null;
        if (collateralValuationId != null) {
            valuation = collateralValuationRepository.findById(collateralValuationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Collateral valuation not found: " + collateralValuationId));
            if (!applicationId.equals(valuation.getApplicationId())) {
                throw new IllegalArgumentException("Collateral valuation does not belong to application.");
            }
        }

        String assetType = resolveAssetType(request, valuation);
        validateAssetType(assetType);
        String securityInterestType = resolveSecurityInterestType(request, valuation);
        IntegrationProperties.CersaiProperties config = integrationProperties.getCersai();

        Map<String, Object> providerResponse = config.isSimulation() || !hasLiveConfig(config)
                ? simulatedRegisterResponse(applicationId, assetType)
                : callCersaiRegister(config, applicationId, request, assetType, securityInterestType);

        Instant now = Instant.now();
        String cersaiId = stringValue(providerResponse, "cersaiId");
        String status = stringValue(providerResponse, "status");
        if (status == null || status.isBlank()) {
            status = cersaiId != null ? "REGISTERED" : "FAILED";
        }

        CersaiRegistration registration = CersaiRegistration.builder()
                .applicationId(applicationId)
                .collateralValuationId(collateralValuationId)
                .cersaiId(cersaiId)
                .assetType(assetType)
                .assetDescription(resolveAssetDescription(request, valuation))
                .registrationStatus(status.toUpperCase(Locale.ROOT))
                .securityInterestType(securityInterestType)
                .securedAmount(request.getSecuredAmount() != null
                        ? request.getSecuredAmount()
                        : valuation != null ? valuation.getValuationAmount() : application.getRequestedAmount())
                .borrowerName(firstNonBlank(request.getBorrowerName(), extractBorrowerName(application)))
                .borrowerPan(firstNonBlank(request.getBorrowerPan(), ApplicantIdentityResolver.resolvePanNumber(application)))
                .lenderName(defaultLenderName(config, request.getLenderName()))
                .lenderCin(firstNonBlank(request.getLenderCin(), config.getLenderCin()))
                .registrationDate("REGISTERED".equalsIgnoreCase(status) ? now : null)
                .expiryDate("REGISTERED".equalsIgnoreCase(status) ? now.plus(3650, ChronoUnit.DAYS) : null)
                .responseData(providerResponse)
                .build();

        registration = cersaiRegistrationRepository.save(registration);

        if (valuation != null && "REGISTERED".equalsIgnoreCase(registration.getRegistrationStatus())) {
            updateValuationCersaiDetails(valuation, registration);
        }

        auditService.logEvent(applicationId, "CERSAI_REGISTERED",
                Map.of(
                        "cersaiId", registration.getCersaiId() != null ? registration.getCersaiId() : "",
                        "assetType", registration.getAssetType(),
                        "status", registration.getRegistrationStatus()
                ));

        log.info("[CERSAI] Registration {} for application {} cersaiId={}",
                registration.getRegistrationStatus(), applicationId, registration.getCersaiId());
        return registration;
    }

    @Transactional(readOnly = true)
    public CersaiRegistration getRegistration(UUID applicationId) {
        return cersaiRegistrationRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No CERSAI registration found for application: " + applicationId));
    }

    @Transactional(readOnly = true)
    public List<CersaiRegistration> listRegistrations(UUID applicationId) {
        return cersaiRegistrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private Map<String, Object> callCersaiSearch(IntegrationProperties.CersaiProperties config,
                                                  String assetType,
                                                  String assetIdentifier,
                                                  UUID applicationId) {
        Map<String, Object> body = Map.of(
                "institutionId", config.getInstitutionId(),
                "assetType", assetType,
                "assetIdentifier", assetIdentifier,
                "applicationId", applicationId.toString()
        );
        return executeCersaiPost(config, config.getSearchPath(), body, "CERSAI_SEARCH");
    }

    private Map<String, Object> callCersaiRegister(IntegrationProperties.CersaiProperties config,
                                                   UUID applicationId,
                                                   CersaiRegistrationRequest request,
                                                   String assetType,
                                                   String securityInterestType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("institutionId", config.getInstitutionId());
        body.put("applicationId", applicationId.toString());
        body.put("assetType", assetType);
        body.put("assetDescription", request.getAssetDescription());
        body.put("assetIdentifier", request.getAssetIdentifier());
        body.put("securityInterestType", securityInterestType);
        body.put("securedAmount", request.getSecuredAmount());
        body.put("borrowerName", request.getBorrowerName());
        body.put("borrowerPan", request.getBorrowerPan());
        body.put("lenderName", defaultLenderName(config, request.getLenderName()));
        body.put("lenderCin", firstNonBlank(request.getLenderCin(), config.getLenderCin()));
        return executeCersaiPost(config, config.getRegisterPath(), body, "CERSAI_REGISTER");
    }

    private Map<String, Object> executeCersaiPost(IntegrationProperties.CersaiProperties config,
                                                  String path,
                                                  Map<String, Object> body,
                                                  String apiName) {
        String txnId = "CERSAI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String url = normalizedBaseUrl(config.getBaseUrl()) + path;
        Instant requestTime = Instant.now();

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize CERSAI request: " + e.getMessage(), e);
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson));
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.getApiKey());
                builder.header("x-api-key", config.getApiKey());
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            saveAuditLog(apiName, requestJson, response.body(), response.statusCode(), null, txnId,
                    requestTime, responseTime);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(apiName + " returned HTTP " + response.statusCode());
            }

            return parseProviderResponse(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            saveAuditLog(apiName, requestJson, null, null, e.getMessage(), txnId, requestTime, Instant.now());
            throw new IllegalStateException(apiName + " failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseProviderResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        Map<String, Object> parsed = objectMapper.convertValue(root, Map.class);

        if (!parsed.containsKey("cersaiId")) {
            String cersaiId = textOrNull(root, "cersaiId", "cersai_id", "registrationId", "id");
            if (cersaiId != null) {
                parsed.put("cersaiId", cersaiId);
            }
        }
        if (!parsed.containsKey("status")) {
            String status = textOrNull(root, "status", "registrationStatus");
            if (status != null) {
                parsed.put("status", status);
            }
        }
        parsed.put("simulated", false);
        return parsed;
    }

    private static Map<String, Object> simulatedSearchResponse(String assetType, String assetIdentifier) {
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("lenderName", "HDFC Bank Ltd");
        charge.put("chargeType", "MORTGAGE");
        charge.put("amount", 2_500_000);
        charge.put("chargeDate", "2020-05-10");
        charge.put("assetIdentifier", assetIdentifier.trim());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("simulated", true);
        response.put("assetType", assetType.trim().toUpperCase(Locale.ROOT));
        response.put("assetIdentifier", assetIdentifier.trim());
        response.put("chargeCount", 1);
        response.put("charges", List.of(charge));
        response.put("message", "Simulated CERSAI search — one existing charge found");
        return response;
    }

    private Map<String, Object> simulatedRegisterResponse(UUID applicationId, String assetType) {
        long numericId = 1_000_000_000_000L
                + (Math.abs((applicationId.toString() + assetType).hashCode()) % 9_000_000_000_000L);
        String cersaiId = String.format(Locale.ROOT, "%014d", numericId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("simulated", true);
        response.put("status", "REGISTERED");
        response.put("cersaiId", cersaiId);
        response.put("message", "Simulated CERSAI security interest registration successful");
        return response;
    }

    private void updateValuationCersaiDetails(CollateralValuation valuation, CersaiRegistration registration) {
        Map<String, Object> details = valuation.getDetails() != null
                ? new HashMap<>(valuation.getDetails())
                : new HashMap<>();
        Map<String, Object> cersai = new LinkedHashMap<>();
        cersai.put("registrationStatus", registration.getRegistrationStatus());
        cersai.put("cersaiId", registration.getCersaiId());
        cersai.put("registeredAt", registration.getRegistrationDate() != null
                ? registration.getRegistrationDate().toString() : null);
        cersai.put("securityInterestType", registration.getSecurityInterestType());
        details.put("cersai", cersai);
        valuation.setDetails(details);
        collateralValuationRepository.save(valuation);
    }

    private LoanApplication requireApplication(UUID applicationId) {
        return loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private static void validateAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            throw new IllegalArgumentException("Asset type is required.");
        }
        String normalized = assetType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("IMMOVABLE", "MOVABLE", "INTANGIBLE").contains(normalized)) {
            throw new IllegalArgumentException("Asset type must be IMMOVABLE, MOVABLE, or INTANGIBLE.");
        }
    }

    private static String resolveAssetType(CersaiRegistrationRequest request, CollateralValuation valuation) {
        if (request.getAssetType() != null && !request.getAssetType().isBlank()) {
            return request.getAssetType().trim().toUpperCase(Locale.ROOT);
        }
        if (valuation != null) {
            return mapCollateralTypeToAssetType(valuation.getCollateralType());
        }
        throw new IllegalArgumentException("Asset type is required for CERSAI registration.");
    }

    private static String resolveSecurityInterestType(CersaiRegistrationRequest request, CollateralValuation valuation) {
        if (request.getSecurityInterestType() != null && !request.getSecurityInterestType().isBlank()) {
            return request.getSecurityInterestType().trim().toUpperCase(Locale.ROOT);
        }
        if (valuation != null) {
            return mapCollateralTypeToSecurityInterest(valuation.getCollateralType());
        }
        return "HYPOTHECATION";
    }

    private static String resolveAssetDescription(CersaiRegistrationRequest request, CollateralValuation valuation) {
        if (request.getAssetDescription() != null && !request.getAssetDescription().isBlank()) {
            return request.getAssetDescription().trim();
        }
        if (request.getAssetIdentifier() != null && !request.getAssetIdentifier().isBlank()) {
            return request.getAssetIdentifier().trim();
        }
        if (valuation != null && valuation.getDescription() != null && !valuation.getDescription().isBlank()) {
            return valuation.getDescription();
        }
        return valuation != null ? valuation.getCollateralType() : "Secured asset";
    }

    private static String mapCollateralTypeToAssetType(String collateralType) {
        if (collateralType == null) {
            return "MOVABLE";
        }
        return switch (collateralType.trim().toUpperCase(Locale.ROOT)) {
            case "PROPERTY" -> "IMMOVABLE";
            case "SHARES" -> "INTANGIBLE";
            default -> "MOVABLE";
        };
    }

    private static String mapCollateralTypeToSecurityInterest(String collateralType) {
        if (collateralType == null) {
            return "HYPOTHECATION";
        }
        return switch (collateralType.trim().toUpperCase(Locale.ROOT)) {
            case "PROPERTY" -> "MORTGAGE";
            case "GOLD", "SHARES", "FIXED_DEPOSIT" -> "PLEDGE";
            case "VEHICLE", "MACHINERY" -> "HYPOTHECATION";
            default -> "LIEN";
        };
    }

    private static String extractBorrowerName(LoanApplication application) {
        if (application.getPersonalInfo() != null) {
            Object name = application.getPersonalInfo().get("fullName");
            if (name == null) {
                name = application.getPersonalInfo().get("name");
            }
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name).trim();
            }
        }
        if (application.getBusinessInfo() != null) {
            Object corporate = application.getBusinessInfo().get("corporateName");
            if (corporate != null && !String.valueOf(corporate).isBlank()) {
                return String.valueOf(corporate).trim();
            }
        }
        return "";
    }

    private static String defaultLenderName(IntegrationProperties.CersaiProperties config, String requestLender) {
        return firstNonBlank(requestLender, config.getLenderName(), "Billion Loans NBFC");
    }

    private static boolean hasLiveConfig(IntegrationProperties.CersaiProperties config) {
        return config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    private static String normalizedBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String textOrNull(JsonNode root, String... keys) {
        for (String key : keys) {
            String value = root.path(key).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void saveAuditLog(String apiName, String request, String response, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("CERSAI")
                    .apiName(apiName)
                    .requestPayload(request)
                    .responsePayload(response)
                    .status(httpStatus != null && httpStatus >= 200 && httpStatus < 300 ? "SUCCESS" : "FAILED")
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(Duration.between(reqTime, resTime).toMillis())
                    .build());
        } catch (Exception e) {
            log.error("[CERSAI] Failed to save audit log: {}", e.getMessage());
        }
    }
}
