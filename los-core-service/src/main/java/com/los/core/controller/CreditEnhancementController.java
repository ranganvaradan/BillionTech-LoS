package com.los.core.controller;

import com.los.core.service.credit.CreditEnhancementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * BR-4.7: Deviation matrix.
 * BR-4.10: Multi-bureau support.
 * BR-5.4: Bank statement analysis.
 */
@RestController
@RequestMapping("/api/v1/credit-enhanced")
@RequiredArgsConstructor
@Tag(name = "Credit Enhancements", description = "Deviation matrix, multi-bureau, bank statement analysis")
public class CreditEnhancementController {

    private final CreditEnhancementService creditEnhancementService;

    @PostMapping("/deviations/{applicationId}")
    @Operation(summary = "BR-4.7: Evaluate credit deviations")
    public ResponseEntity<Map<String, Object>> evaluateDeviations(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> creditData) {
        return ResponseEntity.ok(creditEnhancementService.evaluateDeviations(applicationId, creditData));
    }

    @PostMapping("/multi-bureau/{applicationId}")
    @Operation(summary = "BR-4.10: Multi-bureau credit pull")
    public ResponseEntity<Map<String, Object>> multiBureauPull(
            @PathVariable UUID applicationId,
            @RequestParam String panNumber,
            @RequestParam String customerName) {
        return ResponseEntity.ok(creditEnhancementService.multiBureauPull(applicationId, panNumber, customerName));
    }

    @PostMapping("/bank-analysis/{applicationId}")
    @Operation(summary = "BR-5.4: Analyze bank statements from AA data")
    public ResponseEntity<Map<String, Object>> analyzeBankStatements(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> aaData) {
        return ResponseEntity.ok(creditEnhancementService.analyzeBankStatements(applicationId, aaData));
    }
}
