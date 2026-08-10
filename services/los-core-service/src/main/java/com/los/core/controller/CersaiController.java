package com.los.core.controller;

import com.los.core.model.dto.request.CersaiRegistrationRequest;
import com.los.core.model.entity.CersaiRegistration;
import com.los.core.service.collateral.CersaiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cersai")
@RequiredArgsConstructor
@Tag(name = "CERSAI", description = "CERSAI security interest search and registration")
public class CersaiController {

    private final CersaiService cersaiService;

    @PostMapping("/search")
    @Operation(summary = "Search CERSAI for existing security interests on an asset")
    public ResponseEntity<CersaiRegistration> searchCharges(
            @RequestParam String assetType,
            @RequestParam String assetIdentifier,
            @RequestParam UUID applicationId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cersaiService.searchCharges(assetType, assetIdentifier, applicationId));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a security interest with CERSAI")
    public ResponseEntity<CersaiRegistration> register(@RequestBody CersaiRegistrationRequest request) {
        if (request.getApplicationId() == null) {
            throw new IllegalArgumentException("applicationId is required.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cersaiService.registerSecurityInterest(
                        request.getApplicationId(),
                        request.getCollateralValuationId(),
                        request));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get latest CERSAI registration for an application")
    public ResponseEntity<CersaiRegistration> getByApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(cersaiService.getRegistration(applicationId));
    }

    @GetMapping("/application/{applicationId}/history")
    @Operation(summary = "List all CERSAI search and registration records for an application")
    public ResponseEntity<List<CersaiRegistration>> listByApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(cersaiService.listRegistrations(applicationId));
    }
}
