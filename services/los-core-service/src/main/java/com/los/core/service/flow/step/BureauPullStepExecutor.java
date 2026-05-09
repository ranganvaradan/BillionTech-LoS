package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kyc.IKycOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#BUREAU_PULL} — bureau pull + persist step result. Moved from
 * {@code LoanApplicationFlowService#pullBureauReport}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BureauPullStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final KycStepResultRepository kycStepResultRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final IIntegrationRouterService integrationRouter;
    private final AuditService auditService;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.BUREAU_PULL.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));

        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        if (!"PASS".equalsIgnoreCase(outcome)) {
            @SuppressWarnings("unchecked")
            Object stepSummary = kycOutcome.getOrDefault("stepSummary", List.of());
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "BUREAU_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KYC_OUTCOME_NOT_PASS", "action", "BUREAU_PULL", "kycOutcome", outcome, "stepSummary", stepSummary),
                    null,
                    "Bureau pull blocked: KYC outcome is " + outcome);
            throw new BusinessRuleException(
                    "Bureau pull blocked: KYC outcome is " + outcome,
                    "KYC_OUTCOME_NOT_PASS",
                    "BUREAU_PULL",
                    Map.of("status", app.getStatus().name(), "kycOutcome", outcome, "stepSummary", stepSummary)
            );
        }

        Map<String, Object> borrowerInfo = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            borrowerInfo.putAll(app.getPersonalInfo());
        }
        borrowerInfo.put("applicationId", applicationId.toString());
        borrowerInfo.put("applicationNumber", app.getApplicationNumber());

        IIntegrationRouterService.BureauRouteResult bureauResult = integrationRouter.routeBureauPull(borrowerInfo);

        KycStepResult stepResult = KycStepResult.builder()
                .applicationId(applicationId)
                .stepType(KycStepType.BUREAU_PULL)
                .provider(com.los.core.model.enums.ProviderType.EQUIFAX)
                .outcome(bureauResult.success() ? StepOutcome.SUCCESS : StepOutcome.FAILURE)
                .confidenceScore(bureauResult.success() ? bureauResult.creditScore() : 0.0)
                .parsedData(bureauResult.reportData())
                .transactionId(bureauResult.transactionId())
                .errorMessage(bureauResult.errorMessage())
                .attemptNumber(1)
                .completedAt(Instant.now())
                .build();
        kycStepResultRepository.save(stepResult);

        if (bureauResult.success()) {
            app.setBureauScore(bureauResult.creditScore());
            applicationRepository.save(app);
        }

        auditService.logEvent(applicationId, "BUREAU_PULL",
                Map.of("success", bureauResult.success(), "creditScore", bureauResult.creditScore(),
                        "transactionId", bureauResult.transactionId() != null ? bureauResult.transactionId() : ""));

        log.info("Bureau pull for {} — score: {}, success: {}",
                app.getApplicationNumber(), bureauResult.creditScore(), bureauResult.success());

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "success", bureauResult.success(),
                "creditScore", bureauResult.creditScore(),
                "transactionId", bureauResult.transactionId() != null ? bureauResult.transactionId() : "",
                "reportData", bureauResult.reportData() != null ? bureauResult.reportData() : Map.of()
        );
        return StepResult.ok(out);
    }
}
