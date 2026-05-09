package com.los.core.service.kyc;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.ManualKycReview;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.*;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.ManualKycReviewRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.workflow.IWorkflowEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycOrchestrationServiceImpl implements IKycOrchestrationService {

    private final KycStepResultRepository kycStepResultRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ManualKycReviewRepository manualKycReviewRepository;
    private final IIntegrationRouterService integrationRouter;
    private final IWorkflowEngineService workflowEngine;
    private final AuditService auditService;

    @Override
    @Transactional
    public KycStepResultResponse executeStep(
            UUID applicationId,
            KycStepType stepType,
            Map<String, Object> payload,
            String workflowPreferredProvider) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (app.getStatus() != ApplicationStatus.KYC_IN_PROGRESS && app.getStatus() != ApplicationStatus.KYC_FAILED) {
            throw new BusinessRuleException("Application must be in KYC_IN_PROGRESS or KYC_FAILED status. Current: " + app.getStatus());
        }

        // Determine attempt number
        int attemptNumber = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, stepType)
                .map(r -> r.getAttemptNumber() + 1)
                .orElse(1);

        // Create pending step result
        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(stepType)
                .provider(ProviderType.KARZA) // default, will be updated by router
                .outcome(StepOutcome.PENDING)
                .attemptNumber(attemptNumber)
                .build();

        // Route to provider
        IIntegrationRouterService.KycRouteResult routeResult = integrationRouter.routeKycRequest(
                applicationId, stepType, payload, workflowPreferredProvider);

        if (routeResult.success()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultData = (Map<String, Object>) routeResult.resultData();
            stepResult.setOutcome(StepOutcome.SUCCESS);
            stepResult.setConfidenceScore((double) resultData.getOrDefault("confidenceScore", 0.0));
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedData = (Map<String, Object>) resultData.getOrDefault("parsedData", Map.of());
            stepResult.setParsedData(parsedData);
            stepResult.setTransactionId((String) resultData.get("transactionId"));
            if (routeResult.providerName() != null) {
                try {
                    stepResult.setProvider(ProviderType.valueOf(routeResult.providerName()));
                } catch (IllegalArgumentException ignored) {
                    // keep default
                }
            }
        } else {
            stepResult.setOutcome(StepOutcome.FAILURE);
            stepResult.setErrorMessage(routeResult.errorMessage());
        }

        stepResult.setCompletedAt(Instant.now());
        stepResult = kycStepResultRepository.save(stepResult);

        log.info("KYC step {} for application {} completed with outcome: {}",
                stepType, applicationId, stepResult.getOutcome());

        auditService.logEvent(applicationId, "KYC", stepType.name() + " " + stepResult.getOutcome(),
                null, null,
                Map.of("stepType", stepType.name(), "outcome", stepResult.getOutcome().name(),
                        "provider", stepResult.getProvider().name()),
                "KYC step executed");

        return toResponse(stepResult);
    }

    @Override
    public List<KycStepResultResponse> getStepResults(UUID applicationId) {
        return kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public KycStepResultResponse overrideStep(UUID stepResultId, String reason, UUID overrideBy) {
        KycStepResult stepResult = kycStepResultRepository.findById(stepResultId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC step result not found: " + stepResultId));

        if (stepResult.getOutcome() != StepOutcome.FAILURE) {
            throw new BusinessRuleException("Can only override failed KYC steps");
        }

        stepResult.setOverridden(true);
        stepResult.setOverrideReason(reason);
        stepResult.setOverrideBy(overrideBy);
        stepResult = kycStepResultRepository.save(stepResult);

        log.info("KYC step {} overridden by {} for application {}",
                stepResult.getStepType(), overrideBy, stepResult.getApplicationId());

        auditService.logEvent(stepResult.getApplicationId(), "KYC_OVERRIDE",
                stepResult.getStepType().name() + " overridden",
                overrideBy, Map.of("outcome", "FAILURE"),
                Map.of("overridden", true, "reason", reason),
                "KYC step manually overridden: " + reason);

        return toResponse(stepResult);
    }

    @Override
    @Transactional
    public List<KycStepResultResponse> executeWorkflow(UUID applicationId, Map<String, Object> payload) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        // Get the active workflow for this borrower type and product
        var workflowConfig = workflowEngine.getActiveWorkflow(app.getBorrowerType(), app.getLoanProduct());

        List<Map<String, Object>> steps = workflowConfig.getSteps();
        List<KycStepResultResponse> results = new java.util.ArrayList<>();

        for (Map<String, Object> step : steps) {
            String stepName = (String) step.get("step");
            if (!KycIdentityWorkflow.isKycIdentitySubStepName(stepName)) {
                log.debug("Skipping non-KYC-identity step {} in KYC sub-workflow (bureau / eSign run as separate flow steps)", stepName);
                continue;
            }
            boolean mandatory = (boolean) step.getOrDefault("mandatory", true);
            String workflowPreferredProvider = parseWorkflowProvider(step.get("provider"));

            try {
                KycStepType stepType = KycStepType.valueOf(stepName);
                KycStepResultResponse result =
                        executeStep(applicationId, stepType, payload, workflowPreferredProvider);
                results.add(result);

                // If mandatory step failed and not overridden, stop workflow
                if (mandatory && result.getOutcome() == StepOutcome.FAILURE && !result.isOverridden()) {
                    log.warn("Mandatory KYC step {} failed for application {}. Halting workflow.", stepName, applicationId);
                    break;
                }
            } catch (Exception e) {
                log.error("Error executing KYC step {}: {}", stepName, e.getMessage());
                if (mandatory) {
                    throw e;
                }
            }
        }

        boolean mandatoryFailure = false;
        for (KycStepResultResponse r : results) {
            if (r.getOutcome() == StepOutcome.FAILURE && !r.isOverridden()) {
                mandatoryFailure = true;
                break;
            }
        }

        if (mandatoryFailure) {
            app.setStatus(ApplicationStatus.KYC_FAILED);
            loanApplicationRepository.save(app);
        }

        return results;
    }

    @Override
    public Map<String, Object> computeKycOutcome(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        var workflowConfig = workflowEngine.getActiveWorkflow(app.getBorrowerType(), app.getLoanProduct());
        List<Map<String, Object>> steps = workflowConfig.getSteps();
        List<KycStepResultResponse> results = getStepResults(applicationId);

        Map<String, KycStepResultResponse> latestByStep = new HashMap<>();
        for (KycStepResultResponse r : results) {
            latestByStep.put(r.getStepType().name(), r);
        }

        boolean anyFail = false;
        boolean anyIncomplete = false;

        java.util.List<Map<String, Object>> summary = new java.util.ArrayList<>();

        java.util.Set<KycStepType> phase1StepTypes = java.util.Set.of(
                KycStepType.PAN_VERIFY,
                KycStepType.AADHAAR_OTP,
                KycStepType.MOBILE_OTP,
                KycStepType.FACE_MATCH,
                KycStepType.BANK_PENNY_DROP,
                KycStepType.CKYC_DOWNLOAD
        );

        java.util.Map<KycStepType, ManualKycReview> manualByStep = new java.util.EnumMap<>(KycStepType.class);
        for (ManualKycReview r : manualKycReviewRepository.findByApplicationIdOrderByUpdatedAtDesc(applicationId)) {
            if (r.getStepType() != null && !manualByStep.containsKey(r.getStepType())) {
                manualByStep.put(r.getStepType(), r);
            }
        }

        for (Map<String, Object> step : steps) {
            String stepName = (String) step.get("step");
            if (stepName != null && !KycIdentityWorkflow.isKycIdentitySubStepName(stepName)) {
                continue;
            }
            boolean mandatory = (boolean) step.getOrDefault("mandatory", true);

            KycStepResultResponse r = stepName != null ? latestByStep.get(stepName) : null;
            String outcome = r != null ? r.getOutcome().name() : "MISSING";
            boolean overridden = r != null && r.isOverridden();

            StepOutcome effectiveOutcome = r != null ? r.getOutcome() : null;
            ManualKycDecision manualDecision = null;

            if (stepName != null) {
                try {
                    KycStepType stepTypeEnum = KycStepType.valueOf(stepName);
                    if (phase1StepTypes.contains(stepTypeEnum)) {
                        ManualKycReview manual = manualByStep.get(stepTypeEnum);
                        manualDecision = manual != null ? manual.getDecision() : null;
                        if (manualDecision != null) {
                            if (manualDecision == ManualKycDecision.VERIFIED) {
                                effectiveOutcome = StepOutcome.SUCCESS;
                            } else if (manualDecision == ManualKycDecision.REJECTED) {
                                effectiveOutcome = StepOutcome.FAILURE;
                            } else if (manualDecision == ManualKycDecision.NEEDS_REVIEW) {
                                effectiveOutcome = null;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // stepName not a valid KycStepType
                }
            }

            String effectiveOutcomeStr = effectiveOutcome != null ? effectiveOutcome.name() : "MISSING";
            if (manualDecision != null) {
                if (manualDecision == ManualKycDecision.NEEDS_REVIEW) {
                    effectiveOutcomeStr = "INCOMPLETE";
                }
            }

            if (effectiveOutcome == StepOutcome.FAILURE) {
                anyFail = true;
            }

            if (effectiveOutcome == null) {
                anyIncomplete = true;
            } else if (effectiveOutcome == StepOutcome.ERROR
                    || effectiveOutcome == StepOutcome.PENDING
                    || effectiveOutcome == StepOutcome.MANUAL_REVIEW) {
                anyIncomplete = true;
            }

            if (mandatory) {
                if (effectiveOutcome == null) {
                    anyIncomplete = true;
                } else if (effectiveOutcome != StepOutcome.SUCCESS && effectiveOutcome != StepOutcome.FAILURE) {
                    anyIncomplete = true;
                }
            }

            summary.add(Map.of(
                    "stepType", stepName != null ? stepName : "",
                    "mandatory", mandatory,
                    "outcome", outcome,
                    "effectiveOutcome", effectiveOutcomeStr,
                    "overridden", overridden,
                    "provider", r != null && r.getProvider() != null ? r.getProvider().name() : "",
                    "manualDecision", manualDecision != null ? manualDecision.name() : ""
            ));
        }

        String computed = anyFail ? "FAIL" : (anyIncomplete ? "INCOMPLETE" : "PASS");

        auditService.logEvent(applicationId, "KYC_OUTCOME_COMPUTED", "KYC_OUTCOME_COMPUTED",
                null,
                null,
                Map.of("outcome", computed, "steps", summary),
                "KYC outcome computed: " + computed);

        return Map.of(
                "applicationId", applicationId,
                "outcome", computed,
                "stepSummary", summary
        );
    }

    /**
     * {@code steps[].provider} in workflow JSON (e.g. "AUTHBRIDGE", "EQUIFAX" for a step row).
     */
    private static String parseWorkflowProvider(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private KycStepResultResponse toResponse(KycStepResult result) {
        return KycStepResultResponse.builder()
                .id(result.getId())
                .applicationId(result.getApplicationId())
                .stepType(result.getStepType())
                .provider(result.getProvider())
                .outcome(result.getOutcome())
                .confidenceScore(result.getConfidenceScore())
                .parsedData(result.getParsedData())
                .transactionId(result.getTransactionId())
                .errorMessage(result.getErrorMessage())
                .overridden(result.isOverridden())
                .overrideReason(result.getOverrideReason())
                .attemptNumber(result.getAttemptNumber())
                .createdAt(result.getCreatedAt())
                .completedAt(result.getCompletedAt())
                .build();
    }
}
