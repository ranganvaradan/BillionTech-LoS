package com.los.core.service.workflow;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the active {@link WorkflowConfig} for an application using {@code intake_segment}.
 */
@Service
@RequiredArgsConstructor
public class ActiveWorkflowConfigService {

    private final WorkflowConfigRepository workflowConfigRepository;

    public Optional<WorkflowConfig> findActiveForApplication(LoanApplication app) {
        if (app.getBorrowerType() == null || app.getLoanProduct() == null || app.getLoanProduct().isBlank()) {
            return Optional.empty();
        }
        IntakeSegment seg = app.getIntakeSegment() != null ? app.getIntakeSegment() : IntakeSegment.BORROWER;
        return workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
                app.getBorrowerType().name(), app.getLoanProduct(), seg.name());
    }
}
