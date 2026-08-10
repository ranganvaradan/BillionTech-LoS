package com.los.core.controller;

import com.los.core.service.credit.CreditRulesEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-rules")
@RequiredArgsConstructor
@Tag(name = "Credit Rules Engine", description = "Configurable credit policy evaluation and auto-approve thresholds")
public class CreditRulesController {

    private final CreditRulesEngine creditRulesEngine;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate a loan application against credit policy rules")
    public ResponseEntity<CreditRulesEngine.CreditEvaluation> evaluate(
            @RequestParam UUID applicationId,
            @RequestParam String loanProduct,
            @RequestParam int creditScore,
            @RequestParam BigDecimal monthlyIncome,
            @RequestParam(defaultValue = "0") BigDecimal existingEmi,
            @RequestParam BigDecimal requestedAmount,
            @RequestParam int requestedTenure,
            @RequestBody(required = false) Map<String, Object> additionalData) {
        return ResponseEntity.ok(creditRulesEngine.evaluate(
                applicationId, loanProduct, creditScore, monthlyIncome,
                existingEmi, requestedAmount, requestedTenure, additionalData));
    }

    @GetMapping("/policies")
    @Operation(summary = "Get available credit policies and thresholds")
    public ResponseEntity<Map<String, CreditRulesEngine.CreditPolicy>> getPolicies() {
        return ResponseEntity.ok(creditRulesEngine.getPolicies());
    }
}
