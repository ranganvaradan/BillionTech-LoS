package com.los.core.service.integration.itr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.core.config.IntegrationProperties;
import com.los.core.service.audit.IntegrationApiAuditService;
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
import java.util.Map;
import java.util.UUID;

/**
 * Karza ITR return-forms HTTP client. Passwords are never logged or audited in clear text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KarzaItrReturnFormsClient {

    public static final int STATUS_SUCCESS = 101;

    private final IntegrationProperties integrationProperties;
    private final IntegrationApiAuditService integrationApiAuditService;
    private final ObjectMapper objectMapper;

    public record KarzaItrHttpResult(
            boolean success,
            int karzaStatusCode,
            int httpStatusCode,
            String requestId,
            JsonNode root,
            JsonNode result,
            String rawResponse,
            String errorMessage,
            boolean simulated
    ) {
    }

    public KarzaItrHttpResult fetch(String username, String password, UUID applicationId) {
        IntegrationProperties.KarzaProperties config = integrationProperties.getKarza();
        // ITR uses GST Karza product key — never identity KARZA_API_KEY
        String apiKey = config.getGstKarzaKey() == null ? "" : config.getGstKarzaKey().trim();
        String url = config.getItrReturnFormsUrl() == null || config.getItrReturnFormsUrl().isBlank()
                ? "https://api.karza.in/itr/uat/v1/itr-return-forms"
                : config.getItrReturnFormsUrl().trim();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username == null ? "" : username.trim());
        body.put("password", password == null ? "" : password);
        body.put("consent", "Y");
        body.put("apiVersion", blankToDefault(config.getItrApiVersion(), "1.0.2"));
        body.put("additionalData", false);
        body.put("numberOfYears", config.getItrNumberOfYears() > 0 ? config.getItrNumberOfYears() : 3);
        body.put("partialReport", true);

        Instant requestTime = Instant.now();
        String redactedRequestJson = redactPasswordJson(body);

        if (apiKey.isEmpty()) {
            KarzaItrHttpResult simulated = simulateSuccess(username, requestTime);
            Instant responseTime = Instant.now();
            integrationApiAuditService.record(
                    "KARZA",
                    "KARZA_ITR_RETURN_FORMS",
                    redactedRequestJson,
                    simulated.rawResponse(),
                    "SUCCESS",
                    200,
                    null,
                    simulated.requestId(),
                    applicationId,
                    requestTime,
                    responseTime,
                    Duration.between(requestTime, responseTime).toMillis());
            return simulated;
        }

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            int connectMs = Math.max(config.getConnectTimeoutMs(), 5000);
            int readMs = config.getItrReadTimeoutMs() > 0
                    ? config.getItrReadTimeoutMs()
                    : Math.max(config.getReadTimeoutMs(), 30_000);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(readMs))
                    .header("Content-Type", "application/json")
                    .header("x-karza-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.info("[Karza ITR] POST {} for applicationId={} (using GST Karza key; password redacted)", url, applicationId);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();
            String responseBody = response.body() == null ? "" : response.body();

            JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
            int karzaStatus = normalizedStatusCode(root);
            String requestId = textOrNull(root, "requestId");
            JsonNode resultNode = root.get("result");
            boolean httpOk = response.statusCode() == 200;
            boolean success = httpOk && karzaStatus == STATUS_SUCCESS;

            integrationApiAuditService.record(
                    "KARZA",
                    "KARZA_ITR_RETURN_FORMS",
                    redactedRequestJson,
                    responseBody,
                    success ? "SUCCESS" : "FAILED",
                    response.statusCode(),
                    success ? null : "Karza statusCode=" + karzaStatus,
                    requestId,
                    applicationId,
                    requestTime,
                    responseTime,
                    durationMs);

            if (!httpOk) {
                return new KarzaItrHttpResult(
                        false,
                        karzaStatus,
                        response.statusCode(),
                        requestId,
                        root,
                        resultNode,
                        responseBody,
                        "ITR provider returned HTTP " + response.statusCode(),
                        false);
            }
            if (!success) {
                return new KarzaItrHttpResult(
                        false,
                        karzaStatus,
                        response.statusCode(),
                        requestId,
                        root,
                        resultNode,
                        responseBody,
                        "ITR verification failed (statusCode " + karzaStatus + ")",
                        false);
            }
            return new KarzaItrHttpResult(
                    true,
                    karzaStatus,
                    response.statusCode(),
                    requestId,
                    root,
                    resultNode,
                    responseBody,
                    null,
                    false);
        } catch (Exception e) {
            Instant responseTime = Instant.now();
            log.error("[Karza ITR] call failed for applicationId={}: {}", applicationId, e.getMessage());
            integrationApiAuditService.record(
                    "KARZA",
                    "KARZA_ITR_RETURN_FORMS",
                    redactedRequestJson,
                    null,
                    "ERROR",
                    null,
                    e.getMessage(),
                    null,
                    applicationId,
                    requestTime,
                    responseTime,
                    Duration.between(requestTime, responseTime).toMillis());
            return new KarzaItrHttpResult(
                    false,
                    0,
                    0,
                    null,
                    null,
                    null,
                    null,
                    "ITR provider error: " + e.getMessage(),
                    false);
        }
    }

    /** Redact password fields in JSON strings for audit / safe logging. */
    public static String maskPasswordInJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        return json.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
    }

    String redactPasswordJson(Map<String, Object> body) {
        try {
            ObjectNode node = objectMapper.valueToTree(body);
            if (node.has("password")) {
                node.put("password", "***");
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"username\":\"?\",\"password\":\"***\"}";
        }
    }

    private KarzaItrHttpResult simulateSuccess(String username, Instant requestTime) {
        String requestId = "sim-" + UUID.randomUUID();
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ObjectNode formDetails = result.putObject("formDetails");
            formDetails.put("assessmentYear", "2025-26");
            formDetails.put("financialYear", "2024-25");
            formDetails.put("formName", "ITR-6");

            ObjectNode gen = result.putObject("generalInformation");
            gen.put("entityName", "SIMULATED ITR ENTITY");
            gen.put("entityPan", username == null || username.isBlank() ? "ABCDE1234F" : username.trim().toUpperCase());

            var finArr = result.putArray("financialInformation");
            ObjectNode year = finArr.addObject();
            year.put("assessmentYear", "2025-26");
            year.put("financialYear", "2024-25");
            ObjectNode pl = year.putObject("profitAndLoss");
            pl.put("totalRevenue", 1_200_000);
            pl.put("profitAfterTax", 450_000);
            pl.put("ebitda", 650_000);
            pl.put("interestExpense", 300_000);
            ObjectNode bs = year.putObject("balanceSheet");
            bs.put("totalLiability", 3_500_000);
            bs.put("totalEquity", 5_000_000);
            ObjectNode ratios = year.putObject("ratios");
            ObjectNode liq = ratios.putObject("liquidityRatios");
            liq.put("interestCoverage", 1.6);
            ObjectNode sol = ratios.putObject("solvencyRatios");
            sol.put("debtEquity", 1.5);

            result.put("excelReportLink", "");
            result.put("pdfDownloadLink", "");
            result.putArray("itrFilled");

            ObjectNode root = objectMapper.createObjectNode();
            root.put("requestId", requestId);
            root.put("statusCode", STATUS_SUCCESS);
            root.set("result", result);
            String raw = objectMapper.writeValueAsString(root);
            return new KarzaItrHttpResult(
                    true, STATUS_SUCCESS, 200, requestId, root, result, raw, null, true);
        } catch (Exception e) {
            return new KarzaItrHttpResult(
                    false, 0, 0, requestId, null, null, null, "Simulation failed: " + e.getMessage(), true);
        }
    }

    private static int normalizedStatusCode(JsonNode root) {
        if (root == null) {
            return 0;
        }
        JsonNode camel = root.get("statusCode");
        if (camel != null && camel.isNumber()) {
            return camel.asInt();
        }
        JsonNode kebab = root.get("status-code");
        if (kebab != null && kebab.isNumber()) {
            return kebab.asInt();
        }
        return 0;
    }

    private static String textOrNull(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText(null);
    }

    private static String blankToDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }
}
