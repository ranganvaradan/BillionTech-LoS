package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Encumbrance Certificate (EC) verification for PROPERTY collateral.
 * Uses Karza property encumbrance search by default; simulation mode for dev/staging.
 */
@Slf4j
@Component("propertyEcProvider")
@RequiredArgsConstructor
public class PropertyEcProvider implements IKycProvider {

    private static final String KARZA_EC_PATH = "/property-encumbrance";

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        if (stepType != KycStepType.PROPERTY_EC_VERIFY) {
            return new KycVerificationResult(false, 0.0, null, null,
                    "PropertyEcProvider does not support step: " + stepType);
        }

        log.info("[PropertyEC] Verify with payload keys: {}", payload.keySet());
        String transactionId = "EC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        PropertyEcInput input;
        try {
            input = parseInput(payload);
        } catch (IllegalArgumentException e) {
            return new KycVerificationResult(false, 0.0, null, transactionId, e.getMessage());
        }

        IntegrationProperties.PropertyEcProperties config = integrationProperties.getPropertyEc();
        if (config.isSimulation() || !hasApiKey(config)) {
            log.info("[PropertyEC] Simulation mode — EC search for reg {} / survey {}",
                    input.propertyRegNumber(), input.surveyNumber());
            return simulatedResponse(input, transactionId);
        }

        if (!"KARZA".equalsIgnoreCase(config.getProvider())) {
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Property EC provider not supported yet: " + config.getProvider());
        }

        return callKarzaEc(config, input, transactionId);
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return stepType == KycStepType.PROPERTY_EC_VERIFY;
    }

    @Override
    public String getProviderName() {
        String provider = integrationProperties.getPropertyEc().getProvider();
        return provider != null && !provider.isBlank() ? provider.toUpperCase(Locale.ROOT) : "PROPERTY_EC";
    }

    @Override
    public int getPriority() {
        return 8;
    }

    private KycVerificationResult callKarzaEc(IntegrationProperties.PropertyEcProperties config,
                                               PropertyEcInput input,
                                               String transactionId) {
        String apiKey = resolveApiKey(config);
        String url = normalizedBaseUrl(config.getBaseUrl()) + KARZA_EC_PATH;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("consent", "Y");
        requestBody.put("propertyRegNumber", input.propertyRegNumber());
        requestBody.put("district", input.district());
        requestBody.put("state", input.state());
        requestBody.put("surveyNumber", input.surveyNumber());

        Instant requestTime = Instant.now();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to serialize EC request: " + e.getMessage());
        }

        log.info("[PropertyEC][REQUEST] URL: {} Body: {}", url, requestJson);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("x-karza-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[PropertyEC][RESPONSE] HTTP {} in {}ms", response.statusCode(), durationMs);

            saveAuditLog(requestJson, response.body(),
                    response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                return parseKarzaEcResponse(response.body(), input, transactionId);
            }
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Property EC API returned HTTP " + response.statusCode());

        } catch (Exception e) {
            log.error("[PropertyEC] API call failed: {}", e.getMessage(), e);
            saveAuditLog(requestJson, null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Property EC API error: " + e.getMessage());
        }
    }

    private KycVerificationResult parseKarzaEcResponse(String responseBody, PropertyEcInput input,
                                                        String transactionId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int statusCode = normalizedStatusCode(root);
            JsonNode result = root.path("result");
            boolean success = statusCode == 101 && result.isObject();

            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("simulated", false);
            parsed.put("propertyRegNumber", input.propertyRegNumber());
            parsed.put("district", input.district());
            parsed.put("state", input.state());
            parsed.put("surveyNumber", input.surveyNumber());

            if (result.isObject()) {
                parsed.put("encumbranceStatus", firstNonBlank(result,
                        "encumbranceStatus", "status", "ecStatus"));
                parsed.put("existingCharges", readChargeList(result.path("existingCharges"), result.path("charges")));
                parsed.put("ownerHistory", readOwnerHistory(result.path("ownerHistory"), result.path("owners")));
            }

            String errorMsg = success ? null : normalizedErrorMessage(root, result);
            return new KycVerificationResult(success, success ? 0.96 : 0.0, parsed, transactionId, errorMsg);

        } catch (Exception e) {
            log.error("[PropertyEC] Failed to parse response: {}", e.getMessage());
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to parse Property EC response: " + e.getMessage());
        }
    }

    private KycVerificationResult simulatedResponse(PropertyEcInput input, String transactionId) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("simulated", true);
        parsed.put("propertyRegNumber", input.propertyRegNumber());
        parsed.put("district", input.district());
        parsed.put("state", input.state());
        parsed.put("surveyNumber", input.surveyNumber());
        parsed.put("encumbranceStatus", "CLEAR");
        parsed.put("existingCharges", List.of());
        parsed.put("ownerHistory", List.of(
                Map.of(
                        "ownerName", "Anita Desai",
                        "ownershipType", "SOLE",
                        "fromDate", "2018-04-12",
                        "toDate", "PRESENT"
                )
        ));
        parsed.put("verified", true);
        return new KycVerificationResult(true, 0.94, parsed, transactionId, null);
    }

    private static PropertyEcInput parseInput(Map<String, Object> payload) {
        String propertyRegNumber = requiredString(payload, "propertyRegNumber", "registrationNumber");
        String district = requiredString(payload, "district");
        String state = requiredString(payload, "state");
        String surveyNumber = requiredString(payload, "surveyNumber", "surveyNo");
        return new PropertyEcInput(propertyRegNumber, district, state, surveyNumber);
    }

    private static String requiredString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String s = value.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("Missing required field. Expected one of: " + String.join(", ", keys));
    }

    private List<Map<String, Object>> readChargeList(JsonNode primary, JsonNode fallback) {
        JsonNode node = primary != null && primary.isArray() ? primary : fallback;
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, Object>> readOwnerHistory(JsonNode primary, JsonNode fallback) {
        JsonNode node = primary != null && primary.isArray() ? primary : fallback;
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }

    private static String firstNonBlank(JsonNode result, String... keys) {
        for (String key : keys) {
            String value = result.path(key).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static int normalizedStatusCode(JsonNode root) {
        JsonNode status = root.get("statusCode");
        if (status != null && status.isValueNode()) {
            try {
                return Integer.parseInt(status.asText("").trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    private static String normalizedErrorMessage(JsonNode root, JsonNode result) {
        String msg = root.path("errorMessage").asText("").trim();
        if (!msg.isEmpty()) return msg;
        msg = root.path("statusMessage").asText("").trim();
        if (!msg.isEmpty()) return msg;
        msg = result.path("errorMessage").asText("").trim();
        return msg.isEmpty() ? "Property EC verification failed" : msg;
    }

    private boolean hasApiKey(IntegrationProperties.PropertyEcProperties config) {
        String key = resolveApiKey(config);
        return key != null && !key.isBlank();
    }

    private String resolveApiKey(IntegrationProperties.PropertyEcProperties config) {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        IntegrationProperties.KarzaProperties karza = integrationProperties.getKarza();
        return karza != null ? karza.getApiKey() : "";
    }

    private static String normalizedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.karza.in/v3";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void saveAuditLog(String request, String response, String status, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime, Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("PROPERTY_EC")
                    .apiName("PROPERTY_EC_VERIFY")
                    .requestPayload(request)
                    .responsePayload(response)
                    .status(status)
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            log.error("[PropertyEC] Failed to save audit log: {}", e.getMessage());
        }
    }

    private record PropertyEcInput(
            String propertyRegNumber,
            String district,
            String state,
            String surveyNumber
    ) {}
}
