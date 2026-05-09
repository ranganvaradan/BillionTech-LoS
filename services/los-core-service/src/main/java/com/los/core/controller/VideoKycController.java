package com.los.core.controller;

import com.los.core.model.enums.VkycStatus;
import com.los.core.service.vkyc.VkycWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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

    @GetMapping("/{applicationId}/workflow-ordering")
    @Operation(summary = "Fetch workflow ordering including VKYC")
    public ResponseEntity<Map<String, Object>> workflowOrdering(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(vkycWorkflowService.getWorkflowOrdering(applicationId));
    }
}
