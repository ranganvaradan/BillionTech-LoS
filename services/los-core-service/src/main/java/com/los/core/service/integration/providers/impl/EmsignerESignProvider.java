package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.los.core.config.EmsignerProperties;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.entity.schema.los2.AggregatorProviderConfig;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.schema.los2.AggregatorProviderConfigRepository;
import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * EMSIGNER provider: embedded signing (InitiateEmbeddedSigning / ListTemplates / DownloadWorkflowDocuments)
 * using {@code aggregator_configs} for URLs and credentials, with optional legacy encrypted gateway flow
 * when only {@code los.integration.emsigner.auth-token} is configured.
 */
@Slf4j
@Component("emsignerESignProvider")
@RequiredArgsConstructor
public class EmsignerESignProvider implements IESignProvider {

    private final IntegrationProperties integrationProperties;
    private final EmsignerProperties props;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;
    private final AggregatorProviderConfigRepository aggregatorProviderConfigRepository; // retained for compatibility, no runtime fallback
    private final KfsDocumentRepository kfsDocumentRepository;
    private final LoanApplicationRepository applicationRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;

    @Override
    public String getProviderName() {
        return "EMSIGNER";
    }

    @Override
    public ESignInitResult initiateSigningRequest(IESignProvider.ESignInitRequest request) {
        if (request == null) {
            return new ESignInitResult(false, null, null, "ESign init request is required");
        }
        UUID applicationId = request.applicationId();
        String documentStorageKey = request.documentStorageKey();
        Map<String, Object> signerInfo = request.signerInfo() != null ? request.signerInfo() : Map.of();
        String returnUrl = request.returnUrl();

        log.info("[Emsigner] Initiating eSign (embedded-first) applicationId={} docKey={}", applicationId, documentStorageKey);

        Optional<EmbeddedRuntime> embedded = resolveEmbeddedRuntime();
        if (embedded.isPresent()) {
            log.info("[Emsigner] Embedded runtime resolved, triggering InitiateEmbeddedSigning (applicationId={})", applicationId);
            return initiateEmbedded(embedded.get(), applicationId, documentStorageKey, signerInfo, returnUrl);
        }
        log.error("[Emsigner] Embedded runtime config missing; InitiateEmbeddedSigning was skipped");
        return new ESignInitResult(
                false,
                null,
                null,
                "EMSIGNER PROPERTIES NOT LOADED",
                Map.of("simulated", false, "reason", "MISSING_EMSIGNER_CONFIG"));
    }

    private ESignInitResult initiateEmbedded(
            EmbeddedRuntime rt,
            UUID applicationId,
            String documentStorageKey,
            Map<String, Object> signerInfo,
            String returnUrl) {
        Instant requestTime = Instant.now();
        String auditTxn = "ESIGN-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            byte[] pdfBytes = loadPdfBytes(applicationId, documentStorageKey, signerInfo);
            int pageCount = countPdfPages(pdfBytes);
            log.info("[Emsigner] PDF ready: {} bytes, {} page(s)", pdfBytes.length, pageCount);

            String bearer = fetchAccessToken(rt);
            requireAccessToken(bearer);

            String templateId = selectTemplateId(rt, bearer, pageCount);

            Map<String, Object> body = buildInitiatePayload(
                    applicationId, documentStorageKey, signerInfo, pdfBytes, pageCount, templateId);
            String initiateUrl = rt.initiateUrl();
            String requestJson = objectMapper.writeValueAsString(body);
            log.info("[Emsigner] Initiate payload chars={} pageCount={} templateSelected={}",
                    requestJson.length(), pageCount, templateId != null);
            log.info("[Emsigner] Calling InitiateEmbeddedSigning with valid token");
            log.info("[Emsigner] POST InitiateEmbeddedSigning url={} (template={})", initiateUrl, templateId != null);
            log.info("[Emsigner][FINAL PAYLOAD] {}", requestJson);
            log.info("[Emsigner][REQUEST] headers={{Content-Type:application/json, Accept:application/json, Authorization:Basic ****}}");
            log.info("[Emsigner][REQUEST] payload={}", truncate(redactForAudit(requestJson), 1200));

            HttpResponse<String> response = httpPostJson(rt, initiateUrl, requestJson, bearer);
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();
            log.info("[Emsigner] InitiateEmbeddedSigning HTTP {} in {}ms", response.statusCode(), durationMs);
            log.info("[Emsigner][RAW RESPONSE BODY] {}", response.body());
            log.info("[Emsigner][RESPONSE] status={} body={}", response.statusCode(), truncate(response.body(), 1200));

            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_EMBEDDED", redactForAudit(requestJson),
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, auditTxn, requestTime, responseTime, durationMs);

            if (response.statusCode() != 200) {
                return new ESignInitResult(false, null, null,
                        "InitiateEmbeddedSigning HTTP " + response.statusCode(),
                        Map.of("httpStatus", response.statusCode(), "body", response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            logStructuredParseStart(root);

            ParsedInitiateEnvelope parsed = parseEmbeddedInitiateEnvelope(root);
            if (!parsed.logicallySuccessful()) {
                String msg = firstNonBlank(
                        parsed.failureDetail(),
                        "EmSigner initiation rejected");
                log.warn("[Emsigner][FALLBACK_REASON] initiate failed: {}",
                        truncate(msg, 500));
                Map<String, Object> extras = new LinkedHashMap<>(Map.of("raw", safeMap(root)));
                if (parsed.workflowId() != null && !parsed.workflowId().isBlank()) {
                    extras.put("workflowIdExtracted", parsed.workflowId());
                }
                if (parsed.signingUrl() != null && !parsed.signingUrl().isBlank()) {
                    extras.put("signingUrlSnippet", signingUrlSnippetForLog(parsed.signingUrl()));
                }
                log.info("[Emsigner][FALLBACK_CONTEXT] redactedResponse={}",
                        truncate(redactEmbeddedResponseForLog(root), 1500));
                return new ESignInitResult(false, parsed.workflowId(), parsed.signingUrl(), msg, extras);
            }

            String workflowId = Objects.requireNonNull(parsed.workflowId());
            String signingUrl = Objects.requireNonNull(parsed.signingUrl());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("initiateResponse", objectMapper.convertValue(root, Map.class));
            meta.put("templateId", templateId);
            meta.put("pageCount", pageCount);
            log.info("[Emsigner][EXTRACTED] workflowId={} signUrlSnippet={}",
                    workflowId, signingUrlSnippetForLog(signingUrl));
            log.info("[Emsigner][RESPONSE_OK] Embedded session created (signing URL length={})", signingUrl.length());
            return new ESignInitResult(true, workflowId, signingUrl, null, meta);
        } catch (Exception e) {
            log.error("[Emsigner] Embedded initiation failed: {}", e.getMessage(), e);
            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_EMBEDDED", null,
                    null, "ERROR", null, e.getMessage(), auditTxn,
                    requestTime, Instant.now(), null);
            return new ESignInitResult(false, null, null, "Emsigner embedded error: " + e.getMessage(),
                    Map.of("exceptionType", e.getClass().getSimpleName()));
        }
    }

    private String selectTemplateId(EmbeddedRuntime rt, String bearer, int pageCount) {
        String listUrl = rt.listTemplatesUrl();
        final String fallbackTemplateId = "16649";
        try {
            String listBody = objectMapper.writeValueAsString(Map.of("PageCount", pageCount));
            log.info("[Emsigner] ListTemplates POST url={} (match pageCount={})", listUrl, pageCount);
            log.info("[Emsigner] Calling ListTemplates with valid token");
            HttpResponse<String> resp = httpPostJson(rt, listUrl, listBody, bearer);
            log.info("[Emsigner] ListTemplates HTTP {}", resp.statusCode());
            if (resp.statusCode() != 200) {
                log.warn("[Emsigner] ListTemplates non-200 — falling back to non-template payload");
                return fallbackTemplateId;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode arr = root.path("Response");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                log.warn("[Emsigner] ListTemplates returned no array — fallback non-template payload");
                return fallbackTemplateId;
            }
            for (JsonNode n : arr) {
                int pages = pageCountFromTemplateName(textOrNull(n.path("TemplateName")));
                if (pages > 0 && pages == pageCount) {
                    String tid = textOrNull(n.path("TemplateId"));
                    if (tid == null) {
                        tid = textOrNull(n.path("Id"));
                    }
                    if (tid != null && !tid.isBlank()) {
                        log.info("[Emsigner] Selected TemplateId {} for pageCount={}", tid, pageCount);
                        return tid.trim();
                    }
                }
            }
            log.warn("[Emsigner] No template matched pageCount={} — fallback non-template.", pageCount);
        } catch (Exception ex) {
            log.warn("[Emsigner] ListTemplates failed — fallback non-template payload: {}", ex.getMessage());
        }
        return fallbackTemplateId;
    }

    private static int pageCountFromTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return -1;
        }
        for (int i = templateName.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(templateName.charAt(i))) {
                if (i == templateName.length() - 1) {
                    return -1;
                }
                String tail = templateName.substring(i + 1).trim();
                try {
                    return Integer.parseInt(tail);
                } catch (NumberFormatException ignore) {
                    return -1;
                }
            }
        }
        try {
            return Integer.parseInt(templateName.trim());
        } catch (NumberFormatException ignore) {
            return -1;
        }
    }

    private static JsonNode firstArrayNode(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        String[] paths = {"Data", "Templates", "List", "lstTemplates", "Result", "results"};
        for (String p : paths) {
            JsonNode n = root.path(p);
            if (n.isArray()) {
                return n;
            }
        }
        return null;
    }

    private static int templatePageCount(JsonNode n) {
        String[] keys = {"NumberOfPages", "PageCount", "Pages", "NoOfPages"};
        for (String k : keys) {
            if (n.has(k) && n.get(k).isNumber()) {
                return n.get(k).asInt();
            }
        }
        return -1;
    }

    private Map<String, Object> buildInitiatePayload(
            UUID applicationId,
            String documentStorageKey,
            Map<String, Object> signerInfo,
            byte[] pdfBytes,
            int pageCount,
            String templateId) {
        String name = "Rangan Varadan";
        String emailId = "sivaraj@billionloans.com";
        String borrowerEmail = Objects.toString(
                optionalString(signerInfo, "borrowerEmail", "email", "signerEmail", "EmailId"), "").trim();
        if (borrowerEmail.isBlank()) {
            throw new IllegalArgumentException("Invalid signer configuration for Emsigner");
        }
        List<String> signatoryEmails = List.of(borrowerEmail, emailId);
        String fileB64 = Base64.getEncoder().encodeToString(pdfBytes);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Name", name);
        payload.put("EmailId", emailId);
        payload.put("SignatoryEmailIds", signatoryEmails);

        List<Map<String, Object>> signatureSettings = List.of(
                Map.of("Automatedsigningenabled", false),
                Map.of("Automatedsigningenabled", true));
        payload.put("SignatureSettings", signatureSettings);

        if (templateId != null && !templateId.isBlank()) {
            Integer templateIdNum = parseTemplateId(templateId);
            String documentName = resolveDocumentName(signerInfo, documentStorageKey);
            payload.put("ReferenceNo", Objects.toString(
                    signerInfo.getOrDefault("referenceNumber", UUID.randomUUID().toString()), UUID.randomUUID().toString()));
            payload.put("lstDocumentDetails",
                    List.of(Map.of(
                            "TemplateId", templateIdNum != null ? templateIdNum : 19087,
                            "DocumentName", documentName,
                            "FileData", fileB64)));
            log.debug("[Emsigner] Initiate payload (with template pages={} templateId={})", pageCount, templateId);
            return payload;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controlDetails =
                (List<Map<String, Object>>) signerInfo.get("controlDetails");
        if (controlDetails == null) {
            controlDetails = List.of(Map.of());
        }
        payload.put("lstDocumentDetails",
                List.of(Map.of(
                        "FileData", fileB64,
                        "ControlDetails", controlDetails)));
        log.debug("[Emsigner] Initiate payload (no template) pages={}", pageCount);
        return payload;
    }

    private static List<String> toEmailList(Map<String, Object> signerInfo, String primaryEmail) {
        Object raw = signerInfo.get("SignatoryEmailIds");
        if (raw == null) {
            raw = signerInfo.get("signatoryEmailIds");
        }
        if (raw instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object o : c) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        if (primaryEmail != null && !primaryEmail.isBlank()) {
            return List.of(primaryEmail.trim());
        }
        return List.of();
    }

    private static String resolveDocumentName(Map<String, Object> signerInfo, String documentStorageKey) {
        String name = Objects.toString(optionalString(signerInfo, "DocumentName", "documentName", "FileName", "fileName"), "").trim();
        if (!name.isBlank()) {
            return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
        }
        String fallback = documentStorageKey != null ? documentStorageKey.trim() : "";
        if (fallback.isBlank()) {
            fallback = "agreement";
        }
        return fallback.toLowerCase(Locale.ROOT).endsWith(".pdf") ? fallback : fallback + ".pdf";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeSignatureSettings(Object signatureSettingsRaw) {
        if (signatureSettingsRaw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        if (signatureSettingsRaw instanceof Map<?, ?> map && !map.isEmpty()) {
            return List.of((Map<String, Object>) map);
        }
        return List.of(Map.of("Automatedsigningenabled", false));
    }

    private static Integer parseTemplateId(String templateId) {
        try {
            return Integer.parseInt(templateId.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Object optionalString(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return "";
    }

    private byte[] loadPdfBytes(UUID applicationId, String documentKey, Map<String, Object> signerInfo) throws Exception {
        Object fb = signerInfo.get("fileBase64");
        if (fb != null && !String.valueOf(fb).isBlank()) {
            String raw = String.valueOf(fb).trim();
            int comma = raw.indexOf("base64,");
            if (comma > 0) {
                raw = raw.substring(comma + "base64,".length());
            }
            log.info("[Emsigner] Using fileBase64 from signerInfo ({} chars)", raw.length());
            return Base64.getDecoder().decode(raw.replaceAll("\\s", ""));
        }
        if (applicationId == null) {
            throw new IllegalStateException("applicationId is required to load KFS PDF");
        }
        Optional<KfsDocument> kfsOpt = kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (kfsOpt.isPresent()) {
            byte[] pdf = kfsPdfGenerationService.generateKfsPdf(kfsOpt.get());
            log.info("[Emsigner] Generated PDF from KFS entity id={}", kfsOpt.get().getId());
            return pdf;
        }
        LoanApplication app = applicationRepository.findById(applicationId).orElse(null);
        if (app != null && InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
            byte[] pdf = kfsPdfGenerationService.generateInvoiceDiscountingTermsPdfForApplication(applicationId);
            log.info("[Emsigner] Generated invoice discounting terms PDF (legacy/fallback) for applicationId={}",
                    applicationId);
            return pdf;
        }
        throw new IllegalStateException("No KFS document for application " + applicationId
                + " — generate KFS before eSign or pass fileBase64 in signerInfo.");
    }

    private static int countPdfPages(byte[] pdfBytes) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfBytes);
            return reader.getNumberOfPages();
        } catch (Exception e) {
            log.warn("[Emsigner] PDF page count failed, assuming 1: {}", e.getMessage());
            return 1;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private String fetchAccessToken(EmbeddedRuntime rt) throws Exception {
        String tokenUrl = rt.tokenUrl();
        if (tokenUrl == null || tokenUrl.isBlank()) {
            throw new RuntimeException("Failed to fetch EmSigner token: token URL is missing");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("AppName", rt.appName.trim());
        body.put("SecretKey", rt.secretKey.trim());
        String json = objectMapper.writeValueAsString(body);
        log.info("[Emsigner] Calling AuthorizeApp with AppName={}", rt.appName);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofMillis(rt.readTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = httpClient(rt).send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("[Emsigner] Token HTTP {} body={}", resp.statusCode(), truncate(resp.body(), 512));
            throw new RuntimeException("Failed to fetch EmSigner token: HTTP " + resp.statusCode());
        }
        log.info("[Emsigner] Authorize response received");
        log.info("[Emsigner] Authorize response: {}", truncate(resp.body(), 1200));
        JsonNode root = objectMapper.readTree(resp.body());
        log.debug("[Emsigner] Full authorize JSON parsed: {}", root);
        String token = deepText(root, List.of(
                List.of("Response", "AuthToken"),
                List.of("AccessToken"),
                List.of("access_token"),
                List.of("Token"),
                List.of("Data", "AccessToken"),
                List.of("Data", "Token"),
                List.of("data", "access_token")));
        if (token == null || token.isBlank()) {
            log.error("[Emsigner] Token extraction failed. Available JSON: {}", root);
            throw new RuntimeException("Failed to fetch EmSigner token");
        }
        log.info("[Emsigner] Token extracted successfully");
        return token.trim();
    }

    private static String deepText(JsonNode root, List<List<String>> paths) {
        for (List<String> p : paths) {
            JsonNode n = root;
            for (String seg : p) {
                if (n == null) break;
                n = n.path(seg);
            }
            if (n != null && !n.isMissingNode()) {
                String t = textOrNull(n);
                if (t != null && !t.isBlank()) return t.trim();
            }
        }
        return null;
    }

    private HttpResponse<String> httpPostJson(EmbeddedRuntime rt, String url, String json, String bearer)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(rt.readTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        requireAccessToken(bearer);
        addAuthHeaders(b, bearer);
        b.POST(HttpRequest.BodyPublishers.ofString(json));
        return httpClient(rt).send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpGet(EmbeddedRuntime rt, String url, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(rt.readTimeoutMs()))
                .header("Accept", "application/json");
        requireAccessToken(bearer);
        addAuthHeaders(b, bearer);
        return httpClient(rt).send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void addAuthHeaders(HttpRequest.Builder b, String bearer) {
        String token = bearer.trim();
        b.header("Authorization", "Basic " + token);
        log.info("[Emsigner] Using Authorization: Basic token");
    }

    private static void requireAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("EmSigner token missing");
        }
    }

    private HttpClient httpClient(EmbeddedRuntime rt) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(rt.connectTimeoutMs()))
                .build();
    }

    private Optional<EmbeddedRuntime> resolveEmbeddedRuntime() {
        return resolveEmbeddedRuntimeFromProperties();
    }

    private Optional<EmbeddedRuntime> resolveEmbeddedRuntimeFromProperties() {
        if (!props.isEnabled()) {
            throw new RuntimeException("EMSIGNER PROPERTIES NOT LOADED: los.esign.emsigner.enabled=false");
        }

        List<String> missing = new ArrayList<>();
        if (!hasText(props.getBaseUrl())) missing.add("baseUrl");
        if (!hasText(props.getAppName())) missing.add("appName");
        if (!hasText(props.getSecretKey())) missing.add("secretKey");
        if (!hasText(props.getInitiateApiUrl())) missing.add("initiateApiUrl");
        if (!missing.isEmpty()) {
            throw new RuntimeException("EMSIGNER PROPERTIES NOT LOADED: missing " + missing);
        }

        String base = trimSlash(props.getBaseUrl());
        String initAbs = absoluteUrl(base, props.getInitiateApiUrl());
        String listAbs = absoluteUrl(base,
                hasText(props.getListTemplatesApiUrl()) ? props.getListTemplatesApiUrl() : "/api/ListTemplates");
        String downloadCfg = hasText(props.getDownloadApiUrl()) ? props.getDownloadApiUrl() : "/api/DownloadWorkflowDocuments";
        String dlSeg = trimSlash(downloadCfg).replaceFirst("^/+", "");
        String tokenCfg = hasText(props.getAccessTokenUrl()) ? props.getAccessTokenUrl() : "/api/AuthorizeApp";
        IntegrationProperties.EmsignerProperties y = integrationProperties.getEmsigner();

        log.info("[Emsigner][CONFIG CHECK] baseUrl={}, appName={}, initiate={}",
                base, props.getAppName(), props.getInitiateApiUrl());
        log.info("[Emsigner] Using properties config");
        log.info("[Emsigner][CONFIG SOURCE] PROPERTIES");
        log.info("[Emsigner][CONFIG] baseUrl={} initiateUrl={} listTemplatesUrl={} downloadUrl={} appName={} secretKeyPresent={} tokenPresent={}",
                base, initAbs, listAbs, absoluteUrl(base, downloadCfg), props.getAppName(), true, true);

        return Optional.of(new EmbeddedRuntime(
                base,
                initAbs,
                listAbs,
                dlSeg,
                base,
                absoluteUrl(base, tokenCfg),
                props.getAppName().trim(),
                props.getSecretKey().trim(),
                y.getConnectTimeoutMs(),
                y.getReadTimeoutMs()
        ));
    }

    private static String decryptPlain(String s) {
        // Column names end with _enc; values may still be plaintext in dev — no vault wiring here.
        return s != null ? s.trim() : null;
    }

    private static String strExtra(Map<String, Object> extra, String key, String def) {
        Object v = extra.get(key);
        if (v == null || v.toString().isBlank()) return def;
        return v.toString().trim();
    }

    private static String firstExtra(Map<String, Object> extra, String... keysAndFallback) {
        if (extra != null) {
            for (int i = 0; i < keysAndFallback.length; i++) {
                String k = keysAndFallback[i];
                // last parameter may be a fallback literal rather than key; if so it will still be checked below
                Object v = extra.get(k);
                if (v != null && !v.toString().isBlank()) {
                    return v.toString().trim();
                }
            }
        }
        // last arg is treated as fallback if present
        return keysAndFallback.length > 0 ? keysAndFallback[keysAndFallback.length - 1] : "";
    }

    private static AggregatorProviderConfig pickEsignConfig(List<AggregatorProviderConfig> rows) {
        return rows.stream()
                .min(Comparator.comparingInt(EmsignerESignProvider::stepRank))
                .orElseGet(() -> rows.get(0));
    }

    private static int stepRank(AggregatorProviderConfig c) {
        String s = c.getStepType();
        if (s == null || s.isBlank()) {
            return 100;
        }
        if ("ESIGN_AGREEMENT".equalsIgnoreCase(s)) {
            return 0;
        }
        if ("ESIGN".equalsIgnoreCase(s)) {
            return 1;
        }
        if ("ESIGN_KFS".equalsIgnoreCase(s)) {
            return 2;
        }
        return 50;
    }

    private static String absoluteUrl(String base, String pathOrAbsolute) {
        if (pathOrAbsolute == null || pathOrAbsolute.isBlank()) {
            return trimSlash(base);
        }
        String p = pathOrAbsolute.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        return trimSlash(base) + "/" + trimSlash(p).replaceFirst("^/+", "");
    }

    private static String trimSlash(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private String downloadUrlForWorkflow(EmbeddedRuntime rt, String workflowId) {
        String seg = rt.downloadPathSegment();
        if (seg == null || seg.isBlank()) {
            seg = "api/DownloadWorkflowDocuments";
        }
        String path = trimSlash(seg).replaceFirst("^/+", "");
        if (path.startsWith("http://") || path.startsWith("https://")) {
            boolean hasQuery = path.contains("WorkFlowId=") || path.contains("WorkflowID=");
            String withId = path.replace("{WorkflowID}", workflowId).replace("{WorkFlowId}", workflowId);
            return hasQuery ? withId : withId + (path.contains("?") ? "&" : "?") + "WorkFlowId=" + enc(workflowId);
        }
        String base = trimSlash(rt.apiBase());
        return base + "/" + path + "?WorkFlowId=" + enc(workflowId);
    }

    private static String enc(String wf) {
        return URLEncoder.encode(wf, StandardCharsets.UTF_8);
    }

    /**
     * @param downloadPathSegment path under {@code apiBase}, e.g. {@code api/DownloadWorkflowDocuments}
     */
    private record EmbeddedRuntime(
            String apiBase,
            String initiateUrl,
            String listTemplatesUrl,
            String downloadPathSegment,
            String signingUiBaseUrl,
            String tokenUrl,
            String appName,
            String secretKey,
            int connectTimeoutMs,
            int readTimeoutMs) {
    }

    private record ParsedInitiateEnvelope(boolean logicallySuccessful, String workflowId, String signingUrl, String failureDetail) {}

    private void logStructuredParseStart(JsonNode root) {
        Boolean isSuccess = root.has("IsSuccess") ? root.path("IsSuccess").asBoolean() : null;
        int errCode = root.path("ErrorCode").isNumber() ? root.path("ErrorCode").asInt() : 0;
        log.info("[Emsigner][PARSE] envelope IsSuccess={} ErrorCode={}", isSuccess, errCode);
    }

    private Map<String, Object> safeMap(JsonNode root) {
        try {
            return objectMapper.convertValue(root, Map.class);
        } catch (Exception ex) {
            return Map.of("parseError", ex.getMessage());
        }
    }

    private static ParsedInitiateEnvelope parseEmbeddedInitiateEnvelope(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return new ParsedInitiateEnvelope(false, null, null, "Empty provider JSON");
        }
        if (envelopeIndicatesHardFailure(root)) {
            return new ParsedInitiateEnvelope(false, null, null,
                    firstNonBlank(summarizeMessages(root), root.path("ErrorCode").asText("")));
        }
        JsonNode respBlock = root.path("Response");
        if (respBlock.isObject() && respBlock.has("Status") && respBlock.get("Status").isBoolean()
                && !respBlock.get("Status").asBoolean()) {
            return new ParsedInitiateEnvelope(false, null, null,
                    firstNonBlank(summarizeMessages(root), "EmSigner Response.Status=false"));
        }

        List<JsonNode> layers = responseObjectLayers(root);
        String workflowId = firstWorkflowIdFromLayers(layers, root);
        String signingUrl = firstSigningUrlFromLayers(layers, root);

        boolean success = workflowId != null && !workflowId.isBlank()
                && signingUrl != null && !signingUrl.isBlank();
        if (success) {
            return new ParsedInitiateEnvelope(true, workflowId.trim(), signingUrl.trim(), null);
        }
        String detail = firstNonBlank(
                workflowId == null || workflowId.isBlank() ? "missing workflow id" : null,
                signingUrl == null || signingUrl.isBlank() ? "missing signing url" : null);
        return new ParsedInitiateEnvelope(false, workflowId, signingUrl, detail);
    }

    private static boolean envelopeIndicatesHardFailure(JsonNode root) {
        if (root.has("IsSuccess") && !root.path("IsSuccess").asBoolean(true)) {
            return true;
        }
        JsonNode err = root.get("ErrorCode");
        if (err != null && err.isNumber() && err.asInt() != 0) {
            return true;
        }
        return false;
    }

    private static String summarizeMessages(JsonNode root) {
        JsonNode msgs = root.path("Messages");
        if (msgs.isArray() && msgs.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode m : msgs) {
                String t = textOrNull(m);
                if (t == null) continue;
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(t);
            }
            String s = sb.toString();
            return s.isEmpty() ? null : s;
        }
        return textOrNull(root.path("Message"));
    }

    private static List<JsonNode> responseObjectLayers(JsonNode root) {
        LinkedHashSet<JsonNode> seen = new LinkedHashSet<>();
        List<JsonNode> out = new ArrayList<>();
        if (root != null && root.isObject()) {
            out.add(root);
            seen.add(root);
        }
        for (String wrap : new String[] {
                "Response", "response",
                "Data", "data",
                "Result", "result",
                "Payload", "payload"}) {
            JsonNode n = root.get(wrap);
            if (n != null && n.isObject() && seen.add(n)) {
                out.add(n);
            }
        }
        return out;
    }

    private static String firstWorkflowIdFromLayers(List<JsonNode> layers, JsonNode fullRoot) {
        for (JsonNode layer : layers) {
            String id = fieldAsTextFlexible(layer,
                    "WorkflowID", "WorkflowId", "workflowId", "WorkFlowID", "WorkFlowId", "workFlowId", "workflow_id");
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return deepText(fullRoot, List.of(
                List.of("Response", "WorkflowID"),
                List.of("Response", "WorkflowId"),
                List.of("response", "WorkflowId"),
                List.of("Data", "WorkflowID"),
                List.of("Data", "WorkflowId")));
    }

    private static String firstSigningUrlFromLayers(List<JsonNode> layers, JsonNode fullRoot) {
        for (JsonNode layer : layers) {
            String u = fieldAsTextFlexible(layer,
                    "SigningURL", "SigningUrl", "signingURL", "signingUrl",
                    "URL", "Url", "url",
                    "SignUrl", "signUrl",
                    "SigningLink", "signingLink",
                    "EmbeddedUrl", "embeddedUrl");
            if (u != null && !u.isBlank()) {
                return u;
            }
        }
        return deepText(fullRoot, List.of(
                List.of("Response", "SigningURL"),
                List.of("Response", "SigningUrl"),
                List.of("Response", "URL"),
                List.of("response", "URL"),
                List.of("Data", "SigningURL"),
                List.of("Data", "URL"),
                List.of("Result", "SigningURL")));
    }

    private static String fieldAsTextFlexible(JsonNode parent, String... names) {
        if (parent == null || parent.isMissingNode() || !parent.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode n = parent.get(name);
            if (n == null || n.isMissingNode() || n.isNull()) {
                continue;
            }
            if (n.isNumber()) {
                if (n.canConvertToExactIntegral()) {
                    return String.valueOf(n.longValue());
                }
                return n.asText();
            }
            String t = textOrNull(n);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /**
     * Removes base64 document blobs and secrets from JSON logs.
     */
    private String redactEmbeddedResponseForLog(JsonNode root) {
        try {
            String raw = objectMapper.writeValueAsString(root);
            String redacted = redactForAudit(raw)
                    .replaceAll("(?i)(\"SecretKey\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"")
                    .replaceAll("(?i)(\"AuthToken\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"")
                    .replaceAll("(?i)(\"Token\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
            return redacted;
        } catch (Exception e) {
            return "(unserializable-json)";
        }
    }

    private static String signingUrlSnippetForLog(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() <= 96) {
            return url;
        }
        return url.substring(0, 64) + "…(len=" + url.length() + ")";
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        String t = n.asText();
        return t != null && !t.isBlank() ? t : null;
    }

    private String buildEmbeddedSigningUrl(String uiBase, String workflowId, String returnUrl) {
        if (uiBase == null || uiBase.isBlank()) {
            throw new IllegalStateException("Embedded signing UI base URL missing for EMSIGNER");
        }
        if (uiBase.toLowerCase(Locale.ROOT).contains("esign.example.com")) {
            log.error("[Emsigner] Dummy signing host configured ({}). This is not a real emSigner URL.", uiBase);
        }
        String base = uiBase;
        String u = trimSlash(base) + "?WorkflowID=" + enc(workflowId);
        if (returnUrl != null && !returnUrl.isBlank()) {
            u += "&ReturnUrl=" + enc(returnUrl);
        }
        return u;
    }

    @Override
    public String getSigningUrl(String signingRequestId, String returnUrl) {
        Optional<EmbeddedRuntime> rt = resolveEmbeddedRuntime();
        String uiBase = rt.map(EmbeddedRuntime::signingUiBaseUrl).orElse(null);
        if (uiBase == null || uiBase.isBlank()) {
            uiBase = integrationProperties.getEmsigner().getEmbeddedSigningBaseUrl();
        }
        if (uiBase == null || uiBase.isBlank()) {
            uiBase = integrationProperties.getEmsigner().getUrl();
            uiBase = trimSlash(uiBase).replace("/v3/gateway", "");
        }
        return buildEmbeddedSigningUrl(uiBase, signingRequestId, returnUrl);
    }

    @Override
    public ESignStatusResult getSigningStatus(String eSignTransactionId) {
        log.info("[Emsigner] getSigningStatus {}", eSignTransactionId);
        Optional<EmbeddedRuntime> rt = resolveEmbeddedRuntime();
        IntegrationProperties.EmsignerProperties yaml = integrationProperties.getEmsigner();
        if (rt.isEmpty()) {
            if (yaml.getAuthToken() == null || yaml.getAuthToken().isBlank()) {
                return new ESignStatusResult("COMPLETED", eSignTransactionId,
                        Map.of("simulated", true, "provider", getProviderName()), null);
            }
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(yaml.getConnectTimeoutMs()))
                        .build();
                String statusUrl = trimSlash(yaml.getUrl()).replace("/gateway", "") + "/status/" + eSignTransactionId;
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(statusUrl))
                        .timeout(Duration.ofMillis(yaml.getReadTimeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("AuthToken", yaml.getAuthToken())
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return new ESignStatusResult("UNKNOWN", eSignTransactionId, Map.of(),
                            "Status HTTP " + response.statusCode());
                }
                JsonNode respNode = objectMapper.readTree(response.body());
                String status = respNode.path("status").asText("UNKNOWN");
                Map<String, Object> signerDetails = new LinkedHashMap<>();
                signerDetails.put("legacyGateway", true);
                return new ESignStatusResult(status, eSignTransactionId, signerDetails, null);
            } catch (Exception e) {
                log.error("[Emsigner] getSigningStatus (legacy) error: {}", e.getMessage());
                return new ESignStatusResult("ERROR", eSignTransactionId, Map.of(),
                        e.getMessage());
            }
        }
        try {
            String statusUrl = trimSlash(rt.get().apiBase()) + "/api/WorkflowStatus?WorkFlowId="
                    + enc(eSignTransactionId);
            EmbeddedRuntime embedded = rt.get();
            String bearer = fetchAccessToken(embedded);
            log.info("[Emsigner] WorkflowStatus GET {}", statusUrl);
            HttpResponse<String> response = httpGet(embedded, statusUrl, bearer);
            if (response.statusCode() != 200) {
                return new ESignStatusResult("UNKNOWN", eSignTransactionId, Map.of(),
                        "WorkflowStatus HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String st = textOrNull(root.path("WorkflowStatus"));
            if (st == null) {
                st = textOrNull(root.path("Status"));
            }
            return new ESignStatusResult(st != null ? st : "UNKNOWN", eSignTransactionId,
                    objectMapper.convertValue(root, Map.class), null);
        } catch (Exception e) {
            log.error("[Emsigner] getSigningStatus error: {}", e.getMessage());
            return new ESignStatusResult("ERROR", eSignTransactionId, Map.of(),
                    e.getMessage());
        }
    }

    @Override
    public void cancelSigningRequest(String signingRequestId, String reason) {
        log.info("[Emsigner] cancelSigningRequest id={} reason={} (no remote cancel)", signingRequestId, reason);
    }

    @Override
    public byte[] downloadSignedDocument(String eSignTransactionId) {
        log.info("[Emsigner] DownloadWorkflowDocuments WorkFlowId={}", eSignTransactionId);
        Optional<EmbeddedRuntime> rt = resolveEmbeddedRuntime();
        if (rt.isPresent()) {
            try {
                String bearer = fetchAccessToken(rt.get());
                String url = downloadUrlForWorkflow(rt.get(), eSignTransactionId);
                log.info("[Emsigner] GET {}", url);
                HttpResponse<String> response = httpGet(rt.get(), url, bearer);
                log.info("[Emsigner] Download HTTP {}", response.statusCode());
                if (response.statusCode() != 200) {
                    log.error("[Emsigner] Download failed HTTP {} body={}", response.statusCode(),
                            truncate(response.body(), 400));
                    return null;
                }
                String b64 = extractBase64FileData(response.body());
                if (b64 == null || b64.isBlank()) {
                    log.error("[Emsigner] Base64FileData missing in download response");
                    return null;
                }
                return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
            } catch (Exception e) {
                log.error("[Emsigner] Download error: {}", e.getMessage(), e);
                return null;
            }
        }

        IntegrationProperties.EmsignerProperties yaml = integrationProperties.getEmsigner();
        if (yaml.getAuthToken() == null || yaml.getAuthToken().isBlank()) {
            return ("Signed document placeholder for " + eSignTransactionId).getBytes(StandardCharsets.UTF_8);
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(yaml.getConnectTimeoutMs()))
                    .build();
            String downloadUrl = yaml.getUrl().replace("/gateway", "/download") + "/" + eSignTransactionId;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMillis(yaml.getReadTimeoutMs()))
                    .header("AuthToken", yaml.getAuthToken())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.error("[Emsigner] Legacy download failed HTTP {}", response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("[Emsigner] Legacy download error: {}", e.getMessage());
            return null;
        }
    }

    private String extractBase64FileData(String jsonBody) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        String b64 = deepText(root, List.of(
                List.of("Base64FileData"),
                List.of("base64FileData"),
                List.of("Data", "Base64FileData"),
                List.of("Document", "Base64FileData")));
        if (b64 != null && !b64.isBlank()) {
            return b64;
        }
        JsonNode arr = firstArrayNode(root);
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String t = textOrNull(n.path("Base64FileData"));
                if (t != null) return t;
            }
        }
        return null;
    }

    /**
     * Legacy encrypted gateway (yaml auth token only) — preserved for older deployments.
     */
    private ESignInitResult legacyEncryptedGatewayInit(IESignProvider.ESignInitRequest request,
                                                         IntegrationProperties.EmsignerProperties config) {
        UUID applicationId = request.applicationId();
        String documentStorageKey = request.documentStorageKey();
        Map<String, Object> signerInfo = request.signerInfo() != null ? request.signerInfo() : Map.of();
        String transactionId = "ESIGN-" + UUID.randomUUID().toString().substring(0, 8);
        Instant requestTime = Instant.now();

        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            SecretKey sessionKey = keyGen.generateKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();
            String sessionKeyBase64 = Base64.getEncoder().encodeToString(sessionKeyBytes);

            String signerName = (String) signerInfo.getOrDefault("name", "");
            String referenceNo = (String) signerInfo.getOrDefault("referenceNumber", applicationId.toString());
            String signingLogId = String.valueOf(System.currentTimeMillis());

            Map<String, Object> jsonPayload = new LinkedHashMap<>();
            jsonPayload.put("Name", signerName);
            jsonPayload.put("FileType", "PDF");
            jsonPayload.put("SignatureType", signerInfo.getOrDefault("signatureType", 0));
            jsonPayload.put("SelectPage", "FIRST");
            jsonPayload.put("SignaturePosition", signerInfo.getOrDefault("signaturePosition", "Bottom-Left"));
            jsonPayload.put("SignatureMode", signerInfo.getOrDefault("signatureMode", "1"));
            jsonPayload.put("AuthToken", config.getAuthToken());
            jsonPayload.put("File", signerInfo.getOrDefault("fileBase64", ""));
            jsonPayload.put("PreviewRequired", true);
            jsonPayload.put("SUrl", config.getSuccessCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("FUrl", config.getFailureCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("CUrl", config.getCancelCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("ReferenceNumber", referenceNo);
            jsonPayload.put("Enableuploadsignature", false);
            jsonPayload.put("Enablefontsignature", false);
            jsonPayload.put("EnableDrawSignature", false);
            jsonPayload.put("EnableeSignaturePad", false);
            jsonPayload.put("IsCompressed", false);
            jsonPayload.put("IsCosign", signerInfo.getOrDefault("isCosign", false));
            jsonPayload.put("EnableViewDocumentLink", false);
            jsonPayload.put("Storetodb", true);
            jsonPayload.put("IsGSTN", false);
            jsonPayload.put("IsGSTN3B", false);

            String jsonString = objectMapper.writeValueAsString(jsonPayload);
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKeyBytes, "AES"));
            byte[] encryptedJson = cipher.doFinal(jsonBytes);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(jsonBytes);

            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKeyBytes, "AES"));
            byte[] encryptedHash = cipher.doFinal(hashBytes);

            String encryptedJsonB64 = Base64.getEncoder().encodeToString(encryptedJson);
            String encryptedHashB64 = Base64.getEncoder().encodeToString(encryptedHash);
            String sessionKeyEncryptedB64 = sessionKeyBase64;

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("SymmetricKey", sessionKeyEncryptedB64);
            requestBody.put("JsonData", encryptedJsonB64);
            requestBody.put("Hash", encryptedHashB64);
            requestBody.put("ReferenceNumber", referenceNo);
            requestBody.put("SigningLogId", signingLogId);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Emsigner] Legacy gateway HTTP {} in {}ms", response.statusCode(), durationMs);

            String maskedPayload = jsonString.replaceAll("\"AuthToken\":\"[^\"]*\"", "\"AuthToken\":\"***\"");
            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_SIGN", maskedPayload,
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                JsonNode respNode = objectMapper.readTree(response.body());
                String signingUrl = respNode.path("signingUrl").asText(
                        respNode.path("SigningUrl").asText(
                                respNode.path("url").asText("")));
                if (signingUrl.isEmpty()) {
                    signingUrl = config.getUrl() + "?signingLogId=" + signingLogId;
                }
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("legacyGateway", true);
                meta.put("signingLogId", signingLogId);
                return new ESignInitResult(true, transactionId, signingUrl, null, meta);
            }
            return new ESignInitResult(false, transactionId, null,
                    "Emsigner API returned HTTP " + response.statusCode(),
                    Map.of("httpStatus", response.statusCode(), "legacyGateway", true));
        } catch (Exception e) {
            log.error("[Emsigner] Legacy initiation failed: {}", e.getMessage(), e);
            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_SIGN", null,
                    null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new ESignInitResult(false, transactionId, null,
                    "Emsigner error: " + e.getMessage(), Map.of("legacyGateway", true));
        }
    }

    private static String redactForAudit(String requestJson) {
        if (requestJson == null) return null;
        return requestJson.replaceAll("\"Password\"\\s*:\\s*\"[^\"]*\"", "\"Password\":\"***\"")
                .replaceAll("\"FileData\"\\s*:\\s*\"[^\"]{120,}\"",
                        "\"FileData\":\"***base64 omitted***\"");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static String maskUser(String value) {
        if (value == null || value.isBlank()) return "";
        String v = value.trim();
        if (v.contains("@")) {
            int at = v.indexOf('@');
            if (at <= 2) return "***" + v.substring(at);
            return v.substring(0, 2) + "***" + v.substring(at);
        }
        if (v.length() <= 3) return "***";
        return v.substring(0, 2) + "***";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
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
            log.error("[Emsigner] Failed to save audit log: {}", e.getMessage());
        }
    }
}
