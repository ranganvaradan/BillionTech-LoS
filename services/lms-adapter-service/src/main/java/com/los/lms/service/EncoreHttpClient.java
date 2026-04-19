package com.los.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.lms.config.EncoreProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Encore HTTP Client — adapted from legacy EncoreHTTPClientServiceFacadeImpl.
 *
 * Handles Basic Auth, request/response, and error handling for all Encore API calls.
 * Legacy pattern: Apache HttpClient with Basic Auth + JSON content type.
 * V2.0 pattern: Java 11+ HttpClient with same auth scheme.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncoreHttpClient {

    private final EncoreProperties encoreProperties;
    private final ObjectMapper objectMapper;

    /** Shared, thread-safe HttpClient — created once and reused for all requests. */
    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(encoreProperties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * POST to Encore API with JSON body.
     * Adapted from legacy EncoreHTTPClientServiceFacadeImpl.httpPost()
     */
    public String httpPost(String apiPath, Map<String, String> queryParams, String requestBody) {
        try {
            StringBuilder urlBuilder = new StringBuilder(encoreProperties.getBaseUrl());
            if (!encoreProperties.getBaseUrl().endsWith("/")) urlBuilder.append("/");
            urlBuilder.append(apiPath);

            if (queryParams != null && !queryParams.isEmpty()) {
                urlBuilder.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) urlBuilder.append("&");
                    urlBuilder.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"));
                    urlBuilder.append("=");
                    urlBuilder.append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
                    first = false;
                }
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofMillis(encoreProperties.getReadTimeoutMs()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildBasicAuthHeader());

            if (requestBody != null && !requestBody.isBlank()) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
            } else {
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            }

            log.info("[Encore] POST {} — params: {}", apiPath, queryParams);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            log.info("[Encore] Response: HTTP {} — {}", response.statusCode(),
                    response.body().substring(0, Math.min(response.body().length(), 200)));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else if (response.statusCode() == 500 || response.statusCode() == 400) {
                throw new RuntimeException("Encore API error (HTTP " + response.statusCode() + "): " + response.body());
            } else if (response.statusCode() == 405) {
                throw new RuntimeException("Encore API: Request method not supported for " + apiPath);
            } else {
                throw new RuntimeException("Encore API unexpected status " + response.statusCode() + ": " + response.body());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Encore POST interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Encore POST error for " + apiPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * GET from Encore API with query parameters.
     * Adapted from legacy EncoreHTTPClientServiceFacadeImpl.httpGet()
     */
    public String httpGet(String apiPath, Map<String, String> queryParams) {
        try {
            StringBuilder urlBuilder = new StringBuilder(encoreProperties.getBaseUrl());
            if (!encoreProperties.getBaseUrl().endsWith("/")) urlBuilder.append("/");
            urlBuilder.append(apiPath);

            if (queryParams != null && !queryParams.isEmpty()) {
                urlBuilder.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) urlBuilder.append("&");
                    urlBuilder.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"));
                    urlBuilder.append("=");
                    urlBuilder.append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
                    first = false;
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofMillis(encoreProperties.getReadTimeoutMs()))
                    .header("Accept", "application/json")
                    .header("Authorization", buildBasicAuthHeader())
                    .GET()
                    .build();

            log.info("[Encore] GET {} — params: {}", apiPath, queryParams);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("[Encore] Response: HTTP {} — {}", response.statusCode(),
                    response.body().substring(0, Math.min(response.body().length(), 200)));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new RuntimeException("Encore GET error (HTTP " + response.statusCode() + "): " + response.body());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Encore GET interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Encore GET error for " + apiPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Build Basic Auth header — from legacy pattern of HttpHost + CredentialsProvider.
     */
    private String buildBasicAuthHeader() {
        String credentials = encoreProperties.getApiUsername() + ":" + encoreProperties.getApiPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Check if Encore is configured (credentials are set).
     */
    public boolean isConfigured() {
        return encoreProperties.getBaseUrl() != null
                && !encoreProperties.getBaseUrl().isBlank()
                && encoreProperties.getApiUsername() != null
                && !encoreProperties.getApiUsername().isBlank()
                && encoreProperties.getApiPassword() != null
                && !encoreProperties.getApiPassword().isBlank();
    }
}
