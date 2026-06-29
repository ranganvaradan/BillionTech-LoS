package com.los.core.service.integration.providers.impl;

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
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Vahan.gov.in vehicle registration certificate verification via Karza RC aggregator.
 * Used for VEHICLE collateral — verifies RC number, owner, class, fuel, insurance, hypothecation.
 */
@Slf4j
@Component("vahanRcProvider")
@RequiredArgsConstructor
public class VahanRcProvider implements IKycProvider {

    private static final String RC_ADVANCE_PATH = "/rc-advance";

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        if (stepType != KycStepType.VEHICLE_RC_VERIFY) {
            return new KycVerificationResult(false, 0.0, null, null,
                    "VahanRcProvider does not support step: " + stepType);
        }

        log.info("[Vahan] RC verify with payload keys: {}", payload.keySet());
        String transactionId = "VH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        String rcNumber;
        try {
            rcNumber = requiredRcNumber(payload);
        } catch (IllegalArgumentException e) {
            return new KycVerificationResult(false, 0.0, null, transactionId, e.getMessage());
        }

        IntegrationProperties.VahanProperties vahanConfig = integrationProperties.getVahan();
        if (vahanConfig.isSimulation() || !hasApiKey(vahanConfig)) {
            log.info("[Vahan] Simulation mode — returning dummy RC data for {}", rcNumber);
            return simulatedResponse(rcNumber, payload, transactionId);
        }

        String apiKey = resolveApiKey(vahanConfig);
        String url = normalizedBaseUrl(vahanConfig.getBaseUrl()) + RC_ADVANCE_PATH;
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("consent", "Y");
        requestBody.put("registrationNumber", rcNumber);
        String registrationDate = optionalString(payload, "registrationDate");
        if (!registrationDate.isBlank()) {
            requestBody.put("registrationDate", registrationDate);
        }

        Instant requestTime = Instant.now();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to serialize RC request: " + e.getMessage());
        }

        log.info("[Vahan][REQUEST] URL: {} Body: {}", url, requestJson);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(vahanConfig.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(vahanConfig.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("x-karza-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Vahan][RESPONSE] HTTP {} in {}ms — {}", response.statusCode(), durationMs, response.body());

            saveAuditLog(requestJson, response.body(),
                    response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                return parseKarzaRcResponse(response.body(), rcNumber, transactionId);
            }
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Vahan/Karza RC API returned HTTP " + response.statusCode());

        } catch (Exception e) {
            log.error("[Vahan] RC API call failed: {}", e.getMessage(), e);
            saveAuditLog(requestJson, null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Vahan RC API error: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return stepType == KycStepType.VEHICLE_RC_VERIFY;
    }

    @Override
    public String getProviderName() {
        return "VAHAN";
    }

    @Override
    public int getPriority() {
        return 8;
    }

    private KycVerificationResult parseKarzaRcResponse(String responseBody, String rcNumber, String transactionId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int statusCode = normalizedStatusCode(root);
            JsonNode result = root.path("result");
            boolean success = statusCode == 101 && result.isObject();

            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("rcNumber", rcNumber);
            parsed.put("simulated", false);

            if (result.isObject()) {
                putText(parsed, result, "ownerName", "ownerName", "owner");
                putText(parsed, result, "vehicleClass", "vehicleClass", "class");
                putText(parsed, result, "fuelType", "fuelType", "fuel");
                putText(parsed, result, "insuranceUpto", "insuranceUpto", "insuranceValidUpto");
                putText(parsed, result, "hypothecationBank", "financer", "hypothecationBank");
                putText(parsed, result, "fitnessUpto", "fitnessUpto", "fitnessValidUpto");
                putText(parsed, result, "registrationDate", "registrationDate", "regDate");
                putText(parsed, result, "makerModel", "makerModel", "model");
                putText(parsed, result, "vehicleManufacturerName", "vehicleManufacturerName", "maker");

                Iterator<Map.Entry<String, JsonNode>> fields = result.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    parsed.putIfAbsent(field.getKey(), field.getValue().asText(""));
                }
            }

            String errorMsg = success ? null : normalizedErrorMessage(root, result);
            double confidence = success ? 0.97 : 0.0;
            return new KycVerificationResult(success, confidence, parsed, transactionId, errorMsg);

        } catch (Exception e) {
            log.error("[Vahan] Failed to parse RC response: {}", e.getMessage());
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to parse Vahan RC response: " + e.getMessage());
        }
    }

    private KycVerificationResult simulatedResponse(String rcNumber, Map<String, Object> payload,
                                                      String transactionId) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("simulated", true);
        parsed.put("rcNumber", rcNumber);
        parsed.put("ownerName", payload.getOrDefault("ownerName", "Rajesh Kumar Sharma"));
        parsed.put("vehicleClass", "LMV");
        parsed.put("fuelType", "PETROL");
        parsed.put("makerModel", "MARUTI SWIFT VXI");
        parsed.put("vehicleManufacturerName", "MARUTI SUZUKI INDIA LTD");
        parsed.put("registrationDate",
                optionalString(payload, "registrationDate").isBlank()
                        ? LocalDate.now().minusYears(3).toString()
                        : optionalString(payload, "registrationDate"));
        parsed.put("insuranceUpto", LocalDate.now().plusMonths(8).toString());
        parsed.put("fitnessUpto", LocalDate.now().plusYears(2).toString());
        parsed.put("hypothecationBank", payload.getOrDefault("hypothecationBank", "NONE"));
        parsed.put("rcStatus", "ACTIVE");
        parsed.put("verified", true);
        return new KycVerificationResult(true, 0.95, parsed, transactionId, null);
    }

    private static String requiredRcNumber(Map<String, Object> payload) {
        String rc = optionalString(payload, "rcNumber", "registrationNumber", "vehicleRegistrationNumber");
        if (rc.isBlank()) {
            throw new IllegalArgumentException("RC number is required (rcNumber or registrationNumber).");
        }
        return rc.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String optionalString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String s = value.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    private static void putText(Map<String, Object> target, JsonNode result, String targetKey, String... sourceKeys) {
        for (String key : sourceKeys) {
            String value = result.path(key).asText("").trim();
            if (!value.isEmpty()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    private static int normalizedStatusCode(JsonNode root) {
        JsonNode camel = root.get("statusCode");
        if (camel != null && camel.isValueNode()) {
            try {
                return Integer.parseInt(camel.asText("").trim());
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
        return msg.isEmpty() ? "RC verification failed" : msg;
    }

    private boolean hasApiKey(IntegrationProperties.VahanProperties vahanConfig) {
        String key = resolveApiKey(vahanConfig);
        return key != null && !key.isBlank();
    }

    private String resolveApiKey(IntegrationProperties.VahanProperties vahanConfig) {
        if (vahanConfig.getApiKey() != null && !vahanConfig.getApiKey().isBlank()) {
            return vahanConfig.getApiKey();
        }
        IntegrationProperties.KarzaProperties karza = integrationProperties.getKarza();
        return karza != null ? karza.getApiKey() : "";
    }

    private static String normalizedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.karza.in/v2";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void saveAuditLog(String request, String response, String status, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime, Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("VAHAN")
                    .apiName("VAHAN_RC_VERIFY")
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
            log.error("[Vahan] Failed to save audit log: {}", e.getMessage());
        }
    }
}
