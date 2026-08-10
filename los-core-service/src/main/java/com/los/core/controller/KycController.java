package com.los.core.controller;

import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.dto.request.ManualKycReviewRequest;
import com.los.core.model.dto.response.ManualKycReviewResponse;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ManualKycDecision;
import com.los.core.exception.ForbiddenException;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.kyc.IKycManualReviewService;
import com.los.core.service.audit.AuditService;
import com.los.core.service.flow.event.AutoBureauPullRequestedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC orchestration and step execution")
public class KycController {

    private final IKycOrchestrationService kycOrchestrationService;
    private final IKycManualReviewService kycManualReviewService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private static final java.util.Set<String> MANUAL_KYC_ALLOWED_ROLES = java.util.Set.of(
            "ADMIN",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER",
            "CREDIT_ANALYST"
    );

    @PostMapping("/{applicationId}/step/{stepType}")
    @Operation(summary = "Execute a single KYC step")
    public ResponseEntity<KycStepResultResponse> executeStep(
            @PathVariable UUID applicationId,
            @PathVariable KycStepType stepType,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(kycOrchestrationService.executeStep(applicationId, stepType, payload));
    }

    @GetMapping("/{applicationId}/results")
    @Operation(summary = "Get all KYC step results for an application")
    public ResponseEntity<List<KycStepResultResponse>> getResults(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(kycOrchestrationService.getStepResults(applicationId));
    }

    @GetMapping("/{applicationId}/outcome")
    @Operation(summary = "Compute current KYC outcome for an application")
    public ResponseEntity<Map<String, Object>> getKycOutcome(@PathVariable UUID applicationId) {
        Map<String, Object> outcome = kycOrchestrationService.computeKycOutcome(applicationId);
        return ResponseEntity.ok(outcome);
    }

    @PostMapping("/step/{stepResultId}/override")
    @Operation(summary = "Override a failed KYC step (requires authorization)")
    public ResponseEntity<KycStepResultResponse> overrideStep(
            @PathVariable UUID stepResultId,
            @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID overrideBy = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(kycOrchestrationService.overrideStep(stepResultId, reason, overrideBy));
    }

    @PostMapping("/{applicationId}/manual/{stepType}")
    @Operation(summary = "Save manual KYC review/update for a step")
    public ResponseEntity<ManualKycReviewResponse> saveManualReview(
            @PathVariable UUID applicationId,
            @PathVariable KycStepType stepType,
            @RequestBody ManualKycReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesCsv) {

        java.util.Set<String> roles = new java.util.HashSet<>();
        if (userRole != null && !userRole.isBlank()) {
            roles.add(userRole.trim());
        }
        if (userRolesCsv != null && !userRolesCsv.isBlank()) {
            for (String r : userRolesCsv.split(",")) {
                if (!r.isBlank()) roles.add(r.trim());
            }
        }

        boolean authorized = roles.stream().anyMatch(MANUAL_KYC_ALLOWED_ROLES::contains);
        if (!authorized) {
            throw new ForbiddenException("Forbidden: manual KYC updates require internal role");
        }

        UUID reviewedBy = userId != null ? UUID.fromString(userId) : null;

        ManualKycDecision previousDecision = null;
        try {
            ManualKycReviewResponse existing = kycManualReviewService.getByStep(applicationId, stepType);
            previousDecision = existing != null ? existing.getDecision() : null;
        } catch (Exception ignored) {
            // no existing manual review
        }

        ManualKycReviewResponse saved = kycManualReviewService.save(applicationId, stepType, request, reviewedBy);

        auditService.logEvent(
                applicationId,
                "MANUAL_KYC_SAVE",
                "MANUAL_KYC_SAVE",
                reviewedBy,
                java.util.Map.of(
                        "stepType", stepType.name(),
                        "decision", previousDecision != null ? previousDecision.name() : ""
                ),
                java.util.Map.of(
                        "stepType", stepType.name(),
                        "decision", saved.getDecision() != null ? saved.getDecision().name() : ""
                ),
                "Manual KYC saved for step " + stepType.name()
        );

        Map<String, Object> outcome = kycOrchestrationService.computeKycOutcome(applicationId);
        if ("PASS".equalsIgnoreCase(String.valueOf(outcome.getOrDefault("outcome", "INCOMPLETE")))) {
            eventPublisher.publishEvent(
                    new AutoBureauPullRequestedEvent(applicationId, "MANUAL_KYC_REVIEW_PASS"));
        }

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{applicationId}/manual")
    @Operation(summary = "Get all manual KYC reviews for an application")
    public ResponseEntity<List<ManualKycReviewResponse>> getManualReviews(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(kycManualReviewService.getAll(applicationId));
    }

    @GetMapping("/{applicationId}/manual/{stepType}")
    @Operation(summary = "Get manual KYC review for a specific step")
    public ResponseEntity<ManualKycReviewResponse> getManualReviewByStep(
            @PathVariable UUID applicationId,
            @PathVariable KycStepType stepType) {
        return ResponseEntity.ok(kycManualReviewService.getByStep(applicationId, stepType));
    }

    @PostMapping("/{applicationId}/workflow")
    @Operation(summary = "Execute the full KYC workflow for an application")
    public ResponseEntity<List<KycStepResultResponse>> executeKycWorkflow(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            payload = new HashMap<>();
        }

        List<KycStepResultResponse> results = kycOrchestrationService.executeWorkflow(applicationId, payload);
        return ResponseEntity.ok(results);
    }
}
