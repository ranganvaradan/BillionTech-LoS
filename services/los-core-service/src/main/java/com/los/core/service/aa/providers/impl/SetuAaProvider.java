package com.los.core.service.aa.providers.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.service.aa.providers.AaConsentRequest;
import com.los.core.service.aa.providers.AaConsentResponse;
import com.los.core.service.aa.providers.AaConsentStatus;
import com.los.core.service.aa.providers.AaFetchResponse;
import com.los.core.service.aa.providers.AaFiDataParser;
import com.los.core.service.aa.providers.IAccountAggregatorProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Setu Account Aggregator FIU integration.
 * Docs: https://docs.setu.co/data/account-aggregator
 */
@Slf4j
@Component("setuAaProvider")
@RequiredArgsConstructor
public class SetuAaProvider implements IAccountAggregatorProvider {

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    private volatile CachedToken cachedToken;

    @Override
    public AaConsentResponse createConsent(AaConsentRequest request) {
        IntegrationProperties.SetuAaProperties config = integrationProperties.getSetuAa();
        String txnId = "SETU-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        Map<String, Object> body = buildCreateConsentBody(request, config);
        String requestJson = writeJson(body, txnId);
        String url = normalizedBaseUrl(config.getBaseUrl()) + "/v2/consents";

        JsonNode response = postJson(config, url, requestJson, txnId, "SETU_AA_CREATE_CONSENT");
        String providerConsentId = firstText(response, "id", "consentId", "consentRequestId");
        String redirectUrl = firstText(response, "url", "redirectUrl", "consentUrl");
        String status = normalizeStatus(firstText(response, "status", "consentStatus", "state"), "PENDING");

        if (providerConsentId.isBlank()) {
            throw new IllegalStateException("Setu AA create consent did not return a consent id");
        }

        Map<String, Object> raw = objectMapper.convertValue(response, Map.class);
        return new AaConsentResponse(request.consentHandle(), providerConsentId, blankToNull(redirectUrl),
                status, raw);
    }

    @Override
    public AaConsentStatus checkConsentStatus(String providerConsentId) {
        IntegrationProperties.SetuAaProperties config = integrationProperties.getSetuAa();
        String txnId = "SETU-S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String url = normalizedBaseUrl(config.getBaseUrl()) + "/v2/consents/" + providerConsentId;

        JsonNode response = getJson(config, url, txnId, "SETU_AA_CONSENT_STATUS");
        String status = normalizeStatus(firstText(response, "status", "consentStatus", "state"), "PENDING");
        Map<String, Object> raw = objectMapper.convertValue(response, Map.class);
        return new AaConsentStatus(providerConsentId, status, raw);
    }

    @Override
    public AaFetchResponse fetchFinancialData(String providerConsentId) {
        IntegrationProperties.SetuAaProperties config = integrationProperties.getSetuAa();
        String txnId = "SETU-F-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        Instant to = Instant.now();
        Instant from = to.minusSeconds(Duration.ofDays(180).toSeconds());

        Map<String, Object> sessionBody = new LinkedHashMap<>();
        sessionBody.put("consentId", providerConsentId);
        sessionBody.put("format", "json");
        sessionBody.put("dataRange", Map.of(
                "from", ISO_INSTANT.format(from),
                "to", ISO_INSTANT.format(to)
        ));

        String sessionUrl = normalizedBaseUrl(config.getBaseUrl()) + "/v2/sessions";
        JsonNode sessionResponse = postJson(config, sessionUrl, writeJson(sessionBody, txnId),
                txnId, "SETU_AA_CREATE_SESSION");

        String sessionId = firstText(sessionResponse, "sessionId", "id", "dataSessionId");
        if (sessionId.isBlank()) {
            throw new IllegalStateException("Setu AA data session did not return a session id");
        }

        String fetchUrl = normalizedBaseUrl(config.getBaseUrl()) + "/v2/sessions/" + sessionId;
        JsonNode fetchResponse = getJson(config, fetchUrl, txnId, "SETU_AA_FETCH_FI");

        Map<String, Object> summary = AaFiDataParser.parseSetuFiPayload(fetchResponse);
        if (summary.get("accountCount") instanceof Number count && count.intValue() == 0) {
            log.warn("[SetuAA] Parsed zero accounts from session {} — storing raw payload only", sessionId);
        }

        Map<String, Object> raw = objectMapper.convertValue(fetchResponse, Map.class);
        raw.put("sessionId", sessionId);
        return new AaFetchResponse(summary, raw);
    }

    @Override
    public void revokeConsent(String providerConsentId, String reason) {
        IntegrationProperties.SetuAaProperties config = integrationProperties.getSetuAa();
        String txnId = "SETU-R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        Map<String, Object> body = Map.of(
                "reason", reason != null && !reason.isBlank() ? reason : "Borrower requested revocation"
        );
        String url = normalizedBaseUrl(config.getBaseUrl()) + "/v2/consents/" + providerConsentId + "/revoke";
        postJson(config, url, writeJson(body, txnId), txnId, "SETU_AA_REVOKE_CONSENT");
    }

    @Override
    public String getProviderName() {
        return "SETU_AA";
    }

    private Map<String, Object> buildCreateConsentBody(AaConsentRequest request,
                                                       IntegrationProperties.SetuAaProperties config) {
        Map<String, Object> purpose = request.purpose() != null ? request.purpose() : defaultPurpose();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fetchType", request.fetchFrequency() != null ? request.fetchFrequency() : "ONETIME");
        body.put("consentMode", request.consentMode() != null ? request.consentMode() : "STORE");
        body.put("fiTypes", request.fiTypes() != null ? request.fiTypes() : List.of("DEPOSIT", "TERM_DEPOSIT"));
        body.put("consentTypes", List.of("PROFILE", "SUMMARY", "TRANSACTIONS"));
        body.put("dataRange", Map.of(
                "from", ISO_INSTANT.format(request.consentStartDate()),
                "to", ISO_INSTANT.format(request.consentExpiryDate())
        ));
        body.put("purpose", Map.of(
                "code", String.valueOf(purpose.getOrDefault("code", "101")),
                "text", String.valueOf(purpose.getOrDefault("text", "Loan underwriting and credit assessment")),
                "refUri", String.valueOf(purpose.getOrDefault("refUri",
                        "https://api.rebit.org.in/aa/purpose/101"))
        ));
        if (config.getRedirectUrl() != null && !config.getRedirectUrl().isBlank()) {
            body.put("redirectUrl", config.getRedirectUrl());
        }
        body.put("context", Map.of(
                "applicationId", request.applicationId().toString(),
                "customerId", request.customerId().toString(),
                "consentHandle", request.consentHandle()
        ));
        return body;
    }

    private static Map<String, Object> defaultPurpose() {
        return Map.of(
                "code", "101",
                "text", "Loan underwriting and credit assessment",
                "refUri", "https://api.rebit.org.in/aa/purpose/101"
        );
    }

    private JsonNode postJson(IntegrationProperties.SetuAaProperties config, String url, String requestJson,
                              String txnId, String apiName) {
        Instant requestTime = Instant.now();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = withAuthHeaders(config, HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            saveAuditLog(apiName, requestJson, response.body(), response.statusCode(), null, txnId,
                    requestTime, responseTime);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(apiName + " returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            saveAuditLog(apiName, requestJson, null, null, e.getMessage(), txnId, requestTime, Instant.now());
            throw new IllegalStateException(apiName + " failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJson(IntegrationProperties.SetuAaProperties config, String url, String txnId, String apiName) {
        Instant requestTime = Instant.now();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = withAuthHeaders(config, HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .GET())
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            saveAuditLog(apiName, "GET " + url, response.body(), response.statusCode(), null, txnId,
                    requestTime, responseTime);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(apiName + " returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            saveAuditLog(apiName, "GET " + url, null, null, e.getMessage(), txnId, requestTime, Instant.now());
            throw new IllegalStateException(apiName + " failed: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder withAuthHeaders(IntegrationProperties.SetuAaProperties config,
                                                HttpRequest.Builder builder) {
        if (config.getProductInstanceId() != null && !config.getProductInstanceId().isBlank()) {
            builder.header("x-product-instance-id", config.getProductInstanceId());
        }
        String token = resolveAccessToken(config);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        } else if (config.getClientId() != null && !config.getClientId().isBlank()) {
            builder.header("x-client-id", config.getClientId());
            builder.header("x-client-secret", config.getClientSecret());
        }
        return builder;
    }

    private String resolveAccessToken(IntegrationProperties.SetuAaProperties config) {
        if (config.getClientId() == null || config.getClientId().isBlank()
                || config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            return "";
        }

        CachedToken existing = cachedToken;
        if (existing != null && existing.expiresAt.isAfter(Instant.now())) {
            return existing.token;
        }

        String authUrl = config.getAuthUrl();
        if (authUrl == null || authUrl.isBlank()) {
            return "";
        }

        Map<String, Object> loginBody = Map.of(
                "clientID", config.getClientId(),
                "grant_type", "client_credentials",
                "secret", config.getClientSecret()
        );

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(loginBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[SetuAA] Token fetch failed with HTTP {}", response.statusCode());
                return "";
            }

            JsonNode root = objectMapper.readTree(response.body());
            String token = firstText(root, "accessToken", "access_token", "token");
            if (token.isBlank()) {
                return "";
            }
            cachedToken = new CachedToken(token, Instant.now().plus(Duration.ofMinutes(50)));
            return token;
        } catch (Exception e) {
            log.warn("[SetuAA] Token fetch failed: {}", e.getMessage());
            return "";
        }
    }

    private String writeJson(Map<String, Object> body, String txnId) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Setu AA request " + txnId + ": " + e.getMessage(), e);
        }
    }

    private static String normalizeStatus(String providerStatus, String defaultStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return defaultStatus;
        }
        String normalized = providerStatus.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized) || "APPROVED".equals(normalized)) {
            return "APPROVED";
        }
        if ("REJECTED".equals(normalized) || "FAILED".equals(normalized)) {
            return "REJECTED";
        }
        if ("REVOKED".equals(normalized)) {
            return "REVOKED";
        }
        return normalized;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://fiu-uat.setu.co";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void saveAuditLog(String apiName, String request, String response, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("SETU_AA")
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
            log.error("[SetuAA] Failed to save audit log: {}", e.getMessage());
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
