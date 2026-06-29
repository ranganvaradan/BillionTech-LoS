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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CKYC central registry: download delegates to {@link AuthbridgeKycProvider};
 * upload posts customer KYC data to the configured CKYC intermediary (Karza / Authbridge).
 */
@Slf4j
@Component("ckycRegistryKycProvider")
@RequiredArgsConstructor
public class CkycRegistryKycProvider implements IKycProvider {

    private static final String DEFAULT_KARZA_UPLOAD_PATH = "/v3/ckyc-upload";

    private final AuthbridgeKycProvider authbridgeKycProvider;
    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        if (stepType == KycStepType.CKYC_DOWNLOAD && authbridgeKycProvider.supports(KycStepType.CKYC_DOWNLOAD)) {
            return authbridgeKycProvider.verify(KycStepType.CKYC_DOWNLOAD, payload);
        }
        if (stepType == KycStepType.CKYC_UPLOAD) {
            return uploadCkycRecord(payload);
        }
        return new KycVerificationResult(false, 0.0, null, null, "CKYC step not available");
    }

    private KycVerificationResult uploadCkycRecord(Map<String, Object> payload) {
        log.info("[CKYC] Upload with payload keys: {}", payload != null ? payload.keySet() : "null");

        String referenceId = "CKYC-UP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        try {
            validateUploadPayload(payload);
        } catch (IllegalArgumentException e) {
            return new KycVerificationResult(false, 0.0, null, referenceId, e.getMessage());
        }

        IntegrationProperties.CkycProperties config = integrationProperties.getCkyc();
        if (config.isSimulation() || !hasLiveUploadConfig(config)) {
            log.info("[CKYC] Simulation mode — returning dummy KIN for reference {}", referenceId);
            return simulatedUploadResponse(payload, referenceId);
        }

        return callCkycUploadApi(config, payload, referenceId);
    }

    private KycVerificationResult callCkycUploadApi(IntegrationProperties.CkycProperties config,
                                                    Map<String, Object> payload,
                                                    String referenceId) {
        String url = resolveUploadUrl(config);
        String apiKey = resolveApiKey(config);
        Map<String, Object> requestBody = buildUploadPayload(payload, referenceId, config);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            return new KycVerificationResult(false, 0.0, null, referenceId,
                    "Failed to serialize CKYC upload request: " + e.getMessage());
        }

        Instant requestTime = Instant.now();
        log.info("[CKYC][REQUEST] URL: {} Body keys: {}", url, requestBody.keySet());

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson));

            applyAuthHeader(requestBuilder, config, apiKey);

            HttpResponse<String> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[CKYC][RESPONSE] HTTP {} in {}ms", response.statusCode(), durationMs);

            saveAuditLog(requestJson, response.body(),
                    response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, referenceId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                return parseUploadResponse(response.body(), referenceId);
            }
            return new KycVerificationResult(false, 0.0, null, referenceId,
                    "CKYC upload API returned HTTP " + response.statusCode());

        } catch (Exception e) {
            log.error("[CKYC] Upload API call failed: {}", e.getMessage(), e);
            saveAuditLog(requestJson, null, "ERROR", null, e.getMessage(), referenceId,
                    requestTime, Instant.now(), null);
            return new KycVerificationResult(false, 0.0, null, referenceId,
                    "CKYC upload API error: " + e.getMessage());
        }
    }

    private KycVerificationResult parseUploadResponse(String responseBody, String referenceId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String status = firstNonBlank(
                    root.path("status").asText(null),
                    root.path("statusCode").asText(null),
                    root.path("responseCode").asText(null)
            );
            boolean success = isSuccessStatus(status, root);

            String kin = extractKin(root);
            String message = firstNonBlank(
                    root.path("message").asText(null),
                    root.path("statusMessage").asText(null),
                    root.path("errorMessage").asText(null)
            );

            if (!success) {
                return new KycVerificationResult(false, 0.0, null, referenceId,
                        message != null ? message : "CKYC upload rejected by registry");
            }

            if (kin == null || kin.isBlank()) {
                return new KycVerificationResult(false, 0.0, null, referenceId,
                        "CKYC upload succeeded but KIN was not returned");
            }

            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("recordStatus", "ACCEPTED");
            parsed.put("kin", kin);
            parsed.put("status", status != null ? status : "SUCCESS");
            parsed.put("message", message != null ? message : "CKYC record uploaded successfully");
            parsed.put("referenceId", referenceId);
            parsed.put("simulated", false);

            return new KycVerificationResult(true, 1.0, parsed, kin, null);

        } catch (Exception e) {
            log.error("[CKYC] Failed to parse upload response: {}", e.getMessage(), e);
            return new KycVerificationResult(false, 0.0, null, referenceId,
                    "Failed to parse CKYC upload response: " + e.getMessage());
        }
    }

    private KycVerificationResult simulatedUploadResponse(Map<String, Object> payload, String referenceId) {
        String pan = optionalString(payload, "panNumber", "pan");
        String kin = simulatedKin(pan, referenceId);

        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("recordStatus", "ACCEPTED");
        parsed.put("kin", kin);
        parsed.put("status", "SUCCESS");
        parsed.put("message", "CKYC record uploaded successfully (simulation)");
        parsed.put("referenceId", referenceId);
        parsed.put("simulated", true);

        return new KycVerificationResult(true, 1.0, parsed, kin, null);
    }

    private Map<String, Object> buildUploadPayload(Map<String, Object> payload,
                                                     String referenceId,
                                                     IntegrationProperties.CkycProperties config) {
        Object prebuilt = payload.get("ckycUploadPayload");
        if (prebuilt instanceof Map<?, ?> prebuiltMap) {
            Map<String, Object> body = new LinkedHashMap<>();
            prebuiltMap.forEach((k, v) -> body.put(String.valueOf(k), v));
            body.putIfAbsent("referenceId", referenceId);
            return body;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consent", "Y");
        body.put("purpose", optionalString(payload, "purpose", "LOAN_ORIGINATION"));
        body.put("referenceId", referenceId);
        if (config.getFiCode() != null && !config.getFiCode().isBlank()) {
            body.put("fiCode", config.getFiCode());
        }
        if (config.getBranchCode() != null && !config.getBranchCode().isBlank()) {
            body.put("branchCode", config.getBranchCode());
        }

        Map<String, Object> personalInfo = new LinkedHashMap<>();
        copyIfPresent(personalInfo, payload, "panNumber", "pan");
        copyIfPresent(personalInfo, payload, "name", "fullName", "accountHolderName");
        copyIfPresent(personalInfo, payload, "dob", "dateOfBirth");
        copyIfPresent(personalInfo, payload, "gender");
        copyIfPresent(personalInfo, payload, "mobile", "phone");
        copyIfPresent(personalInfo, payload, "email");
        copyIfPresent(personalInfo, payload, "fatherName");
        copyIfPresent(personalInfo, payload, "motherName");
        copyIfPresent(personalInfo, payload, "aadhaarNumber", "aadhaar");
        body.put("personalInfo", personalInfo);

        Object address = payload.get("address");
        if (address != null) {
            body.put("address", address);
        } else {
            Map<String, Object> addressMap = new LinkedHashMap<>();
            copyIfPresent(addressMap, payload, "addressLine1", "line1");
            copyIfPresent(addressMap, payload, "addressLine2", "line2");
            copyIfPresent(addressMap, payload, "city");
            copyIfPresent(addressMap, payload, "state");
            copyIfPresent(addressMap, payload, "pincode", "pinCode");
            if (!addressMap.isEmpty()) {
                body.put("address", addressMap);
            }
        }

        copyIfPresent(body, payload, "applicationId", "applicationNumber");
        return body;
    }

    private void validateUploadPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("CKYC upload payload is required.");
        }
        if (payload.get("ckycUploadPayload") instanceof Map<?, ?>) {
            return;
        }
        String pan = optionalString(payload, "panNumber", "pan");
        if (pan.length() != 10) {
            throw new IllegalArgumentException("Valid PAN number is required for CKYC upload.");
        }
        String name = optionalString(payload, "name", "fullName", "accountHolderName");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required for CKYC upload.");
        }
    }

    private static boolean isSuccessStatus(String status, JsonNode root) {
        if (status == null) {
            return root.path("kin").isTextual() || extractKin(root) != null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized) || "S00000".equals(normalized) || "101".equals(normalized)) {
            return true;
        }
        if ("FAILED".equals(normalized) || "FAILURE".equals(normalized) || "ERROR".equals(normalized)) {
            return false;
        }
        try {
            int code = Integer.parseInt(normalized);
            return code >= 100 && code < 200;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String extractKin(JsonNode root) {
        String[] keys = {"kin", "KIN", "ckycId", "ckycNumber", "ckyc_id", "ckycNo"};
        for (String key : keys) {
            String value = root.path(key).asText(null);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        JsonNode result = root.path("result");
        if (result.isObject()) {
            for (String key : keys) {
                String value = result.path(key).asText(null);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        JsonNode data = root.path("data");
        if (data.isObject()) {
            for (String key : keys) {
                String value = data.path(key).asText(null);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        JsonNode additional = root.path("additionalData");
        if (additional.isObject()) {
            String ckycId = additional.path("ckycId").asText(null);
            if (ckycId != null && !ckycId.isBlank()) {
                return ckycId.trim();
            }
        }
        return findKinRecursive(root);
    }

    private static String findKinRecursive(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("kin") || key.contains("ckyc")) {
                    String text = entry.getValue().asText(null);
                    if (text != null && text.matches("\\d{10,16}")) {
                        return text.trim();
                    }
                }
                String nested = findKinRecursive(entry.getValue());
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                String nested = findKinRecursive(element);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String simulatedKin(String pan, String referenceId) {
        int seed = Math.abs((pan + referenceId).hashCode());
        long numeric = 1_000_000_000_000L + (seed % 9_000_000_000_000L);
        return String.format(Locale.ROOT, "%014d", numeric).substring(0, 14);
    }

    private boolean hasLiveUploadConfig(IntegrationProperties.CkycProperties config) {
        return resolveApiKey(config) != null && !resolveApiKey(config).isBlank()
                && resolveUploadUrl(config) != null && !resolveUploadUrl(config).isBlank();
    }

    private String resolveUploadUrl(IntegrationProperties.CkycProperties config) {
        if (config.getUploadUrl() != null && !config.getUploadUrl().isBlank()) {
            return config.getUploadUrl().trim();
        }
        if ("AUTHBRIDGE".equalsIgnoreCase(config.getProvider())) {
            return "";
        }
        return normalizedBaseUrl(integrationProperties.getKarza().getBaseUrl()) + DEFAULT_KARZA_UPLOAD_PATH;
    }

    private String resolveApiKey(IntegrationProperties.CkycProperties config) {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        IntegrationProperties.KarzaProperties karza = integrationProperties.getKarza();
        return karza != null ? karza.getApiKey() : "";
    }

    private static void applyAuthHeader(HttpRequest.Builder requestBuilder,
                                        IntegrationProperties.CkycProperties config,
                                        String apiKey) {
        if ("AUTHBRIDGE".equalsIgnoreCase(config.getProvider())) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        } else {
            requestBuilder.header("x-karza-key", apiKey);
        }
    }

    private static String normalizedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.karza.in";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                target.putIfAbsent(keys[0], value);
                return;
            }
        }
    }

    private static String optionalString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String optionalString(Map<String, Object> payload, String key, String defaultValue) {
        String value = optionalString(payload, key);
        return value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void saveAuditLog(String request, String response, String status, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime, Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("CKYC_REGISTRY")
                    .apiName("CKYC_UPLOAD")
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
            log.error("[CKYC] Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return stepType == KycStepType.CKYC_DOWNLOAD || stepType == KycStepType.CKYC_UPLOAD;
    }

    @Override
    public String getProviderName() {
        return "CKYC_REGISTRY";
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
