package com.los.core.service.workflow;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineServiceImpl implements IWorkflowEngineService {

    private final WorkflowConfigRepository workflowRepository;

    @Override
    @Transactional
    public WorkflowConfigResponse createWorkflow(WorkflowConfigRequest request) {
        WorkflowConfig config = WorkflowConfig.builder()
                .name(request.getName())
                .borrowerType(request.getBorrowerType().name())
                .loanProduct(request.getLoanProduct())
                .steps(request.getSteps())
                .active(true)
                .version(1)
                .build();

        config = workflowRepository.save(config);
        log.info("Workflow created: {} for {}/{}", config.getName(), config.getBorrowerType(), config.getLoanProduct());
        return toResponse(config);
    }

    @Override
    @Transactional
    public WorkflowConfigResponse updateWorkflow(UUID workflowId, WorkflowConfigRequest request) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        config.setName(request.getName());
        config.setSteps(request.getSteps());
        config.setVersion(config.getVersion() + 1);
        config.setUpdatedAt(Instant.now());

        config = workflowRepository.save(config);
        log.info("Workflow updated: {} (v{})", config.getName(), config.getVersion());
        return toResponse(config);
    }

    @Override
    public WorkflowConfigResponse getActiveWorkflow(BorrowerType borrowerType, String loanProduct) {
        WorkflowConfig config = workflowRepository
                .findByBorrowerTypeAndLoanProductAndActiveTrue(borrowerType.name(), loanProduct)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No active workflow for %s/%s", borrowerType, loanProduct)));
        return toResponse(config);
    }

    @Override
    public List<WorkflowConfigResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void activateWorkflow(UUID workflowId) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        // Deactivate existing active workflow for same borrower type + product
        workflowRepository.findByBorrowerTypeAndLoanProductAndActiveTrue(
                config.getBorrowerType(), config.getLoanProduct())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    workflowRepository.save(existing);
                });

        config.setActive(true);
        workflowRepository.save(config);
        log.info("Workflow activated: {}", config.getName());
    }

    @Override
    @Transactional
    public void deactivateWorkflow(UUID workflowId) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
        config.setActive(false);
        workflowRepository.save(config);
        log.info("Workflow deactivated: {}", config.getName());
    }

    private WorkflowConfigResponse toResponse(WorkflowConfig config) {
        return WorkflowConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .borrowerType(config.getBorrowerType())
                .loanProduct(config.getLoanProduct())
                .steps(config.getSteps())
                .active(config.isActive())
                .version(config.getVersion())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
