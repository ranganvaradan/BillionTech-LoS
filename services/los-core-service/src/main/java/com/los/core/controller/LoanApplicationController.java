package com.los.core.controller;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.ManualBureauRequest;
import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.exception.ForbiddenException;
import com.los.core.service.loan.ILoanApplicationService;
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

    private static final java.util.Set<String> MANUAL_BUREAU_ALLOWED_ROLES = java.util.Set.of(
            "ADMIN",
            "CREDIT_MANAGER",
            "CREDIT_OFFICER",
            "CREDIT_ANALYST"
    );

    @PostMapping
    @Operation(summary = "Create a new loan application")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
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
            Pageable pageable) {
        return ResponseEntity.ok(loanApplicationService.listApplications(status, borrowerType, pageable));
    }

    @PutMapping("/{applicationId}")
    @Operation(summary = "Update an existing application")
    public ResponseEntity<ApplicationResponse> update(
            @PathVariable UUID applicationId,
            @RequestBody UpdateApplicationRequest request) {
        return ResponseEntity.ok(loanApplicationService.updateApplication(applicationId, request));
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

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get dashboard summary (counts by status)")
    public ResponseEntity<Map<String, Object>> dashboardSummary() {
        return ResponseEntity.ok(loanApplicationService.getDashboardSummary());
    }
}
