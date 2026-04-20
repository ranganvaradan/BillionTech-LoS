package com.los.core.controller;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.enums.ApplicationStatus;
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

    @PostMapping
    @Operation(summary = "Create a new loan application")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID customerId = userId != null ? UUID.fromString(userId) : UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanApplicationService.createApplication(request, customerId));
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

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get dashboard summary (counts by status)")
    public ResponseEntity<Map<String, Object>> dashboardSummary() {
        return ResponseEntity.ok(loanApplicationService.getDashboardSummary());
    }
}
