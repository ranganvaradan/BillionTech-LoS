package com.los.core.controller;

import com.los.core.service.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Video KYC Webhook Controller — receives async callbacks from Hyperverge
 * for VKYC session completion, face match results, and liveness checks.
 *
 * Adapted from legacy HyperVergeController.java callback patterns.
 * Legacy reference: bl-core/.../controller/hyperverge/HyperVergeController.java
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vkyc")
@RequiredArgsConstructor
@Tag(name = "Video KYC Webhooks", description = "Webhook endpoints for Hyperverge VKYC callbacks")
public class VideoKycWebhookController {

    private final AuditService auditService;

    /**
     * Hyperverge VKYC session completion callback.
     * Called when the borrower completes the video KYC session.
     *
     * Payload from Hyperverge typically contains:
     *   - transactionId: The VKYC session transaction ID
     *   - status: "auto_approved", "needs_review", "auto_declined", "user_cancelled"
     *   - result: { startKycUrl, faceMatch, liveness, ocrData, ... }
     *   - workflowDetails: Workflow execution details
     */
    @PostMapping("/webhook/session-complete")
    @Operation(summary = "Webhook for Hyperverge VKYC session completion")
    public ResponseEntity<Map<String, Object>> sessionComplete(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String status = String.valueOf(payload.getOrDefault("status", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        log.info("[VKYC Webhook] Session complete — txnId={}, status={}, appId={}",
                transactionId, status, applicationId);

        // Extract nested result data
        Object resultObj = payload.get("result");
        Map<String, Object> result = resultObj instanceof Map ? (Map<String, Object>) resultObj : Map.of();

        String faceMatchStatus = String.valueOf(result.getOrDefault("faceMatchStatus", ""));
        String livenessStatus = String.valueOf(result.getOrDefault("livenessStatus", ""));

        if (!applicationId.isEmpty()) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "VKYC_SESSION_COMPLETE",
                        Map.of("transactionId", transactionId, "status", status,
                                "faceMatch", faceMatchStatus, "liveness", livenessStatus));
            } catch (Exception e) {
                log.warn("Could not log audit event for VKYC session webhook: {}", e.getMessage());
            }
        }

        // Map Hyperverge status to our KYC step outcome
        String kycOutcome;
        switch (status.toLowerCase()) {
            case "auto_approved" -> kycOutcome = "SUCCESS";
            case "needs_review" -> kycOutcome = "MANUAL_REVIEW";
            case "auto_declined" -> kycOutcome = "FAILURE";
            case "user_cancelled" -> kycOutcome = "CANCELLED";
            default -> kycOutcome = "PENDING";
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "kycOutcome", kycOutcome,
                "status", "PROCESSED"
        ));
    }

    /**
     * Hyperverge face match result callback.
     * Triggered when face comparison between selfie and ID document completes.
     */
    @PostMapping("/webhook/face-match")
    @Operation(summary = "Webhook for Hyperverge face match result")
    public ResponseEntity<Map<String, Object>> faceMatchResult(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        Object resultObj = payload.get("result");
        Map<String, Object> result = resultObj instanceof Map ? (Map<String, Object>) resultObj : Map.of();

        double matchScore = 0.0;
        if (result.containsKey("match")) {
            Object matchObj = result.get("match");
            if (matchObj instanceof Number) {
                matchScore = ((Number) matchObj).doubleValue();
            } else if (matchObj != null) {
                try { matchScore = Double.parseDouble(String.valueOf(matchObj)); } catch (NumberFormatException ignored) {}
            }
        }
        String matchResult = matchScore >= 0.85 ? "MATCH" : "MISMATCH";

        log.info("[VKYC Webhook] Face match — txnId={}, score={}, result={}",
                transactionId, matchScore, matchResult);

        if (!applicationId.isEmpty()) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "VKYC_FACE_MATCH",
                        Map.of("transactionId", transactionId, "matchScore", matchScore, "matchResult", matchResult));
            } catch (Exception e) {
                log.warn("Could not log audit event for face match webhook: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "matchScore", matchScore,
                "matchResult", matchResult,
                "status", "PROCESSED"
        ));
    }

    /**
     * Hyperverge liveness detection callback.
     */
    @PostMapping("/webhook/liveness")
    @Operation(summary = "Webhook for Hyperverge liveness detection result")
    public ResponseEntity<Map<String, Object>> livenessResult(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));

        Object resultObj = payload.get("result");
        Map<String, Object> result = resultObj instanceof Map ? (Map<String, Object>) resultObj : Map.of();

        boolean livenessDetected = Boolean.parseBoolean(
                String.valueOf(result.getOrDefault("live", "false")));
        double confidence = result.containsKey("confidence") ?
                ((Number) result.get("confidence")).doubleValue() : 0.0;

        log.info("[VKYC Webhook] Liveness — txnId={}, live={}, confidence={}",
                transactionId, livenessDetected, confidence);

        if (!applicationId.isEmpty()) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "VKYC_LIVENESS",
                        Map.of("transactionId", transactionId, "livenessDetected", livenessDetected,
                                "confidence", confidence));
            } catch (Exception e) {
                log.warn("Could not log audit event for liveness webhook: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "livenessDetected", livenessDetected,
                "confidence", confidence,
                "status", "PROCESSED"
        ));
    }

    /**
     * Hyperverge OCR data extraction callback.
     * Triggered when document OCR (Aadhaar, PAN, etc.) completes.
     */
    @PostMapping("/webhook/ocr-extract")
    @Operation(summary = "Webhook for Hyperverge OCR document data extraction")
    public ResponseEntity<Map<String, Object>> ocrExtract(@RequestBody Map<String, Object> payload) {
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String applicationId = String.valueOf(payload.getOrDefault("applicationId", ""));
        String documentType = String.valueOf(payload.getOrDefault("documentType", ""));

        log.info("[VKYC Webhook] OCR extract — txnId={}, docType={}", transactionId, documentType);

        if (!applicationId.isEmpty()) {
            try {
                auditService.logEvent(UUID.fromString(applicationId), "VKYC_OCR_EXTRACT",
                        Map.of("transactionId", transactionId, "documentType", documentType));
            } catch (Exception e) {
                log.warn("Could not log audit event for OCR extract webhook: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "received", true,
                "transactionId", transactionId,
                "documentType", documentType,
                "status", "PROCESSED"
        ));
    }
}
