package com.los.core.controller;

import com.los.core.model.dto.response.IntegrationProviderMatrixRow;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.integration.IntegrationProviderMatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "External provider integration management")
@RequiredArgsConstructor
public class IntegrationController {

    private final IIntegrationRouterService integrationRouterService;
    private final IntegrationProviderMatrixService integrationProviderMatrixService;

    @PostMapping("/esign/{applicationId}")
    @Operation(summary = "Initiate eSign for an application (body: documentKey, signerInfo, optional esignStepType)")
    public ResponseEntity<IIntegrationRouterService.ESignRouteResult> initiateESign(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> p = body == null ? new HashMap<>() : new HashMap<>(body);
        if (p.get("documentKey") == null && p.get("documentStorageKey") == null) {
            p.put("documentKey", "KFS_AGREEMENT");
        }
        if (p.get("documentKey") == null && p.get("documentStorageKey") != null) {
            p.put("documentKey", p.get("documentStorageKey").toString());
        }
        if (!p.containsKey("signerInfo")) {
            p.put("signerInfo", Map.of());
        }
        return ResponseEntity.ok(integrationRouterService.routeESignRequest(applicationId, p));
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

    @GetMapping("/provider-matrix")
    @Operation(summary = "Integration provider routing matrix (read-only, from aggregator_routing)")
    public ResponseEntity<List<IntegrationProviderMatrixRow>> providerMatrix() {
        return ResponseEntity.ok(integrationProviderMatrixService.listAllRows());
    }

    @PostMapping("/webhook")
    @Operation(summary = "Central webhook entry (provider in body as providerName, or X-Provider-Name header)")
    public ResponseEntity<Map<String, Object>> integrationWebhook(
            @RequestHeader(value = "X-Provider-Name", required = false) String headerProvider,
            @RequestBody Map<String, Object> body) {
        if (body == null) {
            body = new java.util.HashMap<>();
        } else {
            body = new java.util.HashMap<>(body);
        }
        String p = headerProvider;
        if (p == null || p.isBlank()) {
            p = (String) body.getOrDefault("providerName", body.get("provider"));
        }
        if (p == null || p.isBlank()) {
            p = "EMSIGNER";
        }
        return ResponseEntity.ok(integrationRouterService.handleWebhookCallback(p, body));
    }
}
