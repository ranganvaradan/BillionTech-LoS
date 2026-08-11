package com.los.core.controller;

import com.los.core.security.StaffAccessGuard;
import com.los.core.service.audit.AuditService;
import com.los.core.service.demo.DemoModeService;
import com.los.core.service.esign.EsignEmsignerCompletionService;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.IIntegrationRouterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * eSign webhooks – thin HTTP entry points that delegate to
 * {@link IIntegrationRouterService#handleWebhookCallback} for central routing, while
 * preserving path-specific behaviour via {@code webhookKind}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/esign")
@RequiredArgsConstructor
@Tag(name = "eSign Webhooks", description = "Webhook endpoints for eSign provider callbacks and status tracking")
public class ESignWebhookController {

    private final IIntegrationRouterService integrationRouterService;
    private final AuditService auditService;
    private final EsignEmsignerCompletionService esignEmsignerCompletionService;
    private final EsignRequestTrackingService esignRequestTrackingService;
    private final DemoModeService demoModeService;
    private final StaffAccessGuard staffAccessGuard;

    @PostMapping("/webhook/kfs")
    @Operation(summary = "Webhook callback for KFS eSign completion")
    public ResponseEntity<Map<String, Object>> kfsWebhook(@RequestBody Map<String, Object> payload) {
        Map<String, Object> p = withKind(payload, "KFS");
        return ResponseEntity.ok(integrationRouterService.handleWebhookCallback(resolveProvider(p), p));
    }

    @PostMapping("/webhook/agreement")
    @Operation(summary = "Webhook callback for loan agreement eSign completion")
    public ResponseEntity<Map<String, Object>> agreementWebhook(@RequestBody Map<String, Object> payload) {
        Map<String, Object> p = withKind(payload, "AGREEMENT");
        return ResponseEntity.ok(integrationRouterService.handleWebhookCallback(resolveProvider(p), p));
    }

    @PostMapping("/webhook/sanction-letter")
    @Operation(summary = "Webhook callback for sanction letter digital signature")
    public ResponseEntity<Map<String, Object>> sanctionLetterWebhook(@RequestBody Map<String, Object> payload) {
        Map<String, Object> p = withKind(payload, "SANCTION");
        return ResponseEntity.ok(integrationRouterService.handleWebhookCallback(resolveProvider(p), p));
    }

    @PostMapping("/webhook/nach")
    @Operation(summary = "Webhook callback for NACH mandate eSign")
    public ResponseEntity<Map<String, Object>> nachWebhook(@RequestBody Map<String, Object> payload) {
        Map<String, Object> p = withKind(payload, "NACH");
        return ResponseEntity.ok(integrationRouterService.handleWebhookCallback(resolveProvider(p), p));
    }

    @GetMapping("/status/{transactionId}")
    @Operation(summary = "Get eSign status by transaction ID (legacy stub / non-persisted lookup)")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String transactionId) {
        return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "status", "COMPLETED",
                "provider", "emsigner",
                "signedAt", java.time.Instant.now().toString(),
                "documentHash", "sha256:simulated"
        ));
    }

    @GetMapping("/status/application/{applicationId}")
    @Operation(summary = "Latest esign_requests status for an application (INITIATED / PENDING / SIGNED / FAILED / UNKNOWN)")
    public ResponseEntity<Map<String, Object>> getStatusByApplication(@PathVariable UUID applicationId) {
        String st = esignRequestTrackingService.resolveLatestStatusForApplication(applicationId);
        return ResponseEntity.ok(Map.of(
                "applicationId", applicationId.toString(),
                "status", st));
    }

    @PostMapping("/webhook/emsigner")
    @Operation(summary = "EMSIGNER WorkflowStatus callback (WorkflowID + WorkflowStatus; Completed triggers download)")
    public ResponseEntity<Map<String, Object>> emsignerWorkflowWebhook(@RequestBody Map<String, Object> body) {
        String workflowId = firstString(body,
                new String[]{"WorkflowID", "WorkFlowId", "workflowID", "workFlowId"});
        String workflowStatus = firstString(body,
                new String[]{"WorkflowStatus", "Status", "workflowStatus"});
        log.info("[eSign webhook/emsigner] WorkflowID={} WorkflowStatus={}", workflowId, workflowStatus);
        return ResponseEntity.ok(esignEmsignerCompletionService.handleWorkflowCallback(
                workflowId, workflowStatus, body != null ? body : Map.of()));
    }

    @PostMapping("/simulate-completion/{applicationId}")
    @Operation(summary = "Demo: treat embedded signing as complete, download + store PDF, advance application")
    public ResponseEntity<Map<String, Object>> simulateEsignCompletion(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (!demoModeService.isDemoModeEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", DemoModeService.DEMO_MODE_DISABLED_MESSAGE, "success", false));
        }
        staffAccessGuard.requireAdmin(userId, userRole);
        try {
            Map<String, Object> r = esignEmsignerCompletionService.simulateCompletion(applicationId);
            boolean ok = Boolean.TRUE.equals(r.get("success"));
            return ok ? ResponseEntity.ok(r) : ResponseEntity.badRequest().body(r);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "applicationId", applicationId.toString(),
                    "message", "Simulation failed"));
        }
    }

    private static String firstString(Map<String, ?> body, String[] keys) {
        if (body == null) {
            return "";
        }
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !"null".equals(String.valueOf(v)) && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    @PostMapping("/initiate/sanction-letter")
    @Operation(summary = "Initiate lender-side digital signature for sanction letter")
    public ResponseEntity<Map<String, Object>> initiateSanctionLetterSign(
            @RequestParam UUID applicationId,
            @RequestParam String signerName,
            @RequestParam String signerDesignation) {
        String txnId = "ESIGN-SL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Initiating sanction letter signature: appId={}, signer={}, txnId={}", applicationId, signerName, txnId);

        auditService.logEvent(applicationId, "SANCTION_LETTER_SIGN_INITIATED",
                Map.of("transactionId", txnId, "signerName", signerName, "signerDesignation", signerDesignation));

        return ResponseEntity.ok(Map.of(
                "transactionId", txnId,
                "applicationId", applicationId.toString(),
                "signerName", signerName,
                "signerDesignation", signerDesignation,
                "status", "INITIATED",
                "signingUrl", "https://esign-provider.example.com/sign/" + txnId
        ));
    }

    private static Map<String, Object> withKind(Map<String, Object> payload, String kind) {
        if (payload == null) {
            return Map.of("webhookKind", kind);
        }
        HashMap<String, Object> p = new HashMap<>(payload);
        p.putIfAbsent("webhookKind", kind);
        return p;
    }

    private static String resolveProvider(Map<String, Object> p) {
        Object v = p.get("providerName");
        if (v == null) {
            v = p.get("provider");
        }
        return v != null ? v.toString() : "EMSIGNER";
    }
}
