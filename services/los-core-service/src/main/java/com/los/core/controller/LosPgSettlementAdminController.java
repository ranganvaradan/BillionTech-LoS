package com.los.core.controller;

import com.los.core.model.dto.response.LosLoanPaymentInProgressResponse;
import com.los.core.payment.model.LosPgSettlementBatch;
import com.los.core.payment.service.LosLoanPaymentSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/pg-settlements")
@RequiredArgsConstructor
@Tag(name = "LOS PG settlements", description = "Settle PayU collections for LOS personal/term loans (PRUS/PIP)")
public class LosPgSettlementAdminController {

    private final LosLoanPaymentSettlementService settlementService;

    @GetMapping("/pip")
    @Operation(summary = "List open payment-in-progress lines from LOS loan PayU")
    public ResponseEntity<List<LosLoanPaymentInProgressResponse>> listOpenPip() {
        return ResponseEntity.ok(settlementService.listOpenPip());
    }

    @PostMapping("/batches")
    @Operation(summary = "Apply settlement batch — posts LMS repayments with UTR")
    public ResponseEntity<LosPgSettlementBatch> createBatch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        LocalDate settlementDate = LocalDate.parse(body.get("settlementDate").toString());
        String utr = body.get("settlementUtr").toString();
        @SuppressWarnings("unchecked")
        List<String> pipIdStrings = (List<String>) body.get("pipIds");
        List<UUID> pipIds = pipIdStrings.stream().map(UUID::fromString).collect(Collectors.toList());
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;
        return ResponseEntity.ok(settlementService.createAndApplyBatch(
                settlementDate, utr, pipIds, userId, remarks));
    }
}
