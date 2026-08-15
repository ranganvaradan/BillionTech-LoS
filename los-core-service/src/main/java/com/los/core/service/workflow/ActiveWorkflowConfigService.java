package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application-scoped Workflow lookup (W1).
 * Delegates to {@link ApplicationWorkflowResolver}; never silently substitutes another active Workflow
 * when a persisted reference is broken.
 */
@Service
@RequiredArgsConstructor
public class ActiveWorkflowConfigService {

    private final ApplicationWorkflowResolver applicationWorkflowResolver;

    public Optional<WorkflowConfig> findActiveForApplication(LoanApplication app) {
        if (app == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(applicationWorkflowResolver.requireConfig(app));
        } catch (BusinessRuleException e) {
            if ("WORKFLOW_NOT_RESOLVED".equals(e.getReason())) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
