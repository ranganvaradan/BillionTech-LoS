package com.los.core.controller;

import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.service.loan.LoanApplicationFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Loan Application Flow Controller — orchestration endpoints for the full lifecycle.
 *
 * Each endpoint drives a single step in the loan lifecycle:
 *   POST /flow/{id}/submit           → DRAFT → KYC_IN_PROGRESS
 *   POST /flow/{id}/kyc              → Run KYC workflow (PAN, Aadhaar, GSTIN, etc.)
 *   POST /flow/{id}/bureau           → Pull credit bureau (Equifax)
 *   POST /flow/{id}/underwrite       → KYC_IN_PROGRESS → UNDERWRITING → APPROVED/REJECTED
 *   POST /flow/{id}/sanction         → APPROVED → SANCTION_ISSUED + KFS generation
 *   POST /flow/{id}/esign            → SANCTION_ISSUED → ESIGN_PENDING
 *   POST /flow/{id}/esign-complete   → ESIGN_PENDING → DISBURSEMENT_PENDING
 *   POST /flow/{id}/disburse         → DISBURSEMENT_PENDING → DISBURSED + LMS handover
 *   POST /flow/{id}/full             → Execute all steps in sequence (batch mode)
 */
@RestController
@RequestMapping("/api/v1/flow")
@RequiredArgsConstructor
@Tag(name = "Loan Flow", description = "End-to-end loan application lifecycle orchestration")
public class LoanApplicationFlowController {

    private final LoanApplicationFlowService flowService;

    @PostMapping("/{applicationId}/submit")
    @Operation(summary = "Step 1: Submit application (DRAFT → KYC_IN_PROGRESS)")
    public ResponseEntity<ApplicationResponse> submit(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.submitApplication(applicationId));
    }

    @PostMapping("/{applicationId}/kyc")
    @Operation(summary = "Step 2: Run KYC workflow (PAN, Aadhaar, GSTIN, etc.)")
    public ResponseEntity<Map<String, Object>> runKyc(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> kycPayload) {
        return ResponseEntity.ok(flowService.runKycWorkflow(applicationId, kycPayload));
    }

    @PostMapping("/{applicationId}/bureau")
    @Operation(summary = "Step 3: Pull credit bureau report (Equifax)")
    public ResponseEntity<Map<String, Object>> pullBureau(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.pullBureauReport(applicationId));
    }

    @PostMapping("/{applicationId}/underwrite")
    @Operation(summary = "Step 4: Run underwriting + credit decision (→ APPROVED or REJECTED)")
    public ResponseEntity<Map<String, Object>> underwrite(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.underwriteApplication(applicationId));
    }

    @PostMapping("/{applicationId}/sanction")
    @Operation(summary = "Step 5: Issue sanction letter + generate KFS (→ SANCTION_ISSUED)")
    public ResponseEntity<Map<String, Object>> sanction(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> sanctionParams) {
        return ResponseEntity.ok(flowService.sanctionApplication(applicationId, sanctionParams));
    }

    @PostMapping("/{applicationId}/esign")
    @Operation(summary = "Step 6: Initiate eSign on KFS + agreement (→ ESIGN_PENDING)")
    public ResponseEntity<Map<String, Object>> initiateESign(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> signerInfo) {
        return ResponseEntity.ok(flowService.initiateESign(applicationId,
                signerInfo != null ? signerInfo : Map.of()));
    }

    @PostMapping("/{applicationId}/esign-complete")
    @Operation(summary = "Step 6b: Complete eSign (webhook callback or manual) (→ DISBURSEMENT_PENDING)")
    public ResponseEntity<ApplicationResponse> completeESign(
            @PathVariable UUID applicationId,
            @RequestParam(required = false) String transactionId) {
        return ResponseEntity.ok(flowService.completeESign(applicationId, transactionId));
    }

    @PostMapping("/{applicationId}/disburse")
    @Operation(summary = "Step 7: Disburse loan + hand over to Encore LMS (→ DISBURSED)")
    public ResponseEntity<Map<String, Object>> disburse(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.disburseAndHandoverToLms(applicationId));
    }

    @PostMapping("/{applicationId}/full")
    @Operation(summary = "Execute all steps: Submit → KYC → Bureau → Underwrite → Sanction → eSign → Disburse → LMS")
    public ResponseEntity<Map<String, Object>> executeFullFlow(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> flowRequest) {
        @SuppressWarnings("unchecked")
        Map<String, Object> kycPayload = (Map<String, Object>) flowRequest.getOrDefault("kycPayload", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> sanctionParams = (Map<String, Object>) flowRequest.getOrDefault("sanctionParams", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> signerInfo = (Map<String, Object>) flowRequest.getOrDefault("signerInfo", Map.of());

        return ResponseEntity.ok(flowService.executeFullFlow(applicationId, kycPayload, sanctionParams, signerInfo));
    }
}
