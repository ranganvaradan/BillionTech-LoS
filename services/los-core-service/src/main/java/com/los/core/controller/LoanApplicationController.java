package com.los.core.controller;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.AiLosOpenRequest;
import com.los.core.model.dto.request.ManualBureauRequest;
import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.request.ValidateIdentityRequest;
import com.los.core.model.dto.response.AiLosOpenResponse;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.exception.ForbiddenException;
import com.los.core.model.dto.request.DeleteApplicationRequest;
import com.los.core.model.dto.response.ApplicationDeletionPreviewResponse;
import com.los.core.service.loan.ApplicationReviewService;
import com.los.core.service.borrower.BorrowerIntakeDelegationService;
import com.los.core.service.loan.ApplicationDeletionService;
import com.los.core.service.loan.ILoanApplicationService;
import com.los.core.model.dto.request.ReviewNotesRequest;
import com.los.core.service.loan.WorkflowRoleGuard;
import com.los.core.service.integration.AiLosIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Loan Applications", description = "Loan application CRUD and lifecycle management")
public class LoanApplicationController {

    private final ILoanApplicationService loanApplicationService;
    private final AiLosIntegrationService aiLosIntegrationService;
    private final ApplicationDeletionService applicationDeletionService;
    private final BorrowerIntakeDelegationService borrowerIntakeDelegationService;
    private final ApplicationReviewService applicationReviewService;
    private final WorkflowRoleGuard workflowRoleGuard;
    private final com.los.core.service.integration.gstanalysis.GstAnalysisService gstAnalysisService;

    private static final java.util.Set<String> MANUAL_BUREAU_ALLOWED_ROLES = java.util.Set.of(
            "ADMIN",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER",
            "CREDIT_ANALYST"
    );

    /** Staff who run underwriting but are not credit managers — can save scorecard OTHER/GST fields only. */
    private static final java.util.Set<String> SCORECARD_INPUT_ALLOWED_ROLES = java.util.Set.of(
            "ADMIN",
            "ADMINISTRATOR",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER",
            "CREDIT_ANALYST",
            "OPERATIONS",
            "BRANCH_VERIFIER",
            "KYC_REVIEWER",
            "RISK_MANAGER"
    );

    @PostMapping
    @Operation(summary = "Create a new loan application")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        workflowRoleGuard.requireCreateApplicationRole(userRole);
        UUID actingUser = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanApplicationService.createApplication(request, actingUser, userRole));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get application by ID")
    public ResponseEntity<ApplicationResponse> get(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @GetMapping
    @Operation(summary = "List applications with optional filters")
    public ResponseEntity<Page<ApplicationResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String borrowerType,
            @RequestParam(required = false) String intakeSegment,
            Pageable pageable) {
        return ResponseEntity.ok(loanApplicationService.listApplications(status, borrowerType, intakeSegment, pageable));
    }

    @PutMapping("/{applicationId}")
    @Operation(summary = "Update an existing application")
    public ResponseEntity<ApplicationResponse> update(
            @PathVariable UUID applicationId,
            @RequestBody UpdateApplicationRequest request) {
        return ResponseEntity.ok(loanApplicationService.updateApplication(applicationId, request));
    }

    @PostMapping("/validate-identity")
    @Operation(summary = "Check email/mobile/PAN/GSTIN for duplicate use before submit")
    public ResponseEntity<Map<String, Object>> validateIdentity(@RequestBody ValidateIdentityRequest request) {
        loanApplicationService.validateIdentity(request);
        return ResponseEntity.ok(Map.of("valid", true));
    }

    @PostMapping("/{applicationId}/transition")
    @Operation(summary = "Transition application to a new status")
    public ResponseEntity<ApplicationResponse> transitionStatus(
            @PathVariable UUID applicationId,
            @RequestParam ApplicationStatus newStatus,
            @RequestParam(required = false) String remarks) {
        return ResponseEntity.ok(loanApplicationService.transitionStatus(applicationId, newStatus, remarks));
    }

    @PostMapping("/{applicationId}/bureau/manual")
    @Operation(summary = "Save manual bureau score and optional report document reference")
    public ResponseEntity<ApplicationResponse> saveManualBureau(
            @PathVariable UUID applicationId,
            @RequestBody ManualBureauRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesCsv) {

        java.util.Set<String> roles = new java.util.HashSet<>();
        if (userRole != null && !userRole.isBlank()) roles.add(userRole.trim());
        if (userRolesCsv != null && !userRolesCsv.isBlank()) {
            for (String r : userRolesCsv.split(",")) {
                if (!r.isBlank()) roles.add(r.trim());
            }
        }

        boolean authorized = roles.stream().anyMatch(MANUAL_BUREAU_ALLOWED_ROLES::contains);
        if (!authorized) {
            throw new ForbiddenException("Forbidden: manual bureau override requires internal role");
        }

        UUID performedBy = userId != null ? UUID.fromString(userId) : null;

        return ResponseEntity.ok(
                loanApplicationService.setManualBureau(
                        applicationId,
                        request != null ? request.getManualBureauScore() : null,
                        request != null ? request.getManualBureauRemarks() : null,
                        request != null ? request.getManualBureauDocumentId() : null,
                        performedBy
                )
        );
    }

    @PostMapping("/{applicationId}/manual-credit-inputs")
    @Operation(summary = "Merge manual credit / KYC / bureau / geography fields without overwriting provider sources (uses financialInfo.creditControl)")
    public ResponseEntity<ApplicationResponse> saveManualCreditInputs(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) ManualCreditInputsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesCsv) {
        java.util.Set<String> roles = new java.util.HashSet<>();
        if (userRole != null && !userRole.isBlank()) roles.add(userRole.trim());
        if (userRolesCsv != null && !userRolesCsv.isBlank()) {
            for (String r : userRolesCsv.split(",")) {
                if (!r.isBlank()) roles.add(r.trim());
            }
        }
        if (!roles.stream().anyMatch(MANUAL_BUREAU_ALLOWED_ROLES::contains)) {
            throw new ForbiddenException("Forbidden: manual credit input requires internal role");
        }
        UUID performedBy = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(loanApplicationService.applyManualCreditInputs(applicationId, request, performedBy));
    }

    @PostMapping("/{applicationId}/scorecard-inputs")
    @Operation(summary = "Save scorecard OTHER/GST underwriting fields required before credit decision (broader staff roles than full manual credit input)")
    public ResponseEntity<ApplicationResponse> saveScorecardInputs(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) ManualCreditInputsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesCsv) {
        java.util.Set<String> roles = new java.util.HashSet<>();
        if (userRole != null && !userRole.isBlank()) roles.add(userRole.trim());
        if (userRolesCsv != null && !userRolesCsv.isBlank()) {
            for (String r : userRolesCsv.split(",")) {
                if (!r.isBlank()) roles.add(r.trim());
            }
        }
        if (!roles.stream().anyMatch(SCORECARD_INPUT_ALLOWED_ROLES::contains)) {
            throw new ForbiddenException("Forbidden: scorecard input requires authorized staff role");
        }
        UUID performedBy = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(loanApplicationService.applyScorecardInputs(applicationId, request, performedBy));
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get dashboard summary (counts by status)")
    public ResponseEntity<Map<String, Object>> dashboardSummary() {
        return ResponseEntity.ok(loanApplicationService.getDashboardSummary());
    }

    @PostMapping("/{applicationId}/ai-los/open")
    @Operation(summary = "Open AI LOS review/what-if for underwriting")
    public ResponseEntity<AiLosOpenResponse> openAiLos(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) AiLosOpenRequest request) {
        String returnUrl = request != null ? request.getReturnUrl() : null;
        String mode = request != null ? request.getMode() : null;
        return ResponseEntity.ok(aiLosIntegrationService.initiateOpen(applicationId, returnUrl, mode));
    }

    @GetMapping("/{applicationId}/deletion-preview")
    @Operation(summary = "Preview borrower application deletion warnings and PLP impact")
    public ResponseEntity<ApplicationDeletionPreviewResponse> deletionPreview(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(applicationDeletionService.previewDeletion(applicationId));
    }

    @DeleteMapping("/{applicationId}")
    @Operation(summary = "Permanently delete a borrower application and related records")
    public ResponseEntity<Void> deleteApplication(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) DeleteApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        UUID deletedBy = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        applicationDeletionService.deleteApplication(applicationId, request, deletedBy, userEmail, userRole);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{applicationId}/notify-borrower")
    @Operation(summary = "Save draft and invite borrower to complete intake on portal")
    public ResponseEntity<ApplicationResponse> notifyBorrower(
            @PathVariable UUID applicationId,
            @RequestParam(defaultValue = "1") int completedStep,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        workflowRoleGuard.requireIntakeRole(userRole);
        borrowerIntakeDelegationService.saveDraftAndNotifyBorrower(applicationId, completedStep);
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @PostMapping("/{applicationId}/review/accept")
    @Operation(summary = "Accept application for KYC processing (CO from PENDING_CREDIT_OFFICER; Admin may accept from BORROWER_SUBMITTED)")
    public ResponseEntity<ApplicationResponse> acceptBorrowerSubmission(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return ResponseEntity.ok(applicationReviewService.acceptForProcessing(applicationId, userRole));
    }

    @PostMapping("/{applicationId}/review/send-back")
    @Operation(summary = "Send application back to borrower (RM / Admin only; notes optional)")
    public ResponseEntity<ApplicationResponse> sendBackBorrowerSubmission(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) ReviewNotesRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        String notes = body != null ? body.getNotes() : null;
        return ResponseEntity.ok(applicationReviewService.sendBackToBorrower(applicationId, notes, userRole));
    }

    @PostMapping("/{applicationId}/review/hand-off-to-co")
    @Operation(summary = "RM hands off borrower submission to Credit Officer")
    public ResponseEntity<ApplicationResponse> handOffToCreditOfficer(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) ReviewNotesRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        String notes = body != null ? body.getNotes() : null;
        return ResponseEntity.ok(
                applicationReviewService.handOffToCreditOfficer(applicationId, notes, userRole));
    }

    @PostMapping("/{applicationId}/review/send-back-to-rm")
    @Operation(summary = "Credit Officer sends application back to Relationship Manager")
    public ResponseEntity<ApplicationResponse> sendBackToRelationshipManager(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) ReviewNotesRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        String notes = body != null ? body.getNotes() : null;
        return ResponseEntity.ok(
                applicationReviewService.sendBackToRelationshipManager(applicationId, notes, userRole));
    }

    @GetMapping("/{applicationId}/gst-analysis/status")
    @Operation(summary = "GST analysis upload/report status (admin)")
    public ResponseEntity<Map<String, Object>> gstAnalysisStatus(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(gstAnalysisService.statusBody(applicationId));
    }

    @PostMapping("/{applicationId}/gst-analysis/retry-upload")
    @Operation(summary = "Upload borrower GST PDFs to Karza (or retry after failure / send-back)")
    public ResponseEntity<com.los.core.model.dto.response.KycStepResultResponse> gstAnalysisRetryUpload(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID uid = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(gstAnalysisService.retryUpload(applicationId, uid));
    }

    @PostMapping("/{applicationId}/gst-analysis/generate-report")
    @Operation(summary = "Generate Karza GST analysis report after successful upload")
    public ResponseEntity<com.los.core.model.dto.response.KycStepResultResponse> gstAnalysisGenerateReport(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID uid = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(gstAnalysisService.generateReport(applicationId, uid));
    }
}
