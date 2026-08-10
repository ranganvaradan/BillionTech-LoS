package com.los.core.controller;

import com.los.core.service.esign.EsignEmsignerCompletionService;
import com.los.core.service.esign.EmsignerDebugService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/emsigner")
@RequiredArgsConstructor
@Tag(name = "Internal EmSigner", description = "Internal utilities for emSigner demo completion")
public class InternalEmsignerController {

    private final EsignEmsignerCompletionService completionService;
    private final EmsignerDebugService debugService;

    @GetMapping("/config")
    @Operation(summary = "Read-only EMSIGNER runtime config diagnostics (masked)")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        return ResponseEntity.ok(debugService.debugConfig());
    }

    @PostMapping("/simulate-completion")
    @Operation(summary = "Simulate emSigner completion by WorkflowID (internal demo utility)")
    public ResponseEntity<Map<String, Object>> simulateCompletion(@RequestBody Map<String, Object> body) {
        String workflowId = body != null && body.get("WorkflowID") != null
                ? String.valueOf(body.get("WorkflowID")).trim()
                : "";
        if (workflowId.isBlank() && body != null && body.get("workflowId") != null) {
            workflowId = String.valueOf(body.get("workflowId")).trim();
        }
        try {
            Map<String, Object> r = completionService.simulateCompletionByWorkflowId(workflowId);
            boolean ok = Boolean.TRUE.equals(r.get("success"));
            return ok ? ResponseEntity.ok(r) : ResponseEntity.badRequest().body(r);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "workflowId", workflowId,
                    "message", ex.getMessage()));
        }
    }
}
