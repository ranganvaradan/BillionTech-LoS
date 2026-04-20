package com.los.core.controller;

import com.los.core.service.audit.AuditService;
import com.los.core.service.loan.LoanApplicationFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * eSign Webhook Controller — receives async callbacks from eSign providers
 * (emsigner, Authbridge) for KFS signing, loan agreement signing, sanction letter signing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/esign")
@RequiredArgsConstructor
@Tag(name = "eSign Webhooks", description = "Webhook endpoints for eSign provider callbacks and status tracking")
public class ESignWebhookController {

    private final AuditService auditService;
    private final LoanApplicationFlowService flowService;

    @PostMapping("/webhook/kfs")
    @Operation(summary = "Webhook callback for KFS eSign completion")
    public ResponseEntity<Map<String, Object>> kfsWebhook(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        log.info("eSign webhook received — KFS: txnId={}, status={}, appId={}", transactionId, status, applicationId);

        boolean transitioned = false;
        if (!applicationId.isEmpty()) {
            try {
                UUID appId = UUID.fromString(applicationId);
                auditService.logEvent(appId, "ESIGN_WEBHOOK_KFS",
                        Map.of("transactionId", transactionId, "status", status));

                // On successful signing, transition application to DISBURSEMENT_PENDING
                if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                    try {
                        flowService.completeESign(appId, transactionId);
                        transitioned = true;
                        log.info("eSign KFS webhook: Application {} transitioned to DISBURSEMENT_PENDING", applicationId);
                    } catch (Exception ex) {
                        log.warn("Could not transition application {} on KFS eSign: {}", applicationId, ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not process eSign KFS webhook for appId={}: {}", applicationId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "transitioned", transitioned,
                "status", "PROCESSED"
        ));
    }

    @PostMapping("/webhook/agreement")
    @Operation(summary = "Webhook callback for loan agreement eSign completion")
    public ResponseEntity<Map<String, Object>> agreementWebhook(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        log.info("eSign webhook received — Agreement: txnId={}, status={}, appId={}", transactionId, status, applicationId);

        boolean transitioned = false;
        if (!applicationId.isEmpty()) {
            try {
                UUID appId = UUID.fromString(applicationId);
                auditService.logEvent(appId, "ESIGN_WEBHOOK_AGREEMENT",
                        Map.of("transactionId", transactionId, "status", status));

                if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                    try {
                        flowService.completeESign(appId, transactionId);
                        transitioned = true;
                        log.info("eSign Agreement webhook: Application {} transitioned to DISBURSEMENT_PENDING", applicationId);
                    } catch (Exception ex) {
                        log.warn("Could not transition application {} on Agreement eSign: {}", applicationId, ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not process eSign Agreement webhook for appId={}: {}", applicationId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "transitioned", transitioned,
                "status", "PROCESSED"
        ));
    }

    @PostMapping("/webhook/sanction-letter")
    @Operation(summary = "Webhook callback for sanction letter digital signature")
    public ResponseEntity<Map<String, Object>> sanctionLetterWebhook(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));
        String signerType = String.valueOf(payload.getOrDefault("signerType", "LENDER"));

        log.info("eSign webhook received — Sanction Letter: txnId={}, status={}, signerType={}", transactionId, status, signerType);

        if (!applicationId.isEmpty()) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "ESIGN_WEBHOOK_SANCTION",
                        Map.of("transactionId", transactionId, "status", status, "signerType", signerType));
            } catch (Exception e) {
                log.warn("Could not log audit event for eSign webhook", e);
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "signerType", signerType,
                "status", "PROCESSED"
        ));
    }

    @PostMapping("/webhook/nach")
    @Operation(summary = "Webhook callback for NACH mandate eSign")
    public ResponseEntity<Map<String, Object>> nachWebhook(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String mandateRef = String.valueOf(payload.getOrDefault("mandateReference", ""));
        String umrn = String.valueOf(payload.getOrDefault("umrn", ""));

        log.info("eSign webhook received — NACH: txnId={}, status={}, mandateRef={}, UMRN={}", transactionId, status, mandateRef, umrn);

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "mandateReference", mandateRef,
                "status", "PROCESSED"
        ));
    }

    @GetMapping("/status/{transactionId}")
    @Operation(summary = "Get eSign status by transaction ID")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String transactionId) {
        // In production: query eSign provider's status API
        return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "status", "COMPLETED",
                "provider", "emsigner",
                "signedAt", java.time.Instant.now().toString(),
                "documentHash", "sha256:simulated"
        ));
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
}
