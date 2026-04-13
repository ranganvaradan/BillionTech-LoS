package com.los.core.controller;

import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.enums.KycStepType;
import com.los.core.service.kyc.IKycOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC orchestration and step execution")
public class KycController {

    private final IKycOrchestrationService kycOrchestrationService;

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

    @PostMapping("/step/{stepResultId}/override")
    @Operation(summary = "Override a failed KYC step (requires authorization)")
    public ResponseEntity<KycStepResultResponse> overrideStep(
            @PathVariable UUID stepResultId,
            @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID overrideBy = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(kycOrchestrationService.overrideStep(stepResultId, reason, overrideBy));
    }

    @PostMapping("/{applicationId}/workflow")
    @Operation(summary = "Execute the full KYC workflow for an application")
    public ResponseEntity<List<KycStepResultResponse>> executeWorkflow(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(kycOrchestrationService.executeWorkflow(applicationId, payload));
    }
}
