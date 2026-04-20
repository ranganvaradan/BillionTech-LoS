package com.los.core.controller;

import com.los.core.model.entity.CoLendingAllocation;
import com.los.core.model.entity.CoLendingPartner;
import com.los.core.service.colending.CoLendingService;
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
 * BR-17: Co-Lending Module API.
 */
@RestController
@RequestMapping("/api/v1/co-lending")
@RequiredArgsConstructor
@Tag(name = "Co-Lending", description = "Co-lending partner management, apportionment, and MIS")
public class CoLendingController {

    private final CoLendingService coLendingService;

    @PostMapping("/partners")
    @Operation(summary = "BR-17.1: Create a co-lending partner")
    public ResponseEntity<CoLendingPartner> createPartner(@RequestBody CoLendingPartner partner) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coLendingService.createPartner(partner));
    }

    @GetMapping("/partners")
    @Operation(summary = "BR-17.1: List active co-lending partners")
    public ResponseEntity<List<CoLendingPartner>> listPartners() {
        return ResponseEntity.ok(coLendingService.listActivePartners());
    }

    @PutMapping("/partners/{partnerId}")
    @Operation(summary = "BR-17.1: Update a co-lending partner")
    public ResponseEntity<CoLendingPartner> updatePartner(
            @PathVariable UUID partnerId,
            @RequestBody CoLendingPartner partner) {
        return ResponseEntity.ok(coLendingService.updatePartner(partnerId, partner));
    }

    @PostMapping("/apportion")
    @Operation(summary = "BR-17.2: Calculate co-lending apportionment")
    public ResponseEntity<List<CoLendingAllocation>> apportion(
            @RequestParam UUID applicationId,
            @RequestParam UUID partnerId,
            @RequestParam BigDecimal partnerSharePercent) {
        return ResponseEntity.ok(coLendingService.calculateApportionment(applicationId, partnerId, partnerSharePercent));
    }

    @GetMapping("/allocations/{applicationId}")
    @Operation(summary = "Get co-lending allocations for an application")
    public ResponseEntity<List<CoLendingAllocation>> getAllocations(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(coLendingService.getAllocationsByApplication(applicationId));
    }

    @PostMapping("/disburse/{applicationId}")
    @Operation(summary = "BR-17.3: Trigger co-lending disbursement")
    public ResponseEntity<Map<String, Object>> disburse(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(coLendingService.triggerCoLendingDisbursement(applicationId));
    }

    @GetMapping("/settlement/{partnerId}")
    @Operation(summary = "BR-17.4: Get settlement report for a partner")
    public ResponseEntity<Map<String, Object>> settlementReport(@PathVariable UUID partnerId) {
        return ResponseEntity.ok(coLendingService.getSettlementReport(partnerId));
    }

    @GetMapping("/mis")
    @Operation(summary = "BR-17.5: Get co-lending MIS report")
    public ResponseEntity<Map<String, Object>> mis() {
        return ResponseEntity.ok(coLendingService.getCoLendingMis());
    }
}
