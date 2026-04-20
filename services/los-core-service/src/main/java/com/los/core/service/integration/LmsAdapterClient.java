package com.los.core.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for los-core-service to call the lms-adapter-service.
 * Uses Eureka service discovery via the gateway or direct service URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LmsAdapterClient {

    private final ObjectMapper objectMapper;

    @Value("${los.integration.lms-adapter.base-url:http://lms-adapter-service:8085}")
    private String lmsAdapterBaseUrl;

    /** Shared, thread-safe HttpClient — created once and reused for all requests. */
    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Hand over a disbursed loan to LMS for servicing.
     * Calls POST /api/v1/lms/handover on the lms-adapter-service.
     *
     * @return Map with handoverId, lmsReferenceId, status, firstEmiDate, emiAmount, totalEmis
     */
    public Map<String, Object> handoverLoan(UUID applicationId, String applicationNumber,
                                             String borrowerName, String borrowerType,
                                             String loanProduct, BigDecimal sanctionedAmount,
                                             BigDecimal interestRate, Integer tenureMonths,
                                             BigDecimal emiAmount, Map<String, Object> borrowerDetails) {
        try {
            Map<String, Object> requestBody = Map.ofEntries(
                    Map.entry("applicationId", applicationId.toString()),
                    Map.entry("applicationNumber", applicationNumber),
                    Map.entry("borrowerName", borrowerName != null ? borrowerName : ""),
                    Map.entry("borrowerType", borrowerType),
                    Map.entry("loanProduct", loanProduct),
                    Map.entry("sanctionedAmount", sanctionedAmount != null ? sanctionedAmount : BigDecimal.ZERO),
                    Map.entry("interestRate", interestRate != null ? interestRate : BigDecimal.ZERO),
                    Map.entry("tenureMonths", tenureMonths != null ? tenureMonths : 0),
                    Map.entry("emiAmount", emiAmount != null ? emiAmount : BigDecimal.ZERO),
                    Map.entry("borrowerDetails", borrowerDetails != null ? borrowerDetails : Map.of())
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lmsAdapterBaseUrl + "/api/v1/lms/handover"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("[LMS Client] Handover request for application: {}", applicationNumber);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                log.info("[LMS Client] Handover successful: lmsRefId={}", result.get("lmsReferenceId"));
                return result;
            } else {
                log.error("[LMS Client] Handover failed: HTTP {} — {}", response.statusCode(), response.body());
                return Map.of("status", "FAILED", "message", "LMS handover failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LMS handover interrupted", e);
        } catch (Exception e) {
            log.error("[LMS Client] Handover error for {}: {}", applicationNumber, e.getMessage());
            return Map.of("status", "FAILED", "message", "LMS handover error: " + e.getMessage());
        }
    }

    /**
     * Get loan account summary from LMS.
     */
    public Map<String, Object> getAccountSummary(String applicationNumber) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lmsAdapterBaseUrl + "/api/v1/lms/status/" + applicationNumber))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                return result;
            }
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (Exception e) {
            log.warn("[LMS Client] Error getting account summary for {}: {}", applicationNumber, e.getMessage());
            return Map.of();
        }
    }
}
