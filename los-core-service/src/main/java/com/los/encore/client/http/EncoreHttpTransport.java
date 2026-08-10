package com.los.encore.client.http;

import com.los.encore.client.config.EncoreClientProperties;
import com.los.encore.client.logging.LogSanitizer;
import com.los.encore.client.logging.LmsLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Low-level Encore HTTP transport — Basic Auth, timeouts, GET retries, structured events.
 * Adapted from legacy {@code EncoreHTTPClientServiceFacadeImpl}.
 */
public class EncoreHttpTransport {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private static final Logger log = LoggerFactory.getLogger(EncoreHttpTransport.class);
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_APPLICATION_ID = "applicationId";
    public static final String MDC_PRODUCT_CODE = "productCode";

    private final EncoreClientProperties properties;
    private final HttpClient httpClient;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public EncoreHttpTransport(EncoreClientProperties properties) {
        this.properties = properties;
        this.connectTimeoutMs = clampPositiveMillis(properties.getConnectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS);
        this.readTimeoutMs = clampPositiveMillis(properties.getReadTimeoutMs(), DEFAULT_READ_TIMEOUT_MS);
        if (connectTimeoutMs != properties.getConnectTimeoutMs() || readTimeoutMs != properties.getReadTimeoutMs()) {
            log.warn("Encore HTTP timeouts clamped to connect={} ms read={} ms (configured connect={} read={})",
                    connectTimeoutMs, readTimeoutMs,
                    properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        }
        Duration connectDuration = Duration.ofMillis(Math.max(connectTimeoutMs, 1000));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectDuration)
                .build();
    }

    private static int clampPositiveMillis(int value, int defaultMs) {
        if (value <= 0) {
            return defaultMs;
        }
        return value;
    }

    public boolean isConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getApiUsername() != null && !properties.getApiUsername().isBlank()
                && properties.getApiPassword() != null && !properties.getApiPassword().isBlank();
    }

    public String httpPost(String apiPath, Map<String, String> queryParams, String requestBody) {
        return execute("POST", apiPath, queryParams, requestBody, false);
    }

    public String httpGet(String apiPath, Map<String, String> queryParams) {
        return execute("GET", apiPath, queryParams, null, true);
    }

    private String execute(String method, String apiPath, Map<String, String> queryParams,
                           String requestBody, boolean allowGetRetry) {
        String correlation = Optional.ofNullable(MDC.get(MDC_CORRELATION_ID)).orElse("-");
        String appId = Optional.ofNullable(MDC.get(MDC_APPLICATION_ID)).orElse("-");
        String prodCode = Optional.ofNullable(MDC.get(MDC_PRODUCT_CODE)).orElse("-");
        String url = buildUrl(apiPath, queryParams);
        Instant start = Instant.now();

        log.info("event={} correlationId={} appId={} productCode={} method={} path={} baseUrl={} queryKeys={}",
                LmsLogEvent.LMS_REQUEST_INITIATED, correlation, appId, prodCode, method, apiPath,
                properties.getBaseUrl(), queryParams != null ? queryParams.keySet() : "[]");
        logEncoreRequestDetails(method, apiPath, queryParams, requestBody, url);

        int attempts = 0;
        int maxAttempts = allowGetRetry ? 1 + Math.max(0, properties.getMaxRetriesForGet()) : 1;
        RuntimeException last = null;

        while (attempts < maxAttempts) {
            attempts++;
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", buildBasicAuthHeader());
                applyRestAuthHeader(b, apiPath);

                HttpRequest request;
                if ("POST".equals(method)) {
                    if (requestBody != null && !requestBody.isBlank()) {
                        b.POST(HttpRequest.BodyPublishers.ofString(requestBody));
                    } else {
                        b.POST(HttpRequest.BodyPublishers.noBody());
                    }
                    request = b.build();
                } else {
                    request = b.GET().build();
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long ms = Duration.between(start, Instant.now()).toMillis();

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("event={} correlationId={} appId={} productCode={} method={} path={} status={} durationMs={}",
                            LmsLogEvent.LMS_REQUEST_SUCCESS, correlation, appId, prodCode, method,
                            apiPath, response.statusCode(), ms);
                    log.debug("event={} correlationId={} responsePayload={}",
                            LmsLogEvent.LMS_REQUEST_SUCCESS, correlation,
                            LogSanitizer.maskForLog(response.body(), 2000));
                    return response.body();
                }

                if (allowGetRetry && attempts < maxAttempts && isRetryableStatus(response.statusCode())) {
                    log.warn("event={} correlationId={} method={} path={} status={} attempt={}/{}",
                            LmsLogEvent.LMS_REQUEST_RETRY, correlation, method, apiPath,
                            response.statusCode(), attempts, maxAttempts);
                    sleepBackoff();
                    continue;
                }

                String err = "Encore API error (HTTP " + response.statusCode() + "): "
                        + LogSanitizer.maskForLog(response.body(), 2000);
                log.error("event={} correlationId={} appId={} productCode={} method={} path={} status={} durationMs={} body={}",
                        LmsLogEvent.LMS_REQUEST_FAILED, correlation, appId, prodCode, method, apiPath,
                        response.statusCode(), ms, LogSanitizer.maskForLog(response.body(), 2000));
                log.error("[LOS][ENCORE][RESPONSE] HTTP {} — see [LOS][ENCORE][REQUEST] lines above for full URL and payload",
                        response.statusCode());
                throw new RuntimeException(err);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("event={} correlationId={} method={} path={} interrupted=true",
                        LmsLogEvent.LMS_REQUEST_FAILED, correlation, method, apiPath);
                throw new RuntimeException("Encore " + method + " interrupted", e);
            } catch (IOException e) {
                last = new RuntimeException("Encore " + method + " error for " + apiPath + ": " + e.getMessage(), e);
                if (allowGetRetry && attempts < maxAttempts) {
                    log.warn("event={} correlationId={} method={} path={} attempt={}/{} msg={}",
                            LmsLogEvent.LMS_REQUEST_RETRY, correlation, method, apiPath,
                            attempts, maxAttempts, e.getMessage());
                    sleepBackoff();
                } else {
                    log.error("event={} correlationId={} method={} path={} msg={}",
                            LmsLogEvent.LMS_REQUEST_FAILED, correlation, method,
                            apiPath, e.getMessage());
                    throw last;
                }
            }
        }
        throw last != null ? last : new RuntimeException("Encore request failed after retries");
    }

    private static boolean isRetryableStatus(int code) {
        return code == 502 || code == 503 || code == 504;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(Math.max(0, properties.getRetryBackoffMs()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildUrl(String apiPath, Map<String, String> queryParams) {
        StringBuilder urlBuilder = new StringBuilder(properties.getBaseUrl());
        if (!properties.getBaseUrl().endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(apiPath);
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                first = false;
                urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                urlBuilder.append("=");
                urlBuilder.append(URLEncoder.encode(
                        entry.getValue() != null ? entry.getValue() : "", StandardCharsets.UTF_8));
            }
        }
        return urlBuilder.toString();
    }

    private String buildBasicAuthHeader() {
        String credentials = properties.getApiUsername() + ":" + properties.getApiPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void applyRestAuthHeader(HttpRequest.Builder builder, String apiPath) {
        if (apiPath == null || !apiPath.startsWith("api/")) {
            return;
        }
        String token = properties.getRestAuthToken();
        if (token != null && !token.isBlank()) {
            builder.header("X-Auth-Token", token.trim());
        }
    }

    private void logEncoreRequestDetails(String method, String apiPath, Map<String, String> queryParams,
                                         String requestBody, String fullUrl) {
        int pwdLen = properties.getApiPassword() != null ? properties.getApiPassword().length() : 0;
        log.info("[LOS][ENCORE][REQUEST] method={} path={} baseUrl={} apiUsername={} apiPasswordLength={} "
                        + "authHeader=Basic (credentials base64-encoded, not logged)",
                method, apiPath, properties.getBaseUrl(), properties.getApiUsername(), pwdLen);
        log.info("[LOS][ENCORE][REQUEST] resolvedUrl={}", LogSanitizer.maskForLog(fullUrl, 16_000));
        if (queryParams != null && !queryParams.isEmpty()) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue() : "";
                int max = "loanOdAccount".equals(key) ? 16_000 : 2_000;
                log.info("[LOS][ENCORE][REQUEST] queryParam.{}={}", key, LogSanitizer.maskForLog(value, max));
            }
        }
        if (requestBody != null && !requestBody.isBlank()) {
            log.info("[LOS][ENCORE][REQUEST] postBody={}", LogSanitizer.maskForLog(requestBody, 16_000));
        } else {
            log.info("[LOS][ENCORE][REQUEST] postBody=(empty — openAccount uses query params per bl-core)");
        }
        log.info("[LOS][ENCORE][REQUEST] headers: Accept=application/json, Content-Type=application/json, Authorization=Basic ***"
                + (usesRestAuth(apiPath) ? ", X-Auth-Token=***" : ""));
    }

    private boolean usesRestAuth(String apiPath) {
        if (apiPath == null || !apiPath.startsWith("api/")) {
            return false;
        }
        String token = properties.getRestAuthToken();
        return token != null && !token.isBlank();
    }
}
