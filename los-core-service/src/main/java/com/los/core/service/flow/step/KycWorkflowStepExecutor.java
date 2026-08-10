package com.los.core.service.flow.step;

import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycApplicationEvaluationFacade;
import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.flow.event.AutoBureauPullRequestedEvent;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.ApplicationInputChangeTracker;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#KYC_WORKFLOW} — delegates to {@link IKycOrchestrationService#executeWorkflow}.
 * Logic was moved from {@code LoanApplicationFlowService#runKycWorkflow} without semantic change.
 * KYC-5: optional shadow Decision Policy evaluation after workflow — never mutates KYC outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycWorkflowStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final ObjectProvider<ShadowKycApplicationEvaluationFacade> shadowKycFacade;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.KYC_WORKFLOW.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.KYC_IN_PROGRESS) {
            throw new com.los.core.exception.BusinessRuleException(
                    String.format("Cannot run KYC — application must be in KYC_IN_PROGRESS status. Current: %s", app.getStatus()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> kycPayload = context != null && context.get("kycPayload") != null
                ? (Map<String, Object>) context.get("kycPayload")
                : Map.of();

        List<KycStepResultResponse> results = kycOrchestrationService.executeWorkflow(applicationId, kycPayload);

        long successCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.SUCCESS).count();
        long failureCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.FAILURE).count();
        boolean allPassed = failureCount == 0;

        if (!allPassed) {
            app.setStatus(ApplicationStatus.KYC_FAILED);
            applicationRepository.save(app);
            log.warn("KYC workflow for {} — {} failures out of {} steps",
                    app.getApplicationNumber(), failureCount, results.size());
        } else {
            applicationInputChangeTracker.recordKycVerifiedSnapshot(app);
            applicationRepository.save(app);
            boolean autoBureauEnabled = activeWorkflowConfigService.findActiveForApplication(app)
                    .map(workflow -> workflow.isBureauEnabled() && workflow.isAutoPullBureauAfterKycSuccess())
                    .orElse(true);
            if (autoBureauEnabled) {
                eventPublisher.publishEvent(
                        new AutoBureauPullRequestedEvent(applicationId, "KYC_WORKFLOW_SUCCESS"));
            } else {
                log.info("Skipping auto bureau pull for {} because workflow config disabled it", app.getApplicationNumber());
            }
        }

        // KYC-5: shadow Decision Policy evaluation — never changes production KYC / status / UW
        try {
            ShadowKycApplicationEvaluationFacade facade = shadowKycFacade.getIfAvailable();
            if (facade != null) {
                facade.afterKycWorkflowSafe(applicationId);
            }
        } catch (Exception ex) {
            log.warn("kyc_shadow_hook_swallowed app={} err={}", applicationId, ex.getMessage());
        }

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "totalSteps", results.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "allPassed", allPassed,
                "results", results
        );
        return StepResult.ok(out);
    }
}
