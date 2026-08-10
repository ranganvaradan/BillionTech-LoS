package com.los.core.controller;

import com.los.core.service.credit.CreditRulesEngine;
import com.los.core.service.workflow.SlaTatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tat")
@RequiredArgsConstructor
@Tag(name = "TAT & SLA Tracking", description = "Turnaround time monitoring, SLA tracking, and bottleneck identification")
public class TatController {

    private final SlaTatService slaTatService;

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get TAT summary for an application")
    public ResponseEntity<Map<String, Object>> getApplicationTat(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(slaTatService.getTatSummary(applicationId));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get TAT dashboard with aggregate metrics")
    public ResponseEntity<Map<String, Object>> getTatDashboard() {
        return ResponseEntity.ok(slaTatService.getTatDashboard());
    }

    @PostMapping("/set-sla/{applicationId}")
    @Operation(summary = "Set SLA deadline for an application's current step")
    public ResponseEntity<Map<String, String>> setSla(
            @PathVariable UUID applicationId,
            @RequestParam String stepName) {
        slaTatService.setSlaForStep(applicationId, stepName);
        return ResponseEntity.ok(Map.of("status", "SLA set", "applicationId", applicationId.toString(), "step", stepName));
    }
}
