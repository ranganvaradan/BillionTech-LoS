package com.los.core.controller;

import com.los.core.model.dto.response.TransactionResponse;
import com.los.core.service.transaction.ITransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Disbursement and repayment tracking")
public class TransactionController {

    private final ITransactionService transactionService;

    @PostMapping("/{applicationId}/disburse")
    @Operation(summary = "Trigger loan disbursement")
    public ResponseEntity<TransactionResponse> disburse(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", Map.of());
        return ResponseEntity.ok(transactionService.triggerDisbursement(applicationId, amount, metadata));
    }

    @PostMapping("/{applicationId}/repayment")
    @Operation(summary = "Record a loan repayment")
    public ResponseEntity<TransactionResponse> recordRepayment(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String utrNumber = (String) body.get("utrNumber");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", Map.of());
        return ResponseEntity.ok(transactionService.recordRepayment(applicationId, amount, utrNumber, metadata));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get transaction history for an application")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @PathVariable UUID applicationId, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(applicationId, pageable));
    }

    @GetMapping("/{applicationId}/outstanding")
    @Operation(summary = "Get outstanding balance for an application")
    public ResponseEntity<Map<String, Object>> getOutstanding(@PathVariable UUID applicationId) {
        BigDecimal outstanding = transactionService.getOutstandingBalance(applicationId);
        return ResponseEntity.ok(Map.of("applicationId", applicationId, "outstandingBalance", outstanding));
    }
}
