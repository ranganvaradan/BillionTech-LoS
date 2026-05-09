package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
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
import java.util.Map;
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
        List<Map<String, Object>> steps = request.getSteps() != null
                ? request.getSteps()
                : List.of();
        WorkflowConfig config = WorkflowConfig.builder()
                .name(request.getName())
                .borrowerType(request.getBorrowerType().name())
                .loanProduct(request.getLoanProduct())
                .steps(steps)
                .conditionalRules(request.getConditionalRules())
                .vkycTriggerCondition(request.getVkycTriggerCondition())
                .workflowPosition(request.getWorkflowPosition())
                // Inactive until explicitly activated (avoids multiple active rows per borrower/product)
                .active(false)
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
        if (request.getBorrowerType() != null) {
            config.setBorrowerType(request.getBorrowerType().name());
        }
        if (request.getLoanProduct() != null) {
            config.setLoanProduct(request.getLoanProduct());
        }
        if (request.getSteps() != null) {
            config.setSteps(request.getSteps());
        }
        config.setConditionalRules(request.getConditionalRules());
        config.setVkycTriggerCondition(request.getVkycTriggerCondition());
        config.setWorkflowPosition(request.getWorkflowPosition());
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

    @Override
    @Transactional
    public void deleteWorkflow(UUID workflowId) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
        if (config.isActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an active workflow. Deactivate it first, or delete a draft (inactive) configuration.",
                    "WORKFLOW_ACTIVE_DELETE_FORBIDDEN",
                    "DEACTIVATE_FIRST",
                    null);
        }
        workflowRepository.delete(config);
        log.info("Workflow deleted: {} ({})", config.getName(), workflowId);
    }

    /**
     * BR-6.2: Identify parallel step groups for concurrent execution.
     * Steps within a parallel group can be executed simultaneously.
     */
    public Map<String, Object> getParallelExecutionPlan(UUID workflowId) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        List<Map<String, Object>> steps = config.getSteps();
        List<Map<String, Object>> parallelGroups = config.getParallelGroups();

        List<Map<String, Object>> executionPlan = new java.util.ArrayList<>();
        int phase = 1;

        if (parallelGroups != null && !parallelGroups.isEmpty()) {
            // Build execution phases from parallel groups
            java.util.Set<String> parallelStepNames = new java.util.HashSet<>();
            for (Map<String, Object> group : parallelGroups) {
                @SuppressWarnings("unchecked")
                List<String> groupSteps = (List<String>) group.get("steps");
                if (groupSteps != null) {
                    Map<String, Object> parallelPhase = new java.util.LinkedHashMap<>();
                    parallelPhase.put("phase", phase++);
                    parallelPhase.put("type", "PARALLEL");
                    parallelPhase.put("groupName", group.get("group"));
                    parallelPhase.put("steps", groupSteps);
                    parallelPhase.put("description", "Execute " + groupSteps.size() + " steps concurrently");
                    executionPlan.add(parallelPhase);
                    parallelStepNames.addAll(groupSteps);
                }
            }

            // Add remaining sequential steps
            for (Map<String, Object> step : steps) {
                String stepType = (String) step.get("stepType");
                if (stepType != null && !parallelStepNames.contains(stepType)) {
                    Map<String, Object> sequentialPhase = new java.util.LinkedHashMap<>();
                    sequentialPhase.put("phase", phase++);
                    sequentialPhase.put("type", "SEQUENTIAL");
                    sequentialPhase.put("steps", List.of(stepType));
                    sequentialPhase.put("description", "Execute sequentially");
                    executionPlan.add(sequentialPhase);
                }
            }
        } else {
            // Default: auto-detect parallelizable steps
            // KYC verification steps can run in parallel; document/credit steps are sequential
            List<String> kycSteps = new java.util.ArrayList<>();
            List<String> otherSteps = new java.util.ArrayList<>();

            for (Map<String, Object> step : steps) {
                String stepType = (String) step.get("stepType");
                if (stepType != null && isKycVerificationStep(stepType)) {
                    kycSteps.add(stepType);
                } else if (stepType != null) {
                    otherSteps.add(stepType);
                }
            }

            if (!kycSteps.isEmpty()) {
                Map<String, Object> kycPhase = new java.util.LinkedHashMap<>();
                kycPhase.put("phase", phase++);
                kycPhase.put("type", "PARALLEL");
                kycPhase.put("groupName", "AUTO_KYC_PARALLEL");
                kycPhase.put("steps", kycSteps);
                kycPhase.put("description", "Auto-detected: " + kycSteps.size() + " KYC steps can run in parallel");
                executionPlan.add(kycPhase);
            }

            for (String step : otherSteps) {
                Map<String, Object> seqPhase = new java.util.LinkedHashMap<>();
                seqPhase.put("phase", phase++);
                seqPhase.put("type", "SEQUENTIAL");
                seqPhase.put("steps", List.of(step));
                seqPhase.put("description", "Execute sequentially");
                executionPlan.add(seqPhase);
            }
        }

        return Map.of(
                "workflowId", workflowId.toString(),
                "workflowName", config.getName(),
                "totalSteps", steps.size(),
                "totalPhases", executionPlan.size(),
                "executionPlan", executionPlan,
                "estimatedTimeSavingPercent", calculateTimeSaving(executionPlan, steps.size())
        );
    }

    private boolean isKycVerificationStep(String stepType) {
        return stepType.contains("VERIFY") || stepType.contains("OTP")
                || stepType.equals("GSTIN_VERIFY") || stepType.equals("PAN_VERIFY")
                || stepType.equals("AADHAAR_OTP") || stepType.equals("BANK_PENNY_DROP")
                || stepType.equals("UDYAM_VERIFY") || stepType.equals("CIN_MCA21");
    }

    private int calculateTimeSaving(List<Map<String, Object>> plan, int totalSteps) {
        long parallelStepCount = plan.stream()
                .filter(p -> "PARALLEL".equals(p.get("type")))
                .mapToLong(p -> {
                    @SuppressWarnings("unchecked")
                    List<String> steps2 = (List<String>) p.get("steps");
                    return steps2 != null ? steps2.size() - 1 : 0;
                })
                .sum();
        return totalSteps > 0 ? (int) (parallelStepCount * 100 / totalSteps) : 0;
    }

    private WorkflowConfigResponse toResponse(WorkflowConfig config) {
        return WorkflowConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .borrowerType(config.getBorrowerType())
                .loanProduct(config.getLoanProduct())
                .steps(config.getSteps())
                .conditionalRules(config.getConditionalRules())
                .vkycTriggerCondition(config.getVkycTriggerCondition())
                .workflowPosition(config.getWorkflowPosition())
                .active(config.isActive())
                .version(config.getVersion())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
