package com.los.core.service.integration.gstanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.core.config.IntegrationProperties;
import com.los.core.service.audit.IntegrationApiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Karza GST docs-upload-advance client.
 * Upload: POST multipart (gstin, file…, extendedPeriod, consent).
 * Report: PATCH JSON {{ "requestId", "proceed": true }}.
 * Uses GST Karza product key — never identity KARZA_API_KEY.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KarzaGstDocsUploadClient {

    public static final int STATUS_SUCCESS = 101;

    public record GstFilePart(String fileName, String contentType, byte[] bytes) {
    }

    public record KarzaGstHttpResult(
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

    private final IntegrationProperties integrationProperties;
    private final IntegrationApiAuditService integrationApiAuditService;
    private final ObjectMapper objectMapper;

    public KarzaGstHttpResult upload(
            String gstin,
            List<GstFilePart> files,
            UUID applicationId) {
        IntegrationProperties.KarzaProperties config = integrationProperties.getKarza();
        String apiKey = config.getGstKarzaKey() == null ? "" : config.getGstKarzaKey().trim();
        String url = blankToDefault(
                config.getGstDocsUploadUrl(),
                "https://api.karza.in/gst/uat/v2/docs-upload-advance");
        Instant requestTime = Instant.now();
        String auditRequest = auditUploadRequestJson(gstin, files);

        if (apiKey.isEmpty()) {
            KarzaGstHttpResult simulated = simulateUploadSuccess(gstin);
            recordAudit("KARZA_GST_DOCS_UPLOAD", auditRequest, simulated, applicationId, requestTime);
            return simulated;
        }

        try {
            String boundary = "----LosGstBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipartBody(boundary, gstin, files);
            int connectMs = Math.max(config.getConnectTimeoutMs(), 5000);
            int readMs = readTimeoutMs(config);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(readMs))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("x-karza-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            log.info("[Karza GST analysis] POST upload {} applicationId={} fileCount={} (GST Karza key)",
                    url, applicationId, files == null ? 0 : files.size());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parseAndAudit("KARZA_GST_DOCS_UPLOAD", auditRequest, response, applicationId, requestTime, "upload");
        } catch (Exception e) {
            return errorResult("KARZA_GST_DOCS_UPLOAD", auditRequest, applicationId, requestTime, e, "GST upload");
        }
    }

    public KarzaGstHttpResult generateReport(String requestId, UUID applicationId) {
        IntegrationProperties.KarzaProperties config = integrationProperties.getKarza();
        String apiKey = config.getGstKarzaKey() == null ? "" : config.getGstKarzaKey().trim();
        String url = blankToDefault(
                config.getGstDocsUploadUrl(),
                "https://api.karza.in/gst/uat/v2/docs-upload-advance");
        Instant requestTime = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId == null ? "" : requestId.trim());
        body.put("proceed", true);
        String auditRequest;
        try {
            auditRequest = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            auditRequest = "{\"requestId\":\"" + requestId + "\",\"proceed\":true}";
        }

        if (apiKey.isEmpty()) {
            KarzaGstHttpResult simulated = simulateReportSuccess(requestId);
            recordAudit("KARZA_GST_DOCS_REPORT", auditRequest, simulated, applicationId, requestTime);
            return simulated;
        }

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            int connectMs = Math.max(config.getConnectTimeoutMs(), 5000);
            int readMs = readTimeoutMs(config);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(readMs))
                    .header("Content-Type", "application/json")
                    .header("x-karza-key", apiKey)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.info("[Karza GST analysis] PATCH report {} applicationId={} requestId={}",
                    url, applicationId, requestId);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parseAndAudit("KARZA_GST_DOCS_REPORT", auditRequest, response, applicationId, requestTime, "report");
        } catch (Exception e) {
            return errorResult("KARZA_GST_DOCS_REPORT", auditRequest, applicationId, requestTime, e, "GST report");
        }
    }

    private KarzaGstHttpResult parseAndAudit(
            String operation,
            String auditRequest,
            HttpResponse<String> response,
            UUID applicationId,
            Instant requestTime,
            String label) throws Exception {
        Instant responseTime = Instant.now();
        long durationMs = Duration.between(requestTime, responseTime).toMillis();
        String responseBody = response.body() == null ? "" : response.body();
        JsonNode root = objectMapper.readTree(responseBody.isBlank() ? "{}" : responseBody);
        int karzaStatus = normalizedStatusCode(root);
        String requestId = textOrNull(root, "requestId");
        if (requestId == null && root.path("result").has("requestId")) {
            requestId = textOrNull(root.path("result"), "requestId");
        }
        JsonNode resultNode = root.get("result");
        boolean httpOk = response.statusCode() >= 200 && response.statusCode() < 300;
        boolean success = httpOk && karzaStatus == STATUS_SUCCESS;

        integrationApiAuditService.record(
                "KARZA",
                operation,
                auditRequest,
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
            return new KarzaGstHttpResult(
                    false, karzaStatus, response.statusCode(), requestId, root, resultNode, responseBody,
                    "GST " + label + " provider returned HTTP " + response.statusCode(), false);
        }
        if (!success) {
            return new KarzaGstHttpResult(
                    false, karzaStatus, response.statusCode(), requestId, root, resultNode, responseBody,
                    "GST " + label + " failed (statusCode " + karzaStatus + ")", false);
        }
        return new KarzaGstHttpResult(
                true, karzaStatus, response.statusCode(), requestId, root, resultNode, responseBody, null, false);
    }

    private KarzaGstHttpResult errorResult(
            String operation,
            String auditRequest,
            UUID applicationId,
            Instant requestTime,
            Exception e,
            String label) {
        Instant responseTime = Instant.now();
        log.error("[Karza GST analysis] {} failed for applicationId={}: {}", label, applicationId, e.getMessage());
        integrationApiAuditService.record(
                "KARZA",
                operation,
                auditRequest,
                null,
                "ERROR",
                null,
                e.getMessage(),
                null,
                applicationId,
                requestTime,
                responseTime,
                Duration.between(requestTime, responseTime).toMillis());
        return new KarzaGstHttpResult(
                false, 0, 0, null, null, null, null, label + " provider error: " + e.getMessage(), false);
    }

    private void recordAudit(
            String operation,
            String auditRequest,
            KarzaGstHttpResult result,
            UUID applicationId,
            Instant requestTime) {
        Instant responseTime = Instant.now();
        integrationApiAuditService.record(
                "KARZA",
                operation,
                auditRequest,
                result.rawResponse(),
                result.success() ? "SUCCESS" : "FAILED",
                result.httpStatusCode() > 0 ? result.httpStatusCode() : 200,
                result.errorMessage(),
                result.requestId(),
                applicationId,
                requestTime,
                responseTime,
                Duration.between(requestTime, responseTime).toMillis());
    }

    private int readTimeoutMs(IntegrationProperties.KarzaProperties config) {
        return config.getGstDocsReadTimeoutMs() > 0
                ? config.getGstDocsReadTimeoutMs()
                : 300_000;
    }

    private String auditUploadRequestJson(String gstin, List<GstFilePart> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gstin", gstin == null ? "" : gstin.trim());
        m.put("extendedPeriod", true);
        m.put("consent", "Y");
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (GstFilePart f : files) {
                if (f != null && f.fileName() != null) {
                    names.add(f.fileName());
                }
            }
        }
        m.put("fileCount", names.size());
        m.put("fileNames", names);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"gstin\":\"" + (gstin == null ? "" : gstin) + "\",\"fileCount\":" + names.size() + "}";
        }
    }

    private static byte[] buildMultipartBody(String boundary, String gstin, List<GstFilePart> files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFormField(out, boundary, "gstin", gstin == null ? "" : gstin.trim());
        writeFormField(out, boundary, "extendedPeriod", "true");
        writeFormField(out, boundary, "consent", "Y");
        if (files != null) {
            for (GstFilePart file : files) {
                if (file == null || file.bytes() == null || file.bytes().length == 0) {
                    continue;
                }
                writeFileField(out, boundary, "file",
                        file.fileName() == null || file.fileName().isBlank() ? "document.pdf" : file.fileName(),
                        file.contentType() == null || file.contentType().isBlank()
                                ? "application/pdf"
                                : file.contentType(),
                        file.bytes());
            }
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFileField(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String fileName,
            String contentType,
            byte[] bytes) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + fileName.replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private KarzaGstHttpResult simulateUploadSuccess(String gstin) {
        String requestId = "sim-gst-up-" + UUID.randomUUID();
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ObjectNode alert = result.putObject("alert");
            alert.putArray("doubtfulDocs");
            alert.putArray("missingReturnDocs");
            alert.put("filingstatusCheck", true);
            ObjectNode root = objectMapper.createObjectNode();
            root.put("requestId", requestId);
            root.put("statusCode", STATUS_SUCCESS);
            root.put("statusMessage", "success");
            root.set("result", result);
            String raw = objectMapper.writeValueAsString(root);
            return new KarzaGstHttpResult(true, STATUS_SUCCESS, 200, requestId, root, result, raw, null, true);
        } catch (Exception e) {
            return new KarzaGstHttpResult(
                    false, 0, 0, requestId, null, null, null, "Simulation failed: " + e.getMessage(), true);
        }
    }

    private KarzaGstHttpResult simulateReportSuccess(String requestId) {
        String rid = requestId == null || requestId.isBlank() ? "sim-gst-rep-" + UUID.randomUUID() : requestId;
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("gstin", "29AAHCB0052H2ZV");
            result.put("reportType", "pdfUpload");
            result.put("pdfCount", 1);
            result.put("pdfDownloadLink", "");
            result.put("excelDownloadLink", "");
            result.put("requestId", rid);
            ObjectNode profile = result.putObject("profile");
            profile.put("lgnm", "SIMULATED GST ENTITY");
            profile.put("tradeNam", "SIMULATED GST TRADE");
            profile.put("gstin", "29AAHCB0052H2ZV");
            ObjectNode current = result.putObject("current");
            ObjectNode biz = current.putObject("businessSummary");
            biz.put("gstTurnoverCyInvVal", 5_200_000);
            biz.put("gstTurnoverCyTaxVal", 350_000);
            ObjectNode averages = current.putObject("averages");
            averages.put("avgmonthval", 433_333.33);
            ObjectNode ts = current.putObject("transactionSummary");
            ObjectNode turnover = ts.putObject("turnover");
            turnover.put("ttlVal", 5_200_000);
            ObjectNode filing = current.putArray("filingStatus").addObject();
            filing.put("retPeriod", "062026");
            ObjectNode st = filing.putArray("status").addObject();
            st.put("rtntype", "GSTR1");
            st.put("status", "Filed");
            var mws = current.putArray("monthWiseSummary");
            for (String period : List.of("042026", "052026", "062026")) {
                ObjectNode m = mws.addObject();
                m.put("retPeriod", period);
                ObjectNode g1 = m.putObject("gstr1");
                g1.put("ttlVal", 433_333);
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("requestId", rid);
            root.put("statusCode", STATUS_SUCCESS);
            root.put("statusMessage", "success");
            root.set("result", result);
            String raw = objectMapper.writeValueAsString(root);
            return new KarzaGstHttpResult(true, STATUS_SUCCESS, 200, rid, root, result, raw, null, true);
        } catch (Exception e) {
            return new KarzaGstHttpResult(
                    false, 0, 0, rid, null, null, null, "Simulation failed: " + e.getMessage(), true);
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
