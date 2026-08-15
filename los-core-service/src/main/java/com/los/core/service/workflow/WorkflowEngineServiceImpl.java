package com.los.core.service.workflow;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.loan.CoApplicantWorkflowConfig;
import com.los.core.service.workflow.intake.KycStepIntakeCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineServiceImpl implements IWorkflowEngineService {

    private final WorkflowConfigRepository workflowRepository;
    private final AdminConfigAuditSupport adminConfigAuditSupport;

    @Override
    @Transactional
    public WorkflowConfigResponse createWorkflow(WorkflowConfigRequest request) {
        List<Map<String, Object>> steps = request.getSteps() != null
                ? request.getSteps()
                : List.of();
        String intakeSeg = request.getIntakeSegment() != null
                ? request.getIntakeSegment().name()
                : IntakeSegment.BORROWER.name();
        WorkflowConfig config = WorkflowConfig.builder()
                .name(request.getName())
                .borrowerType(request.getBorrowerType().name())
                .loanProduct(request.getLoanProduct())
                .lmsProductCode(resolveWorkflowLmsProductCode(request))
                .lmsTenureUnit(resolveWorkflowLmsTenureUnit(request))
                .intakeSegment(intakeSeg)
                .intakeIdentitySchema(request.getIntakeIdentitySchema())
                .intakeConfig(resolveIntakeConfigForCreate(request))
                .bureauEnabled(request.getBureauEnabled() == null || request.getBureauEnabled())
                .autoPullBureauAfterKycSuccess(
                        request.getAutoPullBureauAfterKycSuccess() == null
                                || request.getAutoPullBureauAfterKycSuccess())
                .steps(steps)
                .processNotificationMappings(request.getProcessNotificationMappings())
                .manualOverridePolicies(request.getManualOverridePolicies())
                .conditionalRules(request.getConditionalRules())
                .vkycTriggerCondition(request.getVkycTriggerCondition())
                .workflowPosition(request.getWorkflowPosition())
                // Inactive until explicitly activated (avoids multiple active rows per borrower/product)
                .active(false)
                .version(1)
                .build();

        config = workflowRepository.save(config);
        log.info("Workflow created: {} for {}/{}/{}", config.getName(), config.getBorrowerType(), config.getLoanProduct(), config.getIntakeSegment());
        WorkflowConfigResponse saved = toResponse(config);
        adminConfigAuditSupport.captureCreate("WORKFLOW_CONFIG", config.getId().toString(), saved, "Workflow created");
        return saved;
    }

    @Override
    @Transactional
    public WorkflowConfigResponse updateWorkflow(UUID workflowId, WorkflowConfigRequest request) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        WorkflowConfigResponse before = toResponse(config);

        config.setName(request.getName());
        if (request.getBorrowerType() != null) {
            config.setBorrowerType(request.getBorrowerType().name());
        }
        if (request.getLoanProduct() != null) {
            config.setLoanProduct(request.getLoanProduct());
        }
        if (request.getLmsProductCode() != null) {
            config.setLmsProductCode(blankToNull(request.getLmsProductCode()));
        }
        if (request.getLmsTenureUnit() != null) {
            config.setLmsTenureUnit(blankToNull(request.getLmsTenureUnit()));
        }
        if (request.getIntakeSegment() != null) {
            config.setIntakeSegment(request.getIntakeSegment().name());
        }
        if (request.getIntakeIdentitySchema() != null) {
            config.setIntakeIdentitySchema(request.getIntakeIdentitySchema());
        }
        if (request.getIntakeConfig() != null) {
            config.setIntakeConfig(sanitizeIntakeConfig(request.getLoanProduct() != null
                    ? request.getLoanProduct()
                    : config.getLoanProduct(), request.getIntakeSegment() != null
                    ? request.getIntakeSegment().name()
                    : config.getIntakeSegment(), copyJsonMap(request.getIntakeConfig())));
        }
        config.setBureauEnabled(request.getBureauEnabled() == null || request.getBureauEnabled());
        config.setAutoPullBureauAfterKycSuccess(
                request.getAutoPullBureauAfterKycSuccess() == null
                        || request.getAutoPullBureauAfterKycSuccess());
        if (request.getSteps() != null) {
            config.setSteps(request.getSteps());
        }
        config.setProcessNotificationMappings(request.getProcessNotificationMappings());
        config.setManualOverridePolicies(request.getManualOverridePolicies());
        config.setConditionalRules(request.getConditionalRules());
        config.setVkycTriggerCondition(request.getVkycTriggerCondition());
        config.setWorkflowPosition(request.getWorkflowPosition());
        config.setVersion(config.getVersion() + 1);
        config.setUpdatedAt(Instant.now());

        config = workflowRepository.save(config);
        log.info("Workflow updated: {} (v{})", config.getName(), config.getVersion());
        WorkflowConfigResponse after = toResponse(config);
        adminConfigAuditSupport.captureUpdate("WORKFLOW_CONFIG", workflowId.toString(), before, after, "Workflow updated");
        return after;
    }

    @Override
    public WorkflowConfigResponse getActiveWorkflow(BorrowerType borrowerType, String loanProduct) {
        return getActiveWorkflow(borrowerType, loanProduct, IntakeSegment.BORROWER);
    }

    @Override
    public WorkflowConfigResponse getActiveWorkflow(BorrowerType borrowerType, String loanProduct, IntakeSegment intakeSegment) {
        // Admin/catalog lookup by dimensions only — NOT application orchestration (W1).
        // Application consumers must use ApplicationWorkflowResolver / ActiveWorkflowConfigService.
        IntakeSegment seg = intakeSegment != null ? intakeSegment : IntakeSegment.BORROWER;
        WorkflowConfig config = workflowRepository
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        borrowerType.name(), loanProduct, seg.name())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No active workflow for %s/%s/%s", borrowerType, loanProduct, seg)));
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

        WorkflowConfigResponse before = toResponse(config);
        // Multiple active workflows for the same borrower/product/segment are allowed (V84).
        // Applications resolve via workflow_id binding when set, otherwise highest version.
        config.setActive(true);
        config.setUpdatedAt(Instant.now());
        workflowRepository.save(config);
        log.info("Workflow activated: {}", config.getName());
        adminConfigAuditSupport.captureAction(
                "WORKFLOW_CONFIG",
                workflowId.toString(),
                "ACTIVATE",
                before,
                toResponse(config),
                "Workflow activated");
    }

    @Override
    @Transactional
    public void deactivateWorkflow(UUID workflowId) {
        WorkflowConfig config = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
        WorkflowConfigResponse before = toResponse(config);
        config.setActive(false);
        workflowRepository.save(config);
        log.info("Workflow deactivated: {}", config.getName());
        adminConfigAuditSupport.captureAction(
                "WORKFLOW_CONFIG",
                workflowId.toString(),
                "DEACTIVATE",
                before,
                toResponse(config),
                "Workflow deactivated");
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
        WorkflowConfigResponse before = toResponse(config);
        workflowRepository.delete(config);
        log.info("Workflow deleted: {} ({})", config.getName(), workflowId);
        adminConfigAuditSupport.captureDelete(
                "WORKFLOW_CONFIG",
                workflowId.toString(),
                before,
                "Workflow deleted");
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

    private static String normalizeIntakeSegment(String intakeSegment) {
        if (intakeSegment == null || intakeSegment.isBlank()) {
            return IntakeSegment.BORROWER.name();
        }
        return intakeSegment;
    }

    private static String resolveWorkflowLmsProductCode(WorkflowConfigRequest request) {
        if (request.getLmsProductCode() != null && !request.getLmsProductCode().isBlank()) {
            return request.getLmsProductCode().trim();
        }
        // LMS-PRODUCT-MAPPING-P0: no silent IPPOPAYM01 default — blank stays unmapped (fail-closed at open).
        return null;
    }

    private static String resolveWorkflowLmsTenureUnit(WorkflowConfigRequest request) {
        if (request.getLmsTenureUnit() != null && !request.getLmsTenureUnit().isBlank()) {
            return request.getLmsTenureUnit().trim();
        }
        return "Month";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Map<String, Object> resolveIntakeConfigForCreate(WorkflowConfigRequest request) {
        Map<String, Object> base;
        if (request.getIntakeConfig() != null && !request.getIntakeConfig().isEmpty()) {
            base = copyJsonMap(request.getIntakeConfig());
        } else {
            base = KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig();
        }
        String segment = request.getIntakeSegment() != null
                ? request.getIntakeSegment().name()
                : IntakeSegment.BORROWER.name();
        return sanitizeIntakeConfig(request.getLoanProduct(), segment, base);
    }

    /** Co-applicant is never allowed for invoice discounting or ANCHOR intake. */
    private static Map<String, Object> sanitizeIntakeConfig(String loanProduct, String intakeSegment, Map<String, Object> intakeConfig) {
        boolean invoiceDiscounting = StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(loanProduct);
        boolean anchor = IntakeSegment.ANCHOR.name().equalsIgnoreCase(
                intakeSegment != null ? intakeSegment.trim() : "");
        if (invoiceDiscounting || anchor) {
            return CoApplicantWorkflowConfig.forceDisabledInIntakeConfig(intakeConfig);
        }
        return intakeConfig;
    }

    /**
     * Assign a new map instance so Hibernate JSONB dirty detection always fires on update.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyJsonMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copy.put(entry.getKey(), copyJsonMap((Map<String, Object>) nested));
            } else if (value instanceof List<?> list) {
                List<Object> listCopy = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        listCopy.add(copyJsonMap((Map<String, Object>) itemMap));
                    } else {
                        listCopy.add(item);
                    }
                }
                copy.put(entry.getKey(), listCopy);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private WorkflowConfigResponse toResponse(WorkflowConfig config) {
        return WorkflowConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .borrowerType(config.getBorrowerType())
                .loanProduct(config.getLoanProduct())
                .lmsProductCode(config.getLmsProductCode())
                .lmsTenureUnit(config.getLmsTenureUnit())
                .intakeSegment(config.getIntakeSegment())
                .intakeIdentitySchema(config.getIntakeIdentitySchema())
                .intakeConfig(config.getIntakeConfig())
                .bureauEnabled(config.isBureauEnabled())
                .autoPullBureauAfterKycSuccess(config.isAutoPullBureauAfterKycSuccess())
                .steps(config.getSteps())
                .processNotificationMappings(config.getProcessNotificationMappings())
                .manualOverridePolicies(config.getManualOverridePolicies())
                .conditionalRules(config.getConditionalRules())
                .vkycTriggerCondition(config.getVkycTriggerCondition())
                .workflowPosition(config.getWorkflowPosition())
                .active(config.isActive())
                .version(config.getVersion())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
