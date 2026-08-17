package com.los.core.controller;

import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.service.workflow.IWorkflowEngineService;
import com.los.core.service.workflow.WorkflowEngineServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflows", description = "KYC workflow configuration management")
public class WorkflowController {

    private final IWorkflowEngineService workflowEngineService;
    private final WorkflowEngineServiceImpl workflowEngineImpl;

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

    @PostMapping("/{workflowId}/versions")
    @Operation(summary = "Create a new immutable Workflow Version in the same family (does not mutate the source version)")
    public ResponseEntity<WorkflowConfigResponse> createNewVersion(
            @PathVariable UUID workflowId,
            @Valid @RequestBody WorkflowConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowEngineService.createNewVersion(workflowId, request));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active workflow for borrower type, loan product, and intake segment (defaults to BORROWER)")
    public ResponseEntity<WorkflowConfigResponse> getActive(
            @RequestParam BorrowerType borrowerType,
            @RequestParam String loanProduct,
            @RequestParam(required = false, defaultValue = "BORROWER") IntakeSegment intakeSegment) {
        return ResponseEntity.ok(workflowEngineService.getActiveWorkflow(borrowerType, loanProduct, intakeSegment));
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

    @DeleteMapping("/{workflowId}")
    @Operation(summary = "Delete an inactive workflow configuration (active workflows must be deactivated first)")
    public ResponseEntity<Void> delete(@PathVariable UUID workflowId) {
        workflowEngineService.deleteWorkflow(workflowId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workflowId}/parallel-plan")
    @Operation(summary = "BR-6.2: Get parallel execution plan for a workflow")
    public ResponseEntity<Map<String, Object>> getParallelPlan(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowEngineImpl.getParallelExecutionPlan(workflowId));
    }
}
