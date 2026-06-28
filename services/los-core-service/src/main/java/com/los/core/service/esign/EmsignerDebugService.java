package com.los.core.service.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.schema.los2.AggregatorProviderConfig;
import com.los.core.repository.schema.los2.AggregatorProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmsignerDebugService {

    private final AggregatorProviderConfigRepository providerConfigRepository;
    private final IntegrationProperties integrationProperties;
    private final ObjectMapper objectMapper;

    @Value("${los.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${los.security.local-dev-permit-all:false}")
    private boolean localDevPermitAll;

    public Map<String, Object> debugConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<AggregatorProviderConfig> rows = providerConfigRepository.findByProviderNameAndActiveTrue("EMSIGNER");
        AggregatorProviderConfig cfg = rows.isEmpty() ? null : pickConfig(rows);
        IntegrationProperties.EmsignerProperties y = integrationProperties.getEmsigner();
        List<String> missing = new java.util.ArrayList<>();

        String baseUrl = "";
        String initiateUrl = "";
        String usernameMasked = "";
        boolean passwordPresent = false;
        Map<String, Object> tokenCheck = Map.of("attempted", false, "accessTokenGenerated", false);
        if (cfg != null) {
            Map<String, Object> extra = cfg.getExtraConfig() != null ? cfg.getExtraConfig() : Map.of();
            String base = trimSlash(cfg.getBaseUrl());
            String initiateCfg = firstExtra(extra,
                    "emSigner_initiateEmbeddedSigning_api_url",
                    "initiateEmbeddedSigningApiUrl",
                    "initiateEmbeddedSigningUrl",
                    "initiateEmbeddedSigningPath",
                    "/api/InitiateEmbeddedSigning");
            String listCfg = firstExtra(extra,
                    "emSigner_listTemplates_api_url",
                    "listTemplatesApiUrl",
                    "listTemplatesUrl",
                    "listTemplatesPath",
                    "/api/ListTemplates");
            String downloadCfg = firstExtra(extra,
                    "emSigner_downloadWorkflowInfo_api_url",
                    "downloadWorkflowDocumentsApiUrl",
                    "downloadWorkflowDocumentsUrl",
                    "downloadWorkflowDocumentsPath",
                    "/api/DownloadWorkflowDocuments");
            baseUrl = base;
            initiateUrl = absoluteUrl(base, initiateCfg);
            usernameMasked = maskUser(safeString(cfg.getClientIdEnc()));
            passwordPresent = hasText(safeString(cfg.getClientSecretEnc()));
            tokenCheck = tokenCheck(cfg, y);
            out.put("listTemplatesUrl", absoluteUrl(base, listCfg));
            out.put("downloadUrl", absoluteUrl(base, downloadCfg));
            out.put("provider", cfg.getProviderName());
            out.put("stepType", cfg.getStepType());
        }

        boolean tokenPresent = hasText(y.getAuthToken()) || Boolean.TRUE.equals(tokenCheck.get("accessTokenGenerated"));
        if (!hasText(baseUrl)) missing.add("base_url");
        if (!hasText(initiateUrl)) missing.add("initiate_api_url");
        if (!hasText(usernameMasked)) missing.add("username");
        if (!passwordPresent) missing.add("password");
        if (!tokenPresent) missing.add("token");

        out.put("baseUrl", baseUrl);
        out.put("initiateUrl", initiateUrl);
        out.put("username", usernameMasked);
        out.put("passwordPresent", passwordPresent);
        out.put("tokenPresent", tokenPresent);
        out.put("isConfigValid", missing.isEmpty());
        out.put("missingFields", missing);
        out.put("accessTokenCheck", tokenCheck);
        out.put("flags", Map.of(
                "demoEnabled", demoEnabled,
                "localDevPermitAll", localDevPermitAll,
                "legacyAuthTokenConfigured", hasText(y.getAuthToken()),
                "mockOrDummyFallbackAllowed", false));
        return out;
    }

    private Map<String, Object> tokenCheck(AggregatorProviderConfig cfg, IntegrationProperties.EmsignerProperties y) {
        Map<String, Object> t = new LinkedHashMap<>();
        String tokenUrl = cfg.getTokenUrl();
        String username = safeString(cfg.getClientIdEnc());
        String password = safeString(cfg.getClientSecretEnc());
        t.put("tokenUrlPresent", hasText(tokenUrl));
        t.put("usernamePresent", hasText(username));
        t.put("passwordPresent", hasText(password));

        if (!hasText(tokenUrl) || !hasText(username) || !hasText(password)) {
            boolean yamlToken = hasText(y.getAuthToken());
            t.put("attempted", false);
            t.put("accessTokenGenerated", yamlToken);
            t.put("source", yamlToken ? "yamlAuthToken" : "none");
            return t;
        }

        try {
            String reqBody = objectMapper.writeValueAsString(Map.of(
                    "Username", username.trim(),
                    "Password", password.trim()));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(y.getConnectTimeoutMs()))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl.trim()))
                    .timeout(Duration.ofMillis(y.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            t.put("attempted", true);
            t.put("httpStatus", resp.statusCode());
            String token = extractToken(resp.body());
            t.put("accessTokenGenerated", resp.statusCode() == 200 && hasText(token));
            t.put("accessTokenMasked", maskSecret(token));
            if (resp.statusCode() != 200) {
                t.put("error", trim(resp.body(), 300));
            }
            return t;
        } catch (Exception ex) {
            log.warn("[Emsigner debug] Token check failed: {}", ex.getMessage());
            t.put("attempted", true);
            t.put("accessTokenGenerated", false);
            t.put("error", ex.getMessage());
            return t;
        }
    }

    private String extractToken(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String k : List.of("access_token", "Token", "token")) {
                JsonNode n = root.path(k);
                if (!n.isMissingNode() && !n.isNull() && hasText(n.asText())) {
                    return n.asText();
                }
            }
            JsonNode data = root.path("Data");
            if (!data.isMissingNode()) {
                for (String k : List.of("access_token", "Token", "token")) {
                    JsonNode n = data.path(k);
                    if (!n.isMissingNode() && !n.isNull() && hasText(n.asText())) {
                        return n.asText();
                    }
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private static AggregatorProviderConfig pickConfig(List<AggregatorProviderConfig> rows) {
        return rows.stream()
                .sorted((a, b) -> Integer.compare(rank(a), rank(b)))
                .findFirst()
                .orElse(rows.get(0));
    }

    private static int rank(AggregatorProviderConfig c) {
        String s = c.getStepType();
        if (!hasText(s)) return 100;
        if ("ESIGN_AGREEMENT".equalsIgnoreCase(s)) return 0;
        if ("ESIGN".equalsIgnoreCase(s)) return 1;
        if ("ESIGN_KFS".equalsIgnoreCase(s)) return 2;
        return 50;
    }

    private static String strExtra(Map<String, Object> extra, String key, String def) {
        Object v = extra.get(key);
        return (v == null || !hasText(v.toString())) ? def : v.toString().trim();
    }

    private static String firstExtra(Map<String, Object> extra, String... keysAndFallback) {
        if (extra != null) {
            for (String k : keysAndFallback) {
                Object v = extra.get(k);
                if (v != null && hasText(v.toString())) {
                    return v.toString().trim();
                }
            }
        }
        return keysAndFallback.length > 0 ? keysAndFallback[keysAndFallback.length - 1] : "";
    }

    private static String absoluteUrl(String base, String pathOrAbsolute) {
        if (!hasText(pathOrAbsolute)) return trimSlash(base);
        String p = pathOrAbsolute.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        return trimSlash(base) + "/" + p.replaceFirst("^/+", "");
    }

    private static String trimSlash(String s) {
        if (!hasText(s)) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String safeString(String s) {
        return s == null ? "" : s.trim();
    }

    private static String maskSecret(String raw) {
        if (!hasText(raw)) return "";
        String v = raw.trim();
        if (v.length() <= 4) return "****";
        return v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }

    private static String maskUser(String value) {
        if (!hasText(value)) return "";
        String v = value.trim();
        if (v.contains("@")) {
            int at = v.indexOf('@');
            if (at <= 2) return "***" + v.substring(at);
            return v.substring(0, 2) + "***" + v.substring(at);
        }
        if (v.length() <= 3) return "***";
        return v.substring(0, 2) + "***";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

