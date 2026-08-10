package com.los.core.controller;

import com.los.core.model.dto.request.BorrowerLoanPayuInitiateRequest;
import com.los.core.model.dto.request.BorrowerRepaymentRequest;
import com.los.core.model.dto.response.BorrowerDashboardResponse;
import com.los.core.model.dto.response.BorrowerLoanAccountResponse;
import com.los.core.model.dto.response.BorrowerNotificationItemResponse;
import com.los.core.model.dto.response.BorrowerRepaymentScheduleItemResponse;
import com.los.core.model.dto.response.BorrowerServicingDataResponse;
import com.los.core.model.dto.response.BorrowerStatementLineResponse;
import com.los.core.model.dto.response.BorrowerTransactionItemResponse;
import com.los.core.service.borrower.BorrowerPortalService;
import com.los.core.payment.service.LosLoanPayuPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrower")
@RequiredArgsConstructor
@Tag(name = "Borrower", description = "Dashboard and post-disbursement (demo data where noted)")
public class BorrowerResourceController {

    private final BorrowerPortalService borrowerPortalService;
    private final LosLoanPayuPaymentService losLoanPayuPaymentService;

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

    @GetMapping("/loans/{loanId}/account")
    @Operation(summary = "Loan account summary after disbursement (LMS-backed; loanId = applicationId)")
    public ResponseEntity<BorrowerLoanAccountResponse> loanAccount(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.loanAccount(uid, loanId));
    }

    @GetMapping("/loans/{loanId}/repayment-schedule")
    @Operation(summary = "Repayment schedule from LMS with indicative fallback (loanId = applicationId)")
    public ResponseEntity<BorrowerServicingDataResponse<BorrowerRepaymentScheduleItemResponse>> repayment(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.repaymentSchedule(uid, loanId));
    }

    @GetMapping("/loans/{loanId}/statement")
    @Operation(summary = "Account statement from LMS; falls back to real disbursal + repayments (loanId = applicationId)")
    public ResponseEntity<BorrowerServicingDataResponse<BorrowerStatementLineResponse>> statement(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.statement(uid, loanId));
    }

    @GetMapping("/loans/{loanId}/transactions")
    @Operation(summary = "Transaction list from LMS payment history with local fallback (loanId = applicationId)")
    public ResponseEntity<BorrowerServicingDataResponse<BorrowerTransactionItemResponse>> transactions(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.transactions(uid, loanId));
    }

    @PostMapping("/loans/{loanId}/repay")
    @Operation(summary = "Make a repayment on a disbursed loan (posts to LMS; loanId = applicationId)")
    public ResponseEntity<BorrowerLoanAccountResponse> repay(
            @PathVariable UUID loanId,
            @Valid @RequestBody BorrowerRepaymentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.makeRepayment(uid, loanId, request.getAmount()));
    }

    @PostMapping("/loans/{loanId}/payments/payu/initiate")
    @Operation(summary = "Initiate PayU repayment for a disbursed LOS loan (personal/term — not invoice discounting)")
    public ResponseEntity<Map<String, Object>> initiatePayu(
            @PathVariable UUID loanId,
            @Valid @RequestBody BorrowerLoanPayuInitiateRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(losLoanPayuPaymentService.initiatePayu(uid, loanId, request.getAmount()));
    }
}
