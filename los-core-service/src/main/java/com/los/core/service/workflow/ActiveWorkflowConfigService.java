package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application-scoped Workflow lookup (W1).
 * Consumes the persisted application pin only. Never discovers a product default.
 */
@Service
@RequiredArgsConstructor
public class ActiveWorkflowConfigService {

    private final ApplicationWorkflowResolver applicationWorkflowResolver;

    public Optional<WorkflowConfig> findActiveForApplication(LoanApplication app) {
        if (app == null) {
            return Optional.empty();
        }
        if (app.getWorkflowId() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(applicationWorkflowResolver.requireConfig(app));
        } catch (BusinessRuleException e) {
            if (ApplicationConfigurationAuthority.isUnconfiguredReason(e.getReason())) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
