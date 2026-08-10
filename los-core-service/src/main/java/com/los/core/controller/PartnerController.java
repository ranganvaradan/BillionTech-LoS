package com.los.core.controller;

import com.los.core.model.entity.WebhookRegistration;
import com.los.core.service.partner.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-18.4-18.6: Partner integration, webhook, sandbox, DSA APIs.
 */
@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
@Tag(name = "Partner Integration", description = "Webhook registration, API sandbox, DSA/channel partner APIs")
public class PartnerController {

    private final PartnerService partnerService;

    @PostMapping("/webhooks")
    @Operation(summary = "BR-18.4: Register a webhook")
    public ResponseEntity<WebhookRegistration> registerWebhook(@RequestBody WebhookRegistration webhook) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partnerService.registerWebhook(webhook));
    }

    @GetMapping("/webhooks")
    @Operation(summary = "BR-18.4: List webhooks")
    public ResponseEntity<List<WebhookRegistration>> listWebhooks(
            @RequestParam(required = false) String partnerName) {
        return ResponseEntity.ok(partnerService.listWebhooks(partnerName));
    }

    @DeleteMapping("/webhooks/{webhookId}")
    @Operation(summary = "BR-18.4: Deactivate a webhook")
    public ResponseEntity<Void> deactivateWebhook(@PathVariable UUID webhookId) {
        partnerService.deactivateWebhook(webhookId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhooks/fire")
    @Operation(summary = "Fire a webhook event to all listeners")
    public ResponseEntity<List<Map<String, Object>>> fireWebhook(
            @RequestParam String eventType,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(partnerService.fireWebhookEvent(eventType, payload));
    }

    @GetMapping("/sandbox")
    @Operation(summary = "BR-18.5: Get API sandbox configuration")
    public ResponseEntity<Map<String, Object>> sandboxConfig() {
        return ResponseEntity.ok(partnerService.getSandboxConfig());
    }

    @PostMapping("/sandbox/api-key")
    @Operation(summary = "BR-18.5: Generate sandbox API key")
    public ResponseEntity<Map<String, Object>> generateApiKey(
            @RequestParam String partnerName,
            @RequestParam(defaultValue = "sandbox") String environment) {
        return ResponseEntity.ok(partnerService.generateApiKey(partnerName, environment));
    }

    @PostMapping("/dsa/applications")
    @Operation(summary = "BR-18.6: Submit DSA application")
    public ResponseEntity<Map<String, Object>> submitDsaApplication(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(partnerService.submitDsaApplication(payload));
    }

    @GetMapping("/dsa/{dsaCode}/performance")
    @Operation(summary = "BR-18.6: Get DSA performance report")
    public ResponseEntity<Map<String, Object>> dsaPerformance(@PathVariable String dsaCode) {
        return ResponseEntity.ok(partnerService.getDsaPerformanceReport(dsaCode));
    }
}
