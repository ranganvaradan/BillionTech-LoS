package com.los.core.controller;

import com.los.core.model.entity.CollateralValuation;
import com.los.core.service.collateral.CollateralValuationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-4.9: Collateral valuation API.
 */
@RestController
@RequestMapping("/api/v1/collateral")
@RequiredArgsConstructor
@Tag(name = "Collateral Valuation", description = "Collateral valuation management for secured lending")
public class CollateralController {

    private final CollateralValuationService valuationService;

    @PostMapping
    @Operation(summary = "Create a collateral valuation request")
    public ResponseEntity<CollateralValuation> createValuation(@RequestBody CollateralValuation valuation) {
        return ResponseEntity.status(HttpStatus.CREATED).body(valuationService.createValuation(valuation));
    }

    @PostMapping("/{valuationId}/complete")
    @Operation(summary = "Complete a collateral valuation with results")
    public ResponseEntity<CollateralValuation> completeValuation(
            @PathVariable UUID valuationId,
            @RequestParam BigDecimal marketValue,
            @RequestParam(required = false) BigDecimal forcedSaleValue,
            @RequestParam String valuerId,
            @RequestParam String valuerName) {
        return ResponseEntity.ok(valuationService.completeValuation(
                valuationId, marketValue, forcedSaleValue, valuerId, valuerName));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get all valuations for an application")
    public ResponseEntity<List<CollateralValuation>> listByApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(valuationService.getValuationsByApplication(applicationId));
    }

    @GetMapping("/ltv/{applicationId}")
    @Operation(summary = "Calculate LTV ratio for an application")
    public ResponseEntity<Map<String, Object>> calculateLtv(
            @PathVariable UUID applicationId,
            @RequestParam BigDecimal loanAmount) {
        return ResponseEntity.ok(valuationService.calculateLtv(applicationId, loanAmount));
    }
}
