package com.los.core.controller;

import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.workflow.IWorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflows", description = "KYC workflow configuration management")
public class WorkflowController {

    private final IWorkflowEngineService workflowEngineService;

    @PostMapping
    @Operation(summary = "Create a new workflow configuration")
    public ResponseEntity<WorkflowConfigResponse> create(@Valid @RequestBody WorkflowConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowEngineService.createWorkflow(request));
    }

    @PutMapping("/{workflowId}")
    @Operation(summary = "Update an existing workflow")
    public ResponseEntity<WorkflowConfigResponse> update(
            @PathVariable UUID workflowId,
            @Valid @RequestBody WorkflowConfigRequest request) {
        return ResponseEntity.ok(workflowEngineService.updateWorkflow(workflowId, request));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active workflow for borrower type and loan product")
    public ResponseEntity<WorkflowConfigResponse> getActive(
            @RequestParam BorrowerType borrowerType,
            @RequestParam String loanProduct) {
        return ResponseEntity.ok(workflowEngineService.getActiveWorkflow(borrowerType, loanProduct));
    }

    @GetMapping
    @Operation(summary = "List all workflow configurations")
    public ResponseEntity<List<WorkflowConfigResponse>> list() {
        return ResponseEntity.ok(workflowEngineService.listWorkflows());
    }

    @PostMapping("/{workflowId}/activate")
    @Operation(summary = "Activate a workflow (deactivates existing for same type/product)")
    public ResponseEntity<Void> activate(@PathVariable UUID workflowId) {
        workflowEngineService.activateWorkflow(workflowId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{workflowId}/deactivate")
    @Operation(summary = "Deactivate a workflow")
    public ResponseEntity<Void> deactivate(@PathVariable UUID workflowId) {
        workflowEngineService.deactivateWorkflow(workflowId);
        return ResponseEntity.ok().build();
    }
}
