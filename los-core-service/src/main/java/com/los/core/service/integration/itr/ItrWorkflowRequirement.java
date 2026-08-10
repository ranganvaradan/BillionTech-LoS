package com.los.core.service.integration.itr;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.workflow.IWorkflowEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for workflow-gated ITR return-forms requirements.
 */
@Component
@RequiredArgsConstructor
public class ItrWorkflowRequirement {

    private final IWorkflowEngineService workflowEngine;
    private final KycStepResultRepository kycStepResultRepository;

    public boolean isMandatoryInActiveWorkflow(LoanApplication app) {
        if (app == null) {
            return false;
        }
        try {
            IntakeSegment segment = app.getIntakeSegment() != null ? app.getIntakeSegment() : IntakeSegment.BORROWER;
            var workflow = workflowEngine.getActiveWorkflow(app.getBorrowerType(), app.getLoanProduct(), segment);
            return workflowHasMandatoryItr(workflow != null ? workflow.getSteps() : null);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean workflowHasMandatoryItr(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (step == null) {
                continue;
            }
            Object name = step.get("step");
            if (name == null) {
                continue;
            }
            if (!KycStepType.ITR_RETURN_FORMS.name().equalsIgnoreCase(String.valueOf(name).trim())) {
                continue;
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
        return false;
    }

    public boolean hasSuccessfulPull(UUID applicationId) {
        if (applicationId == null) {
            return false;
        }
        return kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.ITR_RETURN_FORMS)
                .filter(r -> r.getOutcome() == StepOutcome.SUCCESS || r.isOverridden())
                .isPresent();
    }

    /** Fail-closed when workflow requires ITR and no SUCCESS result. */
    public void requireSuccessIfMandatory(LoanApplication app, String context) {
        if (!isMandatoryInActiveWorkflow(app)) {
            return;
        }
        if (hasSuccessfulPull(app.getId())) {
            return;
        }
        throw new com.los.core.exception.BusinessRuleException(
                "ITR portal login is required for this product. The borrower must complete ITR verification "
                        + "(username, password, and consent) on the borrower portal before " + context + ".");
    }
}
