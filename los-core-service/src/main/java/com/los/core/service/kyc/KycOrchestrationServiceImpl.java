package com.los.core.service.kyc;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.ManualKycReview;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.*;
import com.los.core.model.entity.AuditEvent;
import com.los.core.repository.AuditEventRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.ManualKycReviewRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import com.los.core.model.entity.WorkflowConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ApplicationWorkflowResolver applicationWorkflowResolver;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;

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

        // Same identity enrichment as executeWorkflow — intake stores mobile/phone, providers expect aliases.
        Map<String, Object> enrichedPayload = ApplicantIdentityResolver.enrichKycPayload(app, payload);
        payload = enrichedPayload;

        // Determine attempt number
        int attemptNumber = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, stepType)
                .map(r -> r.getAttemptNumber() + 1)
                .orElse(1);

        if (stepType == KycStepType.VIDEO_KYC && app.getVkycCompletionMode() == VkycCompletionMode.PKYC) {
            log.info("Skipping HyperVerge VKYC link generation because PKYC completion detected for applicationId={}", applicationId);
            auditService.logEvent(applicationId, "VKYC", "HYPERVERGE_BYPASSED_DUE_TO_PKYC", null, null,
                    Map.of("step", "VIDEO_KYC", "vkycCompletionMode", VkycCompletionMode.PKYC.name()),
                    "VIDEO_KYC KYC sub-step skipped — application completed through Physical KYC");
            KycStepResult stepResult = KycStepResult.builder()
                    .applicationId(applicationId)
                    .stepType(stepType)
                    .provider(ProviderType.HYPERVERGE)
                    .outcome(StepOutcome.SUCCESS)
                    .attemptNumber(attemptNumber)
                    .confidenceScore(1.0)
                    .parsedData(Map.of(
                            "skippedReason", "PKYC_COMPLETED",
                            "message", "Video KYC satisfied via Physical KYC — HyperVerge not invoked"))
                    .transactionId(null)
                    .completedAt(Instant.now())
                    .build();
            stepResult = kycStepResultRepository.save(stepResult);
            log.info("KYC step {} for application {} completed with outcome: {}",
                    stepType, applicationId, stepResult.getOutcome());
            auditService.logEvent(applicationId, "KYC", stepType.name() + " " + stepResult.getOutcome(),
                    null, null,
                    Map.of("stepType", stepType.name(), "outcome", stepResult.getOutcome().name(),
                            "provider", stepResult.getProvider().name(), "pkycBypass", true),
                    "KYC step executed (PKYC bypass)");
            auditKycOutcomeIfChanged(applicationId);
            return toResponse(stepResult);
        }

        // Create pending step result
        if (stepType == KycStepType.ITR_RETURN_FORMS) {
            return handleItrReturnFormsStaffReuse(applicationId, attemptNumber);
        }
        if (stepType == KycStepType.GST_ANALYSIS) {
            return handleGstAnalysisStaffReuse(applicationId, attemptNumber);
        }

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

        auditKycOutcomeIfChanged(applicationId);

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

        auditKycOutcomeIfChanged(stepResult.getApplicationId());

        return toResponse(stepResult);
    }

    @Override
    @Transactional
    public List<KycStepResultResponse> executeWorkflow(UUID applicationId, Map<String, Object> payload) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        com.los.core.service.workflow.ApplicationConfigurationAuthority.assertReadyForWorkflowExecution(app);

        // W1 — consume the application's resolved Workflow Version (not independent active lookup).
        WorkflowConfig workflowConfig = applicationWorkflowResolver.requireConfig(app);

        List<Map<String, Object>> steps = workflowConfig.getSteps();
        List<KycStepResultResponse> results = new java.util.ArrayList<>();
        Map<String, Object> mergedPayload = ApplicantIdentityResolver.enrichKycPayload(app, payload);
        Map<String, Object> intakeConfig = workflowConfig.getIntakeConfig();

        for (Map<String, Object> step : steps) {
            String stepName = (String) step.get("step");
            if (!KycIdentityWorkflow.isKycIdentitySubStepName(stepName)) {
                log.debug("Skipping non-KYC-identity step {} in KYC sub-workflow (bureau / eSign run as separate flow steps)", stepName);
                continue;
            }
            if (KycMandatoryGroupEvaluator.shouldSkipForMissingPayload(stepName, intakeConfig, mergedPayload)) {
                log.info("Skipping KYC step {} for application {} — no input provided (optional member of ANY mandatory group)",
                        stepName, applicationId);
                continue;
            }
            boolean mandatory = (boolean) step.getOrDefault("mandatory", true);
            String workflowPreferredProvider = parseWorkflowProvider(step.get("provider"));

            try {
                KycStepType stepType = KycStepType.valueOf(stepName);
                KycStepResultResponse result =
                        executeStep(applicationId, stepType, mergedPayload, workflowPreferredProvider);
                results.add(result);

                // If mandatory step failed and not overridden, stop workflow (unless in ANY group)
                if (mandatory && result.getOutcome() == StepOutcome.FAILURE && !result.isOverridden()
                        && KycMandatoryGroupEvaluator.shouldHaltOnMandatoryFailure(stepName, intakeConfig)) {
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

        Map<String, StepOutcome> effectiveByStep = new HashMap<>();
        for (KycStepResultResponse r : results) {
            if (r.getStepType() != null) {
                effectiveByStep.put(r.getStepType().name(), r.getOutcome());
            }
        }
        boolean mandatoryFailure = KycMandatoryGroupEvaluator.hasMandatoryFailure(effectiveByStep, steps, intakeConfig);

        if (mandatoryFailure) {
            app.setStatus(ApplicationStatus.KYC_FAILED);
            loanApplicationRepository.save(app);
        }

        auditKycOutcomeIfChanged(applicationId);

        return results;
    }

    @Override
    public Map<String, Object> computeKycOutcome(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        if (app.getWorkflowId() == null) {
            return Map.of(
                    "applicationId", applicationId,
                    "outcome", "CONFIGURATION_PENDING",
                    "configurationPending", true,
                    "reason", com.los.core.service.workflow.ApplicationConfigurationAuthority.WORKFLOW_NOT_PINNED,
                    "stepSummary", List.of());
        }
        WorkflowConfig workflowConfig = applicationWorkflowResolver.requireConfig(app);
        List<Map<String, Object>> steps = workflowConfig.getSteps();
        Map<String, Object> intakeConfig = workflowConfig.getIntakeConfig();
        List<KycStepResultResponse> results = getStepResults(applicationId);

        Map<String, KycStepResultResponse> latestByStep = new HashMap<>();
        for (KycStepResultResponse r : results) {
            latestByStep.put(r.getStepType().name(), r);
        }

        boolean anyFail = false;
        boolean anyIncomplete = false;
        Map<String, StepOutcome> effectiveOutcomes = new HashMap<>();

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

            if (stepName != null && effectiveOutcome != null) {
                effectiveOutcomes.put(stepName, effectiveOutcome);
            }

            Set<String> groupedSteps = KycMandatoryGroupEvaluator.stepsInAnyGroups(intakeConfig);
            boolean inAnyGroup = stepName != null && groupedSteps.contains(stepName.toUpperCase());

            if (effectiveOutcome == StepOutcome.FAILURE && !inAnyGroup) {
                anyFail = true;
            }

            if (effectiveOutcome == null) {
                if (!inAnyGroup) {
                    anyIncomplete = true;
                }
            } else if (effectiveOutcome == StepOutcome.ERROR
                    || effectiveOutcome == StepOutcome.PENDING
                    || effectiveOutcome == StepOutcome.MANUAL_REVIEW) {
                if (!inAnyGroup) {
                    anyIncomplete = true;
                }
            }

            if (mandatory && !inAnyGroup) {
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

        if (KycMandatoryGroupEvaluator.hasMandatoryFailure(effectiveOutcomes, steps, intakeConfig)) {
            anyFail = true;
            anyIncomplete = false;
        } else if (!anyFail) {
            for (KycMandatoryGroupEvaluator.AnyGroup group : KycMandatoryGroupEvaluator.anyGroups(intakeConfig)) {
                boolean anyMandatory = false;
                boolean anySuccess = false;
                for (String member : group.steps()) {
                    Boolean m = null;
                    for (Map<String, Object> step : steps) {
                        if (member.equalsIgnoreCase(stepNameFromMap(step))) {
                            m = (boolean) step.getOrDefault("mandatory", true);
                            break;
                        }
                    }
                    if (m == null || !m) {
                        continue;
                    }
                    anyMandatory = true;
                    StepOutcome o = effectiveOutcomes.get(member);
                    if (o == StepOutcome.SUCCESS) {
                        anySuccess = true;
                        break;
                    }
                }
                if (anyMandatory && !anySuccess) {
                    anyIncomplete = true;
                }
            }
        }

        String computed = anyFail ? "FAIL" : (anyIncomplete ? "INCOMPLETE" : "PASS");

        // Do not audit here — computeKycOutcome is called on reads and unrelated flows.
        // Mutations call auditKycOutcomeIfChanged() so only real outcome transitions are logged.

        return Map.of(
                "applicationId", applicationId,
                "outcome", computed,
                "stepSummary", summary
        );
    }

    /**
     * Persists a KYC_OUTCOME_COMPUTED audit only when the computed outcome differs from the last logged one.
     */
    private void auditKycOutcomeIfChanged(UUID applicationId) {
        Map<String, Object> outcome = computeKycOutcome(applicationId);
        String computed = String.valueOf(outcome.getOrDefault("outcome", "INCOMPLETE"));
        String previous = lastLoggedKycOutcome(applicationId);
        if (computed.equals(previous)) {
            return;
        }
        Object stepSummary = outcome.getOrDefault("stepSummary", List.of());
        auditService.logEvent(
                applicationId,
                "KYC_OUTCOME_COMPUTED",
                "KYC_OUTCOME_COMPUTED",
                null,
                previous == null ? null : Map.of("outcome", previous),
                Map.of("outcome", computed, "steps", stepSummary),
                "KYC outcome computed: " + computed);
    }

    private String lastLoggedKycOutcome(UUID applicationId) {
        Optional<AuditEvent> last = auditEventRepository
                .findTopByApplicationIdAndEventTypeOrderByCreatedAtDesc(applicationId, "KYC_OUTCOME_COMPUTED");
        if (last.isEmpty() || last.get().getNewState() == null) {
            return null;
        }
        Object o = last.get().getNewState().get("outcome");
        return o == null ? null : String.valueOf(o);
    }

    private static String stepNameFromMap(Map<String, Object> step) {
        Object v = step.get("step");
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String parseWorkflowProvider(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * ITR credentials/password are never available on staff Run KYC.
     * Reuse borrower SUCCESS (or prior FAILURE) without re-calling Karza.
     */
    private KycStepResultResponse handleItrReturnFormsStaffReuse(UUID applicationId, int attemptNumber) {
        Optional<KycStepResult> latest = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.ITR_RETURN_FORMS);
        if (latest.isPresent()) {
            KycStepResult existing = latest.get();
            log.info("Reusing borrower ITR_RETURN_FORMS result {} for application {} (staff run, no password re-call)",
                    existing.getOutcome(), applicationId);
            return toResponse(existing);
        }
        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.ITR_RETURN_FORMS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.FAILURE)
                .attemptNumber(attemptNumber)
                .errorMessage("ITR login must be completed by the borrower on the portal (username/password never stored)")
                .parsedData(Map.of(
                        "message", "Borrower must complete ITR return-forms verification with consent",
                        "borrowerOnly", true))
                .completedAt(Instant.now())
                .build();
        stepResult = kycStepResultRepository.save(stepResult);
        auditService.logEvent(applicationId, "KYC", "ITR_RETURN_FORMS FAILURE",
                null, null,
                Map.of("stepType", KycStepType.ITR_RETURN_FORMS.name(), "outcome", StepOutcome.FAILURE.name(),
                        "borrowerOnly", true),
                "ITR step missing — staff run cannot supply credentials");
        auditKycOutcomeIfChanged(applicationId);
        return toResponse(stepResult);
    }

    /**
     * GST analysis upload/report is driven by borrower prepare + admin actions, not Run KYC form payload.
     */
    private KycStepResultResponse handleGstAnalysisStaffReuse(UUID applicationId, int attemptNumber) {
        Optional<KycStepResult> latest = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.GST_ANALYSIS);
        if (latest.isPresent()) {
            KycStepResult existing = latest.get();
            log.info("Reusing GST_ANALYSIS result {} phase={} for application {} (staff run, no re-call)",
                    existing.getOutcome(),
                    existing.getParsedData() != null ? existing.getParsedData().get("phase") : null,
                    applicationId);
            return toResponse(existing);
        }
        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.FAILURE)
                .attemptNumber(attemptNumber)
                .errorMessage("GST analysis must be prepared by the borrower (PDFs + GSTIN + consent); admin generates report after upload")
                .parsedData(Map.of(
                        "message", "Borrower must upload GST return PDFs, GSTIN, and consent on the portal",
                        "borrowerOnly", true,
                        "phase", "PREPARE"))
                .completedAt(Instant.now())
                .build();
        stepResult = kycStepResultRepository.save(stepResult);
        auditService.logEvent(applicationId, "KYC", "GST_ANALYSIS FAILURE",
                null, null,
                Map.of("stepType", KycStepType.GST_ANALYSIS.name(), "outcome", StepOutcome.FAILURE.name(),
                        "borrowerOnly", true),
                "GST analysis missing — staff run cannot supply documents");
        auditKycOutcomeIfChanged(applicationId);
        return toResponse(stepResult);
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
