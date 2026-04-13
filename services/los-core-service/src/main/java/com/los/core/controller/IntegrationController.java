package com.los.core.controller;

import com.los.core.service.integration.IIntegrationRouterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "External provider integration management")
public class IntegrationController {

    private final IIntegrationRouterService integrationRouterService;

    @PostMapping("/esign/{applicationId}")
    @Operation(summary = "Initiate eSign for an application")
    public ResponseEntity<IIntegrationRouterService.ESignRouteResult> initiateESign(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body) {
        String documentKey = (String) body.get("documentKey");
        @SuppressWarnings("unchecked")
        Map<String, Object> signerInfo = (Map<String, Object>) body.getOrDefault("signerInfo", Map.of());
        return ResponseEntity.ok(integrationRouterService.routeESignRequest(applicationId, documentKey, signerInfo));
    }

    @PostMapping("/bureau")
    @Operation(summary = "Pull credit bureau report")
    public ResponseEntity<IIntegrationRouterService.BureauRouteResult> pullBureauReport(
            @RequestBody Map<String, Object> borrowerInfo) {
        return ResponseEntity.ok(integrationRouterService.routeBureauPull(borrowerInfo));
    }

    @GetMapping("/connectivity/{providerName}")
    @Operation(summary = "Test provider connectivity")
    public ResponseEntity<Map<String, Object>> testConnectivity(@PathVariable String providerName) {
        return ResponseEntity.ok(integrationRouterService.testConnectivity(providerName));
    }
}
