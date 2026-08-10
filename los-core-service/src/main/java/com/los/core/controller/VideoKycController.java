package com.los.core.controller;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.PhysicalVkycCompletionRequest;
import com.los.core.model.enums.VkycPkycReason;
import com.los.core.model.enums.VkycStatus;
import com.los.core.service.vkyc.VkycWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vkyc")
@RequiredArgsConstructor
@Tag(name = "Video KYC", description = "VKYC configuration, evaluation, and stage APIs")
public class VideoKycController {

    private final VkycWorkflowService vkycWorkflowService;

    @GetMapping("/{applicationId}/config")
    @Operation(summary = "Fetch VKYC workflow configuration")
    public ResponseEntity<Map<String, Object>> config(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(vkycWorkflowService.getVkycConfiguration(applicationId));
    }

    @GetMapping("/{applicationId}/eligibility")
    @Operation(summary = "Evaluate VKYC eligibility")
    public ResponseEntity<Map<String, Object>> eligibility(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(vkycWorkflowService.evaluateEligibility(applicationId));
    }

    @PostMapping("/{applicationId}/generate-url")
    @Operation(summary = "Generate VKYC URL")
    public ResponseEntity<Map<String, Object>> generateUrl(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(vkycWorkflowService.generateVkycUrl(applicationId, actor));
    }

    @PostMapping("/{applicationId}/resend-link")
    @Operation(summary = "Resend VKYC URL")
    public ResponseEntity<Map<String, Object>> resendLink(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(vkycWorkflowService.resendVkycUrl(applicationId, actor));
    }

    @PostMapping("/{applicationId}/stage")
    @Operation(summary = "Update VKYC stage")
    public ResponseEntity<Map<String, Object>> updateStage(
            @PathVariable UUID applicationId,
            @RequestParam VkycStatus status,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(vkycWorkflowService.updateVkycStage(applicationId, status, actor));
    }

    @GetMapping("/{applicationId}/timeline")
    @Operation(summary = "Fetch VKYC timeline/status")
    public ResponseEntity<Map<String, Object>> timeline(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(vkycWorkflowService.getTimeline(applicationId));
    }

    @PostMapping("/{applicationId}/complete-physical-kyc")
    @Operation(summary = "Complete VKYC via Physical KYC (PKYC) fallback when enabled on the workflow")
    public ResponseEntity<Map<String, Object>> completePhysicalKyc(
            @PathVariable UUID applicationId,
            @RequestBody PhysicalVkycCompletionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesCsv) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessRuleException("X-User-Id header is required for Physical KYC completion", "PKYC_USER_REQUIRED", "PKYC_COMPLETE", null);
        }
        UUID actor = UUID.fromString(userId.trim());
        Set<String> roles = parseRoles(userRole, userRolesCsv);
        String rawReason = body != null && body.getReason() != null ? body.getReason().trim().toUpperCase(Locale.ROOT) : "";
        VkycPkycReason reason;
        try {
            reason = VkycPkycReason.valueOf(rawReason);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid PKYC reason", "PKYC_REASON_INVALID", "PKYC_COMPLETE", Map.of("reason", rawReason));
        }
        String comments = body != null ? body.getComments() : null;
        UUID documentId = body != null ? body.getDocumentId() : null;
        return ResponseEntity.ok(vkycWorkflowService.completePhysicalKyc(applicationId, reason, comments, documentId, actor, roles));
    }

    @GetMapping("/{applicationId}/workflow-ordering")
    @Operation(summary = "Fetch workflow ordering including VKYC")
    public ResponseEntity<Map<String, Object>> workflowOrdering(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(vkycWorkflowService.getWorkflowOrdering(applicationId));
    }

    private static Set<String> parseRoles(String userRole, String userRolesCsv) {
        Set<String> roles = new HashSet<>();
        if (userRole != null && !userRole.isBlank()) {
            roles.add(userRole.trim().toUpperCase(Locale.ROOT));
        }
        if (userRolesCsv != null && !userRolesCsv.isBlank()) {
            for (String r : userRolesCsv.split(",")) {
                if (!r.isBlank()) {
                    roles.add(r.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return roles;
    }
}
