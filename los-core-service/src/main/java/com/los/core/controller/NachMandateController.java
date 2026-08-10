package com.los.core.controller;

import com.los.core.model.entity.NachMandate;
import com.los.core.service.nach.NachMandateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nach")
@RequiredArgsConstructor
@Tag(name = "NACH Mandate", description = "NACH/e-NACH mandate creation, registration, and management")
public class NachMandateController {

    private final NachMandateService nachMandateService;

    @PostMapping
    @Operation(summary = "Create a NACH mandate for EMI auto-debit")
    public ResponseEntity<NachMandate> create(
            @RequestParam UUID applicationId,
            @RequestParam UUID customerId,
            @RequestParam String bankName,
            @RequestParam String accountNumber,
            @RequestParam String ifscCode,
            @RequestParam String accountHolderName,
            @RequestParam BigDecimal maxAmount,
            @RequestParam int tenureMonths) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nachMandateService.createMandate(applicationId, customerId,
                        bankName, accountNumber, ifscCode, accountHolderName, maxAmount, tenureMonths));
    }

    @PostMapping("/{mandateId}/register")
    @Operation(summary = "Register mandate after eSign (UMRN assigned)")
    public ResponseEntity<NachMandate> register(
            @PathVariable UUID mandateId,
            @RequestParam String umrn,
            @RequestParam String esignTransactionId) {
        return ResponseEntity.ok(nachMandateService.registerMandate(mandateId, umrn, esignTransactionId));
    }

    @PostMapping("/{mandateId}/cancel")
    @Operation(summary = "Cancel a NACH mandate")
    public ResponseEntity<NachMandate> cancel(
            @PathVariable UUID mandateId,
            @RequestParam String reason) {
        return ResponseEntity.ok(nachMandateService.cancelMandate(mandateId, reason));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get mandates for an application")
    public ResponseEntity<List<NachMandate>> getMandates(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(nachMandateService.getMandates(applicationId));
    }

    @GetMapping("/application/{applicationId}/active")
    @Operation(summary = "Get active mandate for an application")
    public ResponseEntity<NachMandate> getActiveMandate(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(nachMandateService.getActiveMandate(applicationId));
    }
}
