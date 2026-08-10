package com.los.core.controller;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.AaConsent;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.aa.AccountAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aa")
@RequiredArgsConstructor
@Tag(name = "Account Aggregator", description = "RBI AA-compliant consent management and financial data fetch")
public class AccountAggregatorController {

    private final AccountAggregatorService aaService;
    private final LoanApplicationRepository loanApplicationRepository;

    @PostMapping("/consent")
    @Operation(summary = "Create an AA consent request")
    public ResponseEntity<AaConsent> createConsent(
            @RequestParam UUID applicationId,
            @RequestParam UUID customerId,
            @RequestParam(required = false) List<String> fiTypes,
            @RequestParam(required = false) String aaName,
            @RequestBody(required = false) Map<String, Object> purpose) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aaService.createConsentRequest(applicationId, customerId, fiTypes, aaName, purpose));
    }

    @PostMapping("/consent/application/{applicationId}/initiate")
    @Operation(summary = "Initiate AA consent for application — resolves customer automatically")
    public ResponseEntity<AaConsent> initiateForApplication(
            @PathVariable UUID applicationId,
            @RequestParam(required = false) List<String> fiTypes,
            @RequestParam(required = false) String aaName,
            @RequestBody(required = false) Map<String, Object> purpose) {
        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        UUID customerId = application.getCustomerId();
        if (customerId == null) {
            throw new IllegalArgumentException("Application has no linked customer id: " + applicationId);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aaService.createConsentRequest(applicationId, customerId, fiTypes, aaName, purpose));
    }

    @PostMapping("/consent/{consentHandle}/approve")
    @Operation(summary = "Process consent approval callback from AA")
    public ResponseEntity<AaConsent> approveConsent(
            @PathVariable String consentHandle,
            @RequestParam String consentId) {
        return ResponseEntity.ok(aaService.approveConsent(consentHandle, consentId));
    }

    @PostMapping("/consent/{consentHandle}/fetch")
    @Operation(summary = "Fetch financial data from AA after consent approval")
    public ResponseEntity<AaConsent> fetchData(@PathVariable String consentHandle) {
        return ResponseEntity.ok(aaService.fetchData(consentHandle));
    }

    @PostMapping("/consent/{consentId}/revoke")
    @Operation(summary = "Revoke a consent")
    public ResponseEntity<AaConsent> revokeConsent(
            @PathVariable UUID consentId,
            @RequestParam String reason) {
        return ResponseEntity.ok(aaService.revokeConsent(consentId, reason));
    }

    @GetMapping("/consent/application/{applicationId}")
    @Operation(summary = "Get consents for an application")
    public ResponseEntity<List<AaConsent>> getConsents(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(aaService.getConsents(applicationId));
    }

    @GetMapping("/consent/{consentHandle}")
    @Operation(summary = "Get consent by handle")
    public ResponseEntity<AaConsent> getByHandle(@PathVariable String consentHandle) {
        return ResponseEntity.ok(aaService.getByHandle(consentHandle));
    }

    @GetMapping("/consent/application/{applicationId}/active")
    @Operation(summary = "Check if application has active AA consent")
    public ResponseEntity<Map<String, Boolean>> hasActiveConsent(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(Map.of("hasActiveConsent", aaService.hasActiveConsent(applicationId)));
    }

    @PostMapping("/consent/callback")
    @Operation(summary = "Setu AA webhook callback for consent status and FI data notifications")
    public ResponseEntity<Void> handleAaCallback(@RequestBody Map<String, Object> callbackPayload) {
        aaService.handleAaCallback(callbackPayload);
        return ResponseEntity.ok().build();
    }
}
