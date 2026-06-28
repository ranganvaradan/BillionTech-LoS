package com.los.core.service.vkyc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HypervergeVkycClient {

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateLink(UUID applicationId, String transactionId, Map<String, Object> inputs) {
        IntegrationProperties.HypervergeProperties hv = integrationProperties.getHyperverge();
        String workflowId = hv.getWorkflowId() == null ? "" : hv.getWorkflowId().trim();
        if (workflowId.isEmpty()) {
            // Fail fast with a clear, actionable message instead of letting an empty workflowId
            // reach HyperVerge (which previously surfaced as a silent payload mismatch).
            log.error("HyperVerge workflowId is not configured. Set property los.integration.hyperverge.workflow-id "
                    + "or env HYPERVERGE_WORKFLOW_ID. ApplicationId={}, transactionId={}", applicationId, transactionId);
            throw new RuntimeException("HyperVerge workflowId is not configured");
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("workflowId", workflowId);
        req.put("transactionId", transactionId);
        req.put("inputs", inputs);
        return postWithRetry(applicationId, "HV_GENERATE_VKYC_LINK", hv.getGenerateLinkApiUrl(), req, 2);
    }

    public Map<String, Object> fetchResult(UUID applicationId, String transactionId) {
        Map<String, Object> req = Map.of("transactionId", transactionId, "sendFlag", "yes", "bucketPathFlag", "yes");
        return postWithRetry(applicationId, "HV_FETCH_VKYC_RESULT", integrationProperties.getHyperverge().getResultApiUrl(), req, 2);
    }

    private Map<String, Object> postWithRetry(UUID applicationId, String apiName, String url, Map<String, Object> req, int retries) {
        Exception last = null;
        for (int attempt = 1; attempt <= retries + 1; attempt++) {
            Instant start = Instant.now();
            try {
                Map<String, Object> requestPayload = new LinkedHashMap<>(req);
                Object inputsObj = requestPayload.get("inputs");
                if (inputsObj instanceof Map<?, ?> inputsMapRaw) {
                    Map<String, Object> allInputs = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : inputsMapRaw.entrySet()) {
                        if (e.getKey() != null) {
                            allInputs.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }

                    // Only send mandatory fields to HyperVerge
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    inputs.put("panNumber", allInputs.getOrDefault("panNumber", ""));
                    inputs.put("aadhaarName", "NA");
                    inputs.put("aadhaarCreatedDate", "NA");
                    inputs.put("aadhaarImage", "NA");
                    inputs.put("address", allInputs.getOrDefault("address", ""));
                    inputs.put("pincode", allInputs.getOrDefault("pincode", ""));
                    inputs.put("dob", allInputs.getOrDefault("dob", ""));
                    requestPayload.put("inputs", inputs);

                    log.info("workflowId : {}", requestPayload.get("workflowId"));
                    log.info("transactionId : {}", requestPayload.get("transactionId"));
                    log.info("panNumber : {}", inputs.get("panNumber"));
                    log.info("dob : {}", inputs.get("dob"));
                    log.info("pincode : {}", inputs.get("pincode"));
                    log.info("address : {}", inputs.get("address"));

                    if (inputs.get("panNumber") == null || String.valueOf(inputs.get("panNumber")).isBlank()) {
                        throw new RuntimeException("PAN number missing for VKYC");
                    }
                    if (inputs.get("dob") == null || String.valueOf(inputs.get("dob")).isBlank()) {
                        throw new RuntimeException("DOB missing for VKYC");
                    }
                    if (inputs.get("address") == null || String.valueOf(inputs.get("address")).isBlank()) {
                        throw new RuntimeException("Address missing for VKYC");
                    }
                }

                String body = objectMapper.writeValueAsString(requestPayload);
                String appKey = integrationProperties.getHyperverge().getAppKey();
                String appKeyMasked = (appKey != null && appKey.length() >= 4) ? appKey.substring(0, 4) : "****";
                log.info("========== HYPERVERGE REQUEST ==========");
                log.info("ApplicationId : {}", applicationId);
                log.info("API Name      : {}", apiName);
                log.info("URL           : {}", url);
                log.info("HTTP Method   : POST");
                log.info("TransactionId : {}", requestPayload.get("transactionId"));
                log.info("Headers       : appId={}, appKey={}****",
                        integrationProperties.getHyperverge().getAppId(),
                        appKeyMasked);
                log.info("Request Body  : {}", body);
                log.info("HyperVerge Final Payload:\n{}",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestPayload));

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(integrationProperties.getHyperverge().getConnectTimeoutMs()))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(integrationProperties.getHyperverge().getReadTimeoutMs()))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("appId", integrationProperties.getHyperverge().getAppId())
                        .header("appKey", integrationProperties.getHyperverge().getAppKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                Instant end = Instant.now();
                long durationMs = Duration.between(start, end).toMillis();
                log.info("========== HYPERVERGE RESPONSE ==========");
                log.info("HTTP Status   : {}", response.statusCode());
                log.info("Response Headers : {}", response.headers().map());
                log.info("Response Body : {}", response.body());
                log.info("DurationMs    : {}", durationMs);
                saveAudit(applicationId, apiName, mask(body), mask(response.body()), response.statusCode(), null, requestPayload.get("transactionId"), start, end);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return objectMapper.readValue(response.body(), Map.class);
                }
                String responseBody = response.body();
                log.error("HyperVerge API failed. Status={}, Body={}", response.statusCode(), responseBody);
                log.error("HyperVerge Error Response: {}", responseBody);
                last = new RuntimeException(
                        "HyperVerge call failed: HTTP " + response.statusCode() + " Response: " + responseBody
                );
            } catch (Exception ex) {
                last = ex;
                if (attempt > retries) break;
            }
        }
        throw new RuntimeException(last != null ? last.getMessage() : "HyperVerge call failed: UNKNOWN");
    }

    private void saveAudit(UUID applicationId, String apiName, String req, String res, Integer status, String err, Object txn, Instant start, Instant end) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .applicationId(applicationId)
                    .providerName("HYPERVERGE")
                    .apiName(apiName)
                    .requestPayload(req)
                    .responsePayload(res)
                    .httpStatusCode(status)
                    .errorMessage(err)
                    .transactionId(txn != null ? String.valueOf(txn) : null)
                    .requestTime(start)
                    .responseTime(end)
                    .durationMs(Duration.between(start, end).toMillis())
                    .status(err == null ? "SUCCESS" : "FAILED")
                    .build());
        } catch (Exception ignored) {
        }
    }

    private String mask(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\"panNumber\"\\s*:\\s*\"([^\"]+)\"", "\"panNumber\":\"***\"")
                .replaceAll("(?i)\"aadhaar[^\\\"]*\"\\s*:\\s*\"([^\"]+)\"", "\"aadhaar\":\"***\"");
    }
}
