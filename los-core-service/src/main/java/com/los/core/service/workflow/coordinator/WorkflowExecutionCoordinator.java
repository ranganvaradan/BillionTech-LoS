package com.los.core.service.workflow.coordinator;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import com.los.core.service.flow.step.StepExecutionRecordingService;
import com.los.core.service.flow.step.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Incremental bridge between {@code workflow_configs} and {@link com.los.core.service.flow.step.IStepExecutor}
 * runs (via {@link StepExecutionRecordingService}). Not a workflow instance engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionCoordinator {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final StepExecutionRecordingService stepExecutionRecordingService;
    private final WorkflowResolutionOptions workflowResolutionOptions;
    private final ApplicationWorkflowResolver applicationWorkflowResolver;
    @Value("${los.workflow.step-validation-strict:false}")
    private boolean stepValidationStrict;

    /**
     * Ordered {@link com.los.core.service.flow.step.FlowStepType} keys derived from the active workflow for this application
     * (KYC + bureau by default; eSign/disburse are post-sanction and not in KYC JSON today).
     */
    public List<String> getResolvedOrderedFlowStepTypes(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        return loadConfig(app)
                .map(c -> WorkflowFlowStepOrderResolver.resolve(c, workflowResolutionOptions))
                .orElseGet(List::of);
    }

    /**
     * Runs a single flow step with optional strict validation against the active workflow configuration.
     */
    public StepResult executeFlowStepForApplication(UUID applicationId, String flowStepType, Map<String, Object> context) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!WorkflowFlowStepOrderResolver.isPostSanctionFlowStep(flowStepType)
                && app.getWorkflowId() == null) {
            throw com.los.core.service.workflow.ApplicationConfigurationAuthority.notPinned(app);
        }
        if (!WorkflowFlowStepOrderResolver.isPostSanctionFlowStep(flowStepType)
                && app.getWorkflowId() != null) {
            // P1: refuse to execute a flow step against a pinned Workflow Version whose content
            // was mutated after resolve — immutable-published-version invariant.
            applicationWorkflowResolver.resolveForExecution(app);
        }
        assertFlowStepAllowedIfStrict(app, flowStepType);
        return stepExecutionRecordingService.executeWithRecording(flowStepType, applicationId, context);
    }

    private void assertFlowStepAllowedIfStrict(LoanApplication app, String flowStepType) {
        if (!stepValidationStrict) {
            return;
        }
        if (WorkflowFlowStepOrderResolver.isPostSanctionFlowStep(flowStepType)) {
            return;
        }
        Optional<WorkflowConfig> cfg = loadConfig(app);
        if (cfg.isEmpty()) {
            log.debug("Strict workflow validation: no active workflow for app {} — allowing {}", app.getId(), flowStepType);
            return;
        }
        List<String> order = WorkflowFlowStepOrderResolver.resolve(cfg.get(), workflowResolutionOptions);
        if (order.contains(flowStepType)) {
            return;
        }
        throw new BusinessRuleException(
                "Flow step " + flowStepType + " is not enabled in the active workflow for this product",
                "WORKFLOW_STEP_NOT_IN_CONFIG",
                flowStepType,
                Map.of("applicationId", app.getId(), "resolvedOrder", order, "workflowId", cfg.get().getId()));
    }

    private Optional<WorkflowConfig> loadConfig(LoanApplication app) {
        if (app.getBorrowerType() == null || app.getLoanProduct() == null || app.getLoanProduct().isBlank()) {
            return Optional.empty();
        }
        return activeWorkflowConfigService.findActiveForApplication(app);
    }
}
