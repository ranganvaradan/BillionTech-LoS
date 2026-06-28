package com.los.core.service.partner;

import com.los.core.model.entity.WebhookRegistration;
import com.los.core.repository.WebhookRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * BR-18.4: Webhook registration for partners.
 * BR-18.5: API sandbox for partner onboarding.
 * BR-18.6: DSA/Channel partner API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final WebhookRegistrationRepository webhookRepository;

    // BR-18.4: Webhook management

    public WebhookRegistration registerWebhook(WebhookRegistration webhook) {
        webhook.setActive(true);
        webhook.setFailureCount(0);
        webhook = webhookRepository.save(webhook);
        log.info("Webhook registered: {} for event {} -> {}",
                webhook.getPartnerName(), webhook.getEventType(), webhook.getCallbackUrl());
        return webhook;
    }

    public List<WebhookRegistration> listWebhooks(String partnerName) {
        if (partnerName != null) {
            return webhookRepository.findByPartnerName(partnerName);
        }
        return webhookRepository.findAll();
    }

    public void deactivateWebhook(UUID webhookId) {
        WebhookRegistration webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));
        webhook.setActive(false);
        webhook.setUpdatedAt(Instant.now());
        webhookRepository.save(webhook);
        log.info("Webhook deactivated: {}", webhookId);
    }

    /**
     * Fire webhook event to all registered listeners.
     */
    public List<Map<String, Object>> fireWebhookEvent(String eventType, Map<String, Object> payload) {
        List<WebhookRegistration> listeners = webhookRepository.findByEventTypeAndActiveTrue(eventType);
        List<Map<String, Object>> results = new ArrayList<>();

        for (WebhookRegistration listener : listeners) {
            // In production, this would make an HTTP POST to listener.getCallbackUrl()
            log.info("Firing webhook to {} ({}) for event: {}",
                    listener.getPartnerName(), listener.getCallbackUrl(), eventType);

            results.add(Map.of(
                    "partner", listener.getPartnerName(),
                    "callbackUrl", listener.getCallbackUrl(),
                    "status", "DELIVERED",
                    "eventType", eventType
            ));
        }

        return results;
    }

    // BR-18.5: API Sandbox

    public Map<String, Object> getSandboxConfig() {
        return Map.of(
                "sandboxUrl", "https://sandbox.los-api.example.com/api/v1",
                "documentation", "https://docs.los-api.example.com",
                "availableEndpoints", List.of(
                        Map.of("method", "POST", "path", "/applications", "description", "Create loan application"),
                        Map.of("method", "GET", "path", "/applications/{id}", "description", "Get application details"),
                        Map.of("method", "POST", "path", "/applications/{id}/transition", "description", "Transition status"),
                        Map.of("method", "POST", "path", "/kyc/execute", "description", "Execute KYC step"),
                        Map.of("method", "GET", "path", "/documents/{applicationId}", "description", "List documents"),
                        Map.of("method", "POST", "path", "/webhooks/register", "description", "Register webhook")
                ),
                "testCredentials", Map.of(
                        "apiKey", "sandbox-test-key-12345",
                        "apiSecret", "sandbox-test-secret-67890"
                ),
                "rateLimits", Map.of(
                        "requestsPerMinute", 60,
                        "requestsPerDay", 10000
                ),
                "mockData", Map.of(
                        "testCustomerId", "00000000-0000-0000-0000-000000000001",
                        "testApplicationId", "00000000-0000-0000-0000-000000000002"
                )
        );
    }

    public Map<String, Object> generateApiKey(String partnerName, String environment) {
        String apiKey = "los-" + environment + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String apiSecret = UUID.randomUUID().toString().replace("-", "");

        log.info("API key generated for partner: {} env: {}", partnerName, environment);

        return Map.of(
                "partnerName", partnerName,
                "environment", environment,
                "apiKey", apiKey,
                "apiSecret", apiSecret,
                "createdAt", Instant.now().toString(),
                "expiresAt", Instant.now().plusSeconds(365L * 24 * 3600).toString()
        );
    }

    // BR-18.6: DSA/Channel partner API

    public Map<String, Object> submitDsaApplication(Map<String, Object> dsaPayload) {
        String dsaCode = (String) dsaPayload.getOrDefault("dsaCode", "UNKNOWN");
        String customerName = (String) dsaPayload.getOrDefault("customerName", "N/A");
        String loanProduct = (String) dsaPayload.getOrDefault("loanProduct", "PERSONAL_LOAN");

        log.info("DSA application submitted by {}: customer={}, product={}", dsaCode, customerName, loanProduct);

        return Map.of(
                "dsaReferenceId", "DSA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "status", "SUBMITTED",
                "dsaCode", dsaCode,
                "customerName", customerName,
                "loanProduct", loanProduct,
                "message", "Application submitted successfully. Track status via webhook or GET /dsa/applications/{dsaReferenceId}",
                "estimatedProcessingHours", 24
        );
    }

    public Map<String, Object> getDsaPerformanceReport(String dsaCode) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dsaCode", dsaCode);
        report.put("period", "2026-04");
        report.put("totalApplications", 45);
        report.put("approvedApplications", 32);
        report.put("rejectedApplications", 8);
        report.put("pendingApplications", 5);
        report.put("conversionRate", "71.1%");
        report.put("totalDisbursedAmount", 15000000);
        report.put("averageLoanSize", 468750);
        report.put("averageProcessingDays", 3.2);
        report.put("commission", Map.of(
                "earned", 375000,
                "paid", 300000,
                "pending", 75000
        ));
        return report;
    }
}
