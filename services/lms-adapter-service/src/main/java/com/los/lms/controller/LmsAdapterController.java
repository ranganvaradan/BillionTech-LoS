package com.los.lms.controller;

import com.los.lms.dto.*;
import com.los.lms.service.LmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@Tag(name = "LMS Adapter", description = "Loan Management System integration")
public class LmsAdapterController {

    private final LmsService lmsService;

    @PostMapping("/handover")
    @Operation(summary = "Handover disbursed loan to LMS for servicing")
    public ResponseEntity<LoanHandoverResponse> handoverLoan(@RequestBody LoanHandoverRequest request) {
        log.info("Loan handover request for application: {}", request.getApplicationNumber());
        return ResponseEntity.ok(lmsService.handoverLoan(request));
    }

    @GetMapping("/status/{applicationNumber}")
    @Operation(summary = "Get loan account summary from LMS")
    public ResponseEntity<LoanAccountSummary> getAccountSummary(@PathVariable String applicationNumber) {
        return ResponseEntity.ok(lmsService.getAccountSummary(applicationNumber));
    }

    @GetMapping("/schedule/{applicationNumber}")
    @Operation(summary = "Get full repayment schedule for a loan")
    public ResponseEntity<RepaymentScheduleResponse> getRepaymentSchedule(@PathVariable String applicationNumber) {
        return ResponseEntity.ok(lmsService.getRepaymentSchedule(applicationNumber));
    }

    @PostMapping("/callback/repayment")
    @Operation(summary = "Receive repayment callback from LMS")
    public ResponseEntity<Map<String, Object>> repaymentCallback(@RequestBody RepaymentCallbackRequest callback) {
        log.info("Repayment callback for: {} installment #{}", callback.getApplicationNumber(), callback.getInstallmentNumber());
        return ResponseEntity.ok(lmsService.processRepaymentCallback(callback));
    }

    @GetMapping("/payments/{applicationNumber}")
    @Operation(summary = "Get payment history for a loan")
    public ResponseEntity<List<RepaymentCallbackRequest>> getPaymentHistory(@PathVariable String applicationNumber) {
        return ResponseEntity.ok(lmsService.getPaymentHistory(applicationNumber));
    }

    @GetMapping("/accounts")
    @Operation(summary = "Get all active loan accounts")
    public ResponseEntity<List<LoanAccountSummary>> getAllAccounts() {
        return ResponseEntity.ok(lmsService.getAllAccounts());
    }
}
