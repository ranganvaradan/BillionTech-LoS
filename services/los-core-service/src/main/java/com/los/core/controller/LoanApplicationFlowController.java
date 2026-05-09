package com.los.core.controller;

import com.los.core.model.dto.request.PostCreditRejectRequest;
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
 *   POST /flow/{id}/kyc/retry         → KYC_FAILED → KYC_IN_PROGRESS (then POST /kyc to re-run)
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

    @PostMapping("/{applicationId}/kyc/retry")
    @Operation(summary = "KYC retry: KYC_FAILED → KYC_IN_PROGRESS (retains history; call POST /kyc to run checks again)")
    public ResponseEntity<ApplicationResponse> retryKyc(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.retryKyc(applicationId));
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
    @Operation(summary = "Step 4: Run underwriting + credit decision (→ APPROVED, REJECTED, or MANUAL_REVIEW with configured rules)")
    public ResponseEntity<Map<String, Object>> underwrite(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(flowService.underwriteApplication(applicationId, userId));
    }

    @PostMapping("/{applicationId}/underwriting/approve")
    @Operation(summary = "After MANUAL_REVIEW, credit manager approves (→ APPROVED)")
    public ResponseEntity<ApplicationResponse> approveAfterManualReview(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.completeManualUnderwritingDecision(applicationId, true));
    }

    @PostMapping("/{applicationId}/underwriting/reject")
    @Operation(summary = "After MANUAL_REVIEW, credit manager rejects (→ REJECTED)")
    public ResponseEntity<ApplicationResponse> rejectAfterManualReview(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.completeManualUnderwritingDecision(applicationId, false));
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

    @PostMapping("/{applicationId}/cam/reviewed")
    @Operation(summary = "Mark CAM as reviewed (CAM_READY → CAM_REVIEWED)")
    public ResponseEntity<ApplicationResponse> markCamReviewed(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String approvedByUserId) {
        return ResponseEntity.ok(flowService.markCamReviewed(applicationId, approvedByUserId));
    }

    @PostMapping("/{applicationId}/sanction-pending")
    @Operation(summary = "Move to formal sanction step (CAM_REVIEWED → SANCTION_PENDING)")
    public ResponseEntity<ApplicationResponse> proceedToSanctionPending(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.proceedToSanctionPending(applicationId));
    }

    @PostMapping("/{applicationId}/sanction/reject")
    @Operation(summary = "Reject at CAM / sanction decision (CAM_REVIEWED or SANCTION_PENDING → REJECTED)")
    public ResponseEntity<ApplicationResponse> rejectPostCredit(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) PostCreditRejectRequest body) {
        String remarks = body != null ? body.getRemarks() : null;
        return ResponseEntity.ok(flowService.rejectAfterCamReview(applicationId, remarks));
    }

    @PostMapping("/{applicationId}/ready-for-disbursement")
    @Operation(summary = "After eSign complete — gate before disburse (→ READY_FOR_DISBURSEMENT)")
    public ResponseEntity<ApplicationResponse> markReadyForDisbursement(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(flowService.markReadyForDisbursement(applicationId));
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
