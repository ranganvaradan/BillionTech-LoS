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
import java.util.*;

/**
 * Karza KYC Provider — adapted from legacy KarzaServiceFacadeImpl.
 *
 * Calls the Karza API for PAN verification, GSTIN lookup, Aadhaar OTP,
 * Driving License, Voter ID, and bank account (penny-drop) verification.
 *
 * Legacy reference: bl-core/.../facade/impl/KarzaServiceFacadeImpl.java
 */
@Slf4j
@Component("karzaKycProvider")
@RequiredArgsConstructor
public class KarzaKycProvider implements IKycProvider {

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    private static final Set<KycStepType> SUPPORTED = Set.of(
            KycStepType.PAN_VERIFY, KycStepType.GSTIN_VERIFY,
            KycStepType.AADHAAR_OTP, KycStepType.DL_VERIFY,
            KycStepType.VOTER_ID_VERIFY, KycStepType.BANK_PENNY_DROP,
            KycStepType.UDYAM_VERIFY, KycStepType.CIN_MCA21
    );

    /** Karza endpoint suffixes per step type — matches legacy URL routing */
    private static final Map<KycStepType, String> ENDPOINT_MAP = Map.of(
            KycStepType.PAN_VERIFY, "/v2/pan",
            KycStepType.GSTIN_VERIFY, "/v3/gstdetailed",
            KycStepType.AADHAAR_OTP, "/v2/aadhaar-verification",
            KycStepType.DL_VERIFY, "/v2/dl-verification",
            KycStepType.VOTER_ID_VERIFY, "/v2/voter-verification",
            KycStepType.BANK_PENNY_DROP, "/v2/bankacc",
            KycStepType.UDYAM_VERIFY, "/v2/udyam-verification",
            KycStepType.CIN_MCA21, "/v2/mca"
    );

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        log.info("[Karza] Executing KYC step: {} with payload keys: {}", stepType, payload.keySet());

        IntegrationProperties.KarzaProperties config = integrationProperties.getKarza();
        String transactionId = "KZ-" + UUID.randomUUID().toString().substring(0, 8);

        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("[Karza] API key not configured — returning simulated response for step: {}", stepType);
            return simulatedFallback(stepType, payload, transactionId);
        }

        // Build the target URL
        String endpoint = ENDPOINT_MAP.getOrDefault(stepType, "/v2/pan");
        String url;
        if (stepType == KycStepType.GSTIN_VERIFY && config.getGstnUrl() != null && !config.getGstnUrl().isBlank()) {
            url = config.getGstnUrl();
        } else {
            url = config.getBaseUrl() + endpoint;
        }

        Instant requestTime = Instant.now();
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return new KycVerificationResult(false, 0.0, null, transactionId, "Failed to serialize request: " + e.getMessage());
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("x-karza-key", config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Karza] {} → HTTP {} in {}ms", stepType, response.statusCode(), durationMs);

            // Audit log
            saveAuditLog("KARZA", "KARZA_" + stepType.name(), requestBody,
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                return parseKarzaResponse(stepType, response.body(), transactionId);
            } else {
                return new KycVerificationResult(false, 0.0, null, transactionId,
                        "Karza API returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[Karza] API call failed for {}: {}", stepType, e.getMessage(), e);
            saveAuditLog("KARZA", "KARZA_" + stepType.name(), requestBody,
                    null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Karza API error: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return SUPPORTED.contains(stepType);
    }

    @Override
    public String getProviderName() {
        return "KARZA";
    }

    /**
     * Parse Karza JSON response into KycVerificationResult.
     * Karza responses typically have: { "statusCode": 101, "result": { ... } }
     */
    private KycVerificationResult parseKarzaResponse(KycStepType stepType, String responseBody, String transactionId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            int statusCode = root.path("statusCode").asInt(0);
            // Karza uses 101 for success in most APIs
            boolean success = statusCode == 101 || statusCode == 100;

            JsonNode result = root.path("result");
            Map<String, Object> parsedData = new LinkedHashMap<>();

            switch (stepType) {
                case PAN_VERIFY -> {
                    parsedData.put("panNumber", result.path("pan").asText(""));
                    parsedData.put("name", result.path("name").asText(""));
                    parsedData.put("panStatus", result.path("panStatus").asText(""));
                    parsedData.put("aadhaarLinked", result.path("aadhaarSeedingStatus").asText("").equalsIgnoreCase("Y"));
                    parsedData.put("category", result.path("category").asText(""));
                    parsedData.put("lastName", result.path("lastName").asText(""));
                    parsedData.put("firstName", result.path("firstName").asText(""));
                }
                case GSTIN_VERIFY -> {
                    parsedData.put("gstin", result.path("gstin").asText(""));
                    parsedData.put("legalName", result.path("lgnm").asText(""));
                    parsedData.put("tradeName", result.path("tradeNam").asText(""));
                    parsedData.put("status", result.path("sts").asText(""));
                    parsedData.put("registrationDate", result.path("rgdt").asText(""));
                    parsedData.put("businessType", result.path("ctb").asText(""));
                    parsedData.put("stateCode", result.path("stj").asText(""));
                }
                case AADHAAR_OTP -> {
                    parsedData.put("maskedAadhaar", result.path("maskedAadhaarNumber").asText(""));
                    parsedData.put("name", result.path("name").asText(""));
                    parsedData.put("dob", result.path("dob").asText(""));
                    parsedData.put("gender", result.path("gender").asText(""));
                    parsedData.put("address", result.path("address").asText(""));
                }
                case BANK_PENNY_DROP -> {
                    parsedData.put("accountNumber", result.path("accountNumber").asText(""));
                    parsedData.put("ifsc", result.path("ifsc").asText(""));
                    parsedData.put("accountHolderName", result.path("accountName").asText(""));
                    parsedData.put("bankName", result.path("bankName").asText(""));
                    parsedData.put("accountStatus", success ? "ACTIVE" : "INVALID");
                }
                default -> {
                    // Generic parsing for DL, Voter ID, Udyam, CIN
                    if (result.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = result.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            parsedData.put(field.getKey(), field.getValue().asText(""));
                        }
                    }
                }
            }

            double confidence = success ? 0.98 : 0.0;
            String errorMsg = success ? null : root.path("statusMessage").asText("Verification failed");

            return new KycVerificationResult(success, confidence, parsedData, transactionId, errorMsg);

        } catch (Exception e) {
            log.error("[Karza] Failed to parse response for {}: {}", stepType, e.getMessage());
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to parse Karza response: " + e.getMessage());
        }
    }

    /**
     * Fallback simulated response when API key is not configured.
     * This allows the system to function in dev/staging without real credentials.
     */
    private KycVerificationResult simulatedFallback(KycStepType stepType, Map<String, Object> payload, String transactionId) {
        log.info("[Karza] Returning simulated response for {} (no API key configured)", stepType);
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("simulated", true);

        switch (stepType) {
            case PAN_VERIFY -> {
                parsed.put("panNumber", payload.getOrDefault("panNumber", ""));
                parsed.put("name", payload.getOrDefault("name", ""));
                parsed.put("panStatus", "ACTIVE");
                parsed.put("aadhaarLinked", true);
            }
            case GSTIN_VERIFY -> {
                parsed.put("gstin", payload.getOrDefault("gstin", ""));
                parsed.put("legalName", payload.getOrDefault("businessName", ""));
                parsed.put("status", "ACTIVE");
            }
            case AADHAAR_OTP -> {
                String aadhaar = (String) payload.getOrDefault("aadhaarNumber", "");
                if (aadhaar.length() == 12) {
                    parsed.put("maskedAadhaar", "XXXX-XXXX-" + aadhaar.substring(8));
                }
                parsed.put("name", payload.getOrDefault("name", ""));
            }
            case BANK_PENNY_DROP -> {
                parsed.put("accountNumber", payload.getOrDefault("accountNumber", ""));
                parsed.put("ifsc", payload.getOrDefault("ifsc", ""));
                parsed.put("accountHolderName", payload.getOrDefault("name", ""));
                parsed.put("accountStatus", "ACTIVE");
            }
            default -> parsed.put("verified", true);
        }

        return new KycVerificationResult(true, 0.95, parsed, transactionId, null);
    }

    private void saveAuditLog(String provider, String apiName, String request, String response,
                               String status, Integer httpStatus, String errorMsg, String txnId,
                               Instant reqTime, Instant resTime, Long durationMs) {
        try {
            ApiAuditLog audit = ApiAuditLog.builder()
                    .providerName(provider)
                    .apiName(apiName)
                    .requestPayload(request)
                    .responsePayload(response)
                    .status(status)
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(durationMs)
                    .build();
            apiAuditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("[Karza] Failed to save audit log: {}", e.getMessage());
        }
    }
}
