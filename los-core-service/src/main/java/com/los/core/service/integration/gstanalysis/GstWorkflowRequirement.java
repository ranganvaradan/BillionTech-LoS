package com.los.core.service.integration.gstanalysis;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow-gated GST analysis helpers (prepare-gate only — upload success not required at submit).
 * W1: uses application-resolved Workflow Version only.
 */
@Component
@RequiredArgsConstructor
public class GstWorkflowRequirement {

    private final ApplicationWorkflowResolver applicationWorkflowResolver;
    private final KycStepResultRepository kycStepResultRepository;

    public boolean isOnActiveWorkflow(LoanApplication app) {
        if (app == null) {
            return false;
        }
        try {
            WorkflowConfig workflow = applicationWorkflowResolver.requireConfig(app);
            return workflowHasStep(workflow.getSteps());
        } catch (BusinessRuleException e) {
            if (com.los.core.service.workflow.ApplicationConfigurationAuthority.isUnconfiguredReason(e.getReason())) {
                return false;
            }
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMandatoryInActiveWorkflow(LoanApplication app) {
        if (app == null) {
            return false;
        }
        try {
            WorkflowConfig workflow = applicationWorkflowResolver.requireConfig(app);
            return workflowHasMandatory(workflow.getSteps());
        } catch (BusinessRuleException e) {
            if (com.los.core.service.workflow.ApplicationConfigurationAuthority.isUnconfiguredReason(e.getReason())) {
                return false;
            }
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean workflowHasStep(List<Map<String, Object>> steps) {
        return findStep(steps) != null;
    }

    public static boolean workflowHasMandatory(List<Map<String, Object>> steps) {
        Map<String, Object> step = findStep(steps);
        if (step == null) {
            return false;
        }
        Object mandatory = step.get("mandatory");
        if (mandatory == null) {
            return true;
        }
        if (mandatory instanceof Boolean b) {
            return b;
        }
        return !"false".equalsIgnoreCase(String.valueOf(mandatory).trim());
    }

    private static Map<String, Object> findStep(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        for (Map<String, Object> step : steps) {
            if (step == null) {
                continue;
            }
            Object name = step.get("step");
            if (name == null) {
                continue;
            }
            if (KycStepType.GST_ANALYSIS.name().equalsIgnoreCase(String.valueOf(name).trim())) {
                return step;
            }
        }
        return null;
    }

    public boolean hasSuccessfulReport(UUID applicationId) {
        if (applicationId == null) {
            return false;
        }
        return kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.GST_ANALYSIS)
                .filter(r -> r.getOutcome() == StepOutcome.SUCCESS || r.isOverridden())
                .filter(r -> {
                    Map<String, Object> p = r.getParsedData();
                    return p != null && "REPORT".equalsIgnoreCase(String.valueOf(p.get("phase")));
                })
                .isPresent();
    }
}
