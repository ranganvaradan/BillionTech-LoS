package com.los.core.controller;

import com.los.core.model.dto.response.BorrowerDashboardResponse;
import com.los.core.model.dto.response.BorrowerNotificationItemResponse;
import com.los.core.model.dto.response.BorrowerRepaymentScheduleItemResponse;
import com.los.core.model.dto.response.BorrowerStatementLineResponse;
import com.los.core.model.dto.response.BorrowerTransactionItemResponse;
import com.los.core.service.borrower.BorrowerPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrower")
@RequiredArgsConstructor
@Tag(name = "Borrower", description = "Dashboard and post-disbursement (demo data where noted)")
public class BorrowerResourceController {

    private final BorrowerPortalService borrowerPortalService;

    @GetMapping("/dashboard")
    @Operation(summary = "Borrower dashboard summary")
    public ResponseEntity<BorrowerDashboardResponse> dashboard(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.dashboard(uid));
    }

    @GetMapping("/notifications")
    @Operation(summary = "In-app notifications derived from application status (no email/SMS)")
    public ResponseEntity<List<BorrowerNotificationItemResponse>> notifications(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.notifications(uid));
    }

    @GetMapping("/loans/{loanId}/repayment-schedule")
    @Operation(summary = "Repayment schedule (demo schedule until LMS integration; loanId = applicationId)")
    public ResponseEntity<List<BorrowerRepaymentScheduleItemResponse>> repayment(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.repaymentScheduleDemo(uid, loanId));
    }

    @GetMapping("/loans/{loanId}/statement")
    @Operation(summary = "Account statement (demo; loanId = applicationId)")
    public ResponseEntity<List<BorrowerStatementLineResponse>> statement(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.statementDemo(uid, loanId));
    }

    @GetMapping("/loans/{loanId}/transactions")
    @Operation(summary = "Transaction list (demo; loanId = applicationId)")
    public ResponseEntity<List<BorrowerTransactionItemResponse>> transactions(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.transactionsDemo(uid, loanId));
    }
}
