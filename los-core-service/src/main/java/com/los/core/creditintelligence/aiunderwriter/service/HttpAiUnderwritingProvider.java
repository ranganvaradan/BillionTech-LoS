package com.los.core.creditintelligence.aiunderwriter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optional HTTP provider. Disabled when base URL blank. On failure returns unavailable —
 * never DEMO_AI_LOS_URL fallback.
 */
@Component
public class HttpAiUnderwritingProvider implements AiUnderwritingProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpAiUnderwritingProvider.class);

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public HttpAiUnderwritingProvider() {
        this("", new ObjectMapper());
    }

    public HttpAiUnderwritingProvider(String baseUrl) {
        this(baseUrl, new ObjectMapper());
    }

    public HttpAiUnderwritingProvider(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderResponse analyze(
            AiUnderwritingContext context,
            List<String> requestedOutputTypes,
            Map<String, Object> promptVersions) {
        if (!isConfigured()) {
            return ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE");
        }
        try {
            Map<String, Object> body = Map.of(
                    "context", context.toSanitizedMap(),
                    "requestedOutputTypes", requestedOutputTypes == null ? List.of() : requestedOutputTypes,
                    "promptVersions", promptVersions == null ? Map.of() : promptVersions
            );
            String json = mapper.writeValueAsString(body);
            String url = baseUrl.endsWith("/")
                    ? baseUrl + "api/v1/ai-underwriting/analyze"
                    : baseUrl + "/api/v1/ai-underwriting/analyze";
            // Explicitly never append DEMO paths
            if (url.toLowerCase().contains("demo")) {
                log.warn("Http AI provider URL looks like demo; treating as unavailable");
                return ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE");
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE");
            }
            Map<String, Object> parsed = mapper.readValue(response.body(), Map.class);
            List<RawSuggestion> suggestions = new ArrayList<>();
            Object rawList = parsed.get("suggestions");
            if (rawList instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Object type = m.get("type");
                        Object title = m.get("title");
                        Object content = m.get("content");
                        Object promptVersion = m.get("promptVersion");
                        suggestions.add(new RawSuggestion(
                                String.valueOf(type == null ? "NARRATIVE" : type),
                                String.valueOf(title == null ? "" : title),
                                String.valueOf(content == null ? "" : content),
                                m.get("structuredPayload") instanceof Map<?, ?> sp
                                        ? (Map<String, Object>) sp : Map.of(),
                                m.get("evidenceRefs") instanceof List<?> er ? (List<Object>) er : List.of(),
                                m.get("sourceRefs") instanceof List<?> sr ? (List<Object>) sr : List.of(),
                                m.get("limitations") instanceof List<?> lim ? (List<Object>) lim : List.of(),
                                String.valueOf(promptVersion == null ? "" : promptVersion),
                                m.get("modelConfidence") instanceof Number n ? n.doubleValue() : null
                        ));
                    }
                }
            }
            return new ProviderResponse(
                    true,
                    null,
                    String.valueOf(parsed.getOrDefault("modelProvider", "http")),
                    String.valueOf(parsed.getOrDefault("modelName", "remote")),
                    String.valueOf(parsed.getOrDefault("modelVersion", "unknown")),
                    suggestions
            );
        } catch (Exception e) {
            log.warn("HTTP AI underwriting provider failed: {}", e.toString());
            return ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE");
        }
    }
}
