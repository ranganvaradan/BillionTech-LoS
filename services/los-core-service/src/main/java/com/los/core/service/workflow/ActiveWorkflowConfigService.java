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
 * Prefers an application-bound {@code workflow_id} when present; otherwise highest active version.
 */
@Service
@RequiredArgsConstructor
public class ActiveWorkflowConfigService {

    private final WorkflowConfigRepository workflowConfigRepository;

    public Optional<WorkflowConfig> findActiveForApplication(LoanApplication app) {
        if (app.getWorkflowId() != null) {
            Optional<WorkflowConfig> bound = workflowConfigRepository.findById(app.getWorkflowId());
            if (bound.isPresent()) {
                return bound;
            }
        }
        if (app.getBorrowerType() == null || app.getLoanProduct() == null || app.getLoanProduct().isBlank()) {
            return Optional.empty();
        }
        IntakeSegment seg = app.getIntakeSegment() != null ? app.getIntakeSegment() : IntakeSegment.BORROWER;
        return workflowConfigRepository
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        app.getBorrowerType().name(), app.getLoanProduct(), seg.name())
                .stream()
                .findFirst();
    }
}
