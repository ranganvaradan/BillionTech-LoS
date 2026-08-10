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
 * Hyperverge Video KYC Provider — adapted from legacy FullKycServiceFacadeImpl + HyperVergeController.
 *
 * Supports:
 *   - VIDEO_KYC: Generate VKYC session URL via Hyperverge link-generation API
 *   - FACE_MATCH: Liveness + face match verification
 *   - LIVENESS: Liveness check only
 *
 * Flow (VIDEO_KYC):
 *   1. Build payload with workflowId, transactionId, and borrower inputs (PAN, address, DOB, etc.)
 *   2. POST to Hyperverge generate-link API with appId/appKey headers
 *   3. Parse response to extract startKycUrl
 *   4. Return URL to frontend for borrower to complete VKYC
 *
 * Legacy reference:
 *   - bl-core/.../facade/impl/FullKycServiceFacadeImpl.java (generateVkycURL, apiCall)
 *   - bl-core/.../web/rest/HyperVergeController.java (webhook, status, video download)
 */
@Slf4j
@Component("hypervergeKycProvider")
@RequiredArgsConstructor
public class HypervergeKycProvider implements IKycProvider {

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    private static final Set<KycStepType> SUPPORTED = Set.of(
            KycStepType.FACE_MATCH, KycStepType.LIVENESS, KycStepType.VIDEO_KYC
    );

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        log.info("[Hyperverge] Executing {} verification", stepType);

        IntegrationProperties.HypervergeProperties config = integrationProperties.getHyperverge();
        String transactionId = "HV-" + UUID.randomUUID().toString().substring(0, 8);

        if (config.getAppId() == null || config.getAppId().isBlank()) {
            log.warn("[Hyperverge] Credentials not configured — returning simulated response for {}", stepType);
            return simulatedFallback(stepType, payload, transactionId);
        }

        return switch (stepType) {
            case VIDEO_KYC -> initiateVideoKyc(payload, config, transactionId);
            case FACE_MATCH -> executeFaceMatch(payload, config, transactionId);
            case LIVENESS -> executeLivenessCheck(payload, config, transactionId);
            default -> new KycVerificationResult(false, 0.0, null, transactionId,
                    "Unsupported step type: " + stepType);
        };
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return SUPPORTED.contains(stepType);
    }

    @Override
    public String getProviderName() {
        return "HYPERVERGE";
    }

    /**
     * Initiate Video KYC — generates a VKYC session URL.
     * Adapted from legacy FullKycServiceFacadeImpl.generateVkycURL()
     */
    private KycVerificationResult initiateVideoKyc(Map<String, Object> payload,
                                                     IntegrationProperties.HypervergeProperties config,
                                                     String transactionId) {
        Instant requestTime = Instant.now();

        try {
            // Build input payload — from legacy code
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("panNumber", payload.getOrDefault("panNumber", ""));
            inputs.put("address", payload.getOrDefault("address", ""));
            inputs.put("pincode", payload.getOrDefault("pincode", ""));
            inputs.put("dob", payload.getOrDefault("dob", ""));

            // Optional Aadhaar data if available
            if (payload.containsKey("aadhaarName")) {
                inputs.put("aadhaarName", payload.get("aadhaarName"));
                inputs.put("aadhaarCreatedDate", payload.getOrDefault("aadhaarCreatedDate", ""));
                inputs.put("aadhaarImage", payload.getOrDefault("aadhaarImage", "NA"));
            }

            // Build complete payload — from legacy code structure
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("workflowId", config.getWorkflowId());
            requestPayload.put("transactionId", transactionId);
            requestPayload.put("inputs", inputs);

            String requestJson = objectMapper.writeValueAsString(requestPayload);
            log.info("[Hyperverge] Generate VKYC link payload: {}", requestJson);

            // API call — from legacy apiCall() method
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getGenerateLinkUrl()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("appId", config.getAppId())
                    .header("appKey", config.getAppKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Hyperverge] VKYC link generation: HTTP {} in {}ms", response.statusCode(), durationMs);

            // Audit log
            saveAuditLog("HYPERVERGE", "HV_GENERATE_VKYC_LINK", requestJson,
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                return parseGenerateLinkResponse(response.body(), transactionId);
            } else {
                return new KycVerificationResult(false, 0.0, null, transactionId,
                        "Hyperverge API returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[Hyperverge] VKYC link generation failed: {}", e.getMessage(), e);
            saveAuditLog("HYPERVERGE", "HV_GENERATE_VKYC_LINK", null,
                    null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Hyperverge error: " + e.getMessage());
        }
    }

    /**
     * Parse Hyperverge generate-link response.
     * Expected: { "statusCode": 200, "result": { "startKycUrl": "https://..." } }
     */
    private KycVerificationResult parseGenerateLinkResponse(String responseBody, String transactionId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int statusCode = root.path("statusCode").asInt(0);
            JsonNode result = root.path("result");

            if (statusCode == 200 && result.has("startKycUrl") && !result.path("startKycUrl").isNull()) {
                String vkycUrl = result.path("startKycUrl").asText("");
                if (!vkycUrl.isEmpty()) {
                    Map<String, Object> parsed = new LinkedHashMap<>();
                    parsed.put("vkycUrl", vkycUrl);
                    parsed.put("status", "PENDING");
                    parsed.put("workflowId", result.path("workflowId").asText(""));
                    return new KycVerificationResult(true, 0.95, parsed, transactionId, null);
                }
            }

            String errorMsg = root.path("error").asText(root.path("message").asText("Link generation failed"));
            return new KycVerificationResult(false, 0.0, null, transactionId, errorMsg);

        } catch (Exception e) {
            log.error("[Hyperverge] Failed to parse generate-link response: {}", e.getMessage());
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Failed to parse Hyperverge response: " + e.getMessage());
        }
    }

    /**
     * Execute face match verification via Hyperverge API.
     */
    private KycVerificationResult executeFaceMatch(Map<String, Object> payload,
                                                    IntegrationProperties.HypervergeProperties config,
                                                    String transactionId) {
        String selfieImage = (String) payload.get("selfieImage");
        String documentImage = (String) payload.get("documentImage");

        if (selfieImage == null || documentImage == null) {
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Both selfie and document images are required");
        }

        Instant requestTime = Instant.now();

        try {
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("selfie", selfieImage);
            requestPayload.put("id_card", documentImage);
            requestPayload.put("type", "face_match");

            String requestJson = objectMapper.writeValueAsString(requestPayload);

            // Use the base URL for face match (different endpoint)
            String faceMatchUrl = config.getGenerateLinkUrl().replace("/link/generate", "/faceMatch");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(faceMatchUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("appId", config.getAppId())
                    .header("appKey", config.getAppKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            saveAuditLog("HYPERVERGE", "HV_FACE_MATCH", "[IMAGE_DATA_OMITTED]",
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                double matchScore = root.path("result").path("match").path("value").asDouble(0.0);
                boolean livenessDetected = root.path("result").path("liveness").path("value").asBoolean(false);

                Map<String, Object> parsed = new LinkedHashMap<>();
                parsed.put("matchScore", matchScore);
                parsed.put("livenessDetected", livenessDetected);
                parsed.put("spoofDetected", root.path("result").path("spoof").path("value").asBoolean(false));
                parsed.put("faceDetected", true);
                parsed.put("matchThreshold", 0.80);

                return new KycVerificationResult(matchScore >= 0.80, matchScore, parsed, transactionId, null);
            } else {
                return new KycVerificationResult(false, 0.0, null, transactionId,
                        "Face match API returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[Hyperverge] Face match failed: {}", e.getMessage(), e);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Face match error: " + e.getMessage());
        }
    }

    /**
     * Execute liveness check via Hyperverge API.
     */
    private KycVerificationResult executeLivenessCheck(Map<String, Object> payload,
                                                        IntegrationProperties.HypervergeProperties config,
                                                        String transactionId) {
        String selfieImage = (String) payload.get("selfieImage");
        if (selfieImage == null) {
            return new KycVerificationResult(false, 0.0, null, transactionId, "Selfie image is required");
        }

        Instant requestTime = Instant.now();

        try {
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("selfie", selfieImage);
            requestPayload.put("type", "liveness");

            String requestJson = objectMapper.writeValueAsString(requestPayload);
            String livenessUrl = config.getGenerateLinkUrl().replace("/link/generate", "/liveness");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(livenessUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("appId", config.getAppId())
                    .header("appKey", config.getAppKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            saveAuditLog("HYPERVERGE", "HV_LIVENESS", "[IMAGE_DATA_OMITTED]",
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                boolean livenessDetected = root.path("result").path("liveness").path("value").asBoolean(false);
                double confidence = root.path("result").path("liveness").path("confidence").asDouble(0.0);

                Map<String, Object> parsed = new LinkedHashMap<>();
                parsed.put("livenessDetected", livenessDetected);
                parsed.put("confidence", confidence);
                parsed.put("faceDetected", root.path("result").path("faceDetected").asBoolean(true));

                return new KycVerificationResult(livenessDetected, confidence, parsed, transactionId, null);
            } else {
                return new KycVerificationResult(false, 0.0, null, transactionId,
                        "Liveness API returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[Hyperverge] Liveness check failed: {}", e.getMessage(), e);
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Liveness error: " + e.getMessage());
        }
    }

    /**
     * Fallback simulated response when credentials are not configured.
     */
    private KycVerificationResult simulatedFallback(KycStepType stepType, Map<String, Object> payload, String transactionId) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("simulated", true);

        switch (stepType) {
            case VIDEO_KYC -> {
                parsed.put("vkycUrl", "https://vkyc.example.com/session/" + transactionId);
                parsed.put("status", "PENDING");
                return new KycVerificationResult(true, 0.95, parsed, transactionId, null);
            }
            case FACE_MATCH -> {
                parsed.put("matchScore", 0.92);
                parsed.put("livenessDetected", true);
                parsed.put("spoofDetected", false);
                parsed.put("faceDetected", true);
                parsed.put("matchThreshold", 0.80);
                return new KycVerificationResult(true, 0.92, parsed, transactionId, null);
            }
            case LIVENESS -> {
                parsed.put("livenessDetected", true);
                parsed.put("confidence", 0.95);
                parsed.put("faceDetected", true);
                return new KycVerificationResult(true, 0.95, parsed, transactionId, null);
            }
            default -> {
                return new KycVerificationResult(true, 0.90, parsed, transactionId, null);
            }
        }
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
            log.error("[Hyperverge] Failed to save audit log: {}", e.getMessage());
        }
    }
}
