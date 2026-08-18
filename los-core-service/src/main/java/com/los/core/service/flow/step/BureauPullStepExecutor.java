package com.los.core.service.flow.step;

import com.los.core.creditintelligence.bureau.service.BureauIngestionService;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditControlKeys;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final CreditControlService creditControlService;
    private final IIntegrationRouterService integrationRouter;
    private final AuditService auditService;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final BureauIngestionService bureauIngestionService;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.BUREAU_PULL.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        boolean bureauEnabled = activeWorkflowConfigService.findActiveForApplication(app)
                .map(com.los.core.model.entity.WorkflowConfig::isBureauEnabled)
                .orElse(false);
        if (app.getWorkflowId() == null) {
            throw com.los.core.service.workflow.ApplicationConfigurationAuthority.notPinned(app);
        }
        if (!bureauEnabled) {
            throw new BusinessRuleException(
                    "Bureau pull is disabled for the workflow bound to this application",
                    "BUREAU_DISABLED",
                    "BUREAU_PULL",
                    Map.of("applicationId", applicationId.toString()));
        }

        KycStepResult latestBureauAttempt = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.BUREAU_PULL)
                .orElse(null);
        boolean forceFixtureIngest = context != null
                && context.get(com.los.core.service.integration.providers.impl.EquifaxBureauProvider.FIXTURE_SOURCE_KEY) != null;
        if (!forceFixtureIngest && latestBureauAttempt != null && latestBureauAttempt.getOutcome() == StepOutcome.SUCCESS) {
            Map<String, Object> reportData = latestBureauAttempt.getParsedData() != null
                    ? latestBureauAttempt.getParsedData()
                    : Map.of();
            return StepResult.ok(Map.of(
                    "applicationId", applicationId,
                    "success", true,
                    "creditScore", (int) latestBureauAttempt.getConfidenceScore(),
                    "transactionId", latestBureauAttempt.getTransactionId() != null
                            ? latestBureauAttempt.getTransactionId()
                            : "",
                    "reportData", reportData,
                    "alreadyAvailable", true
            ));
        }

        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        EffectiveUnderwritingContext ctx = creditControlService.resolveEffective(app, outcome);
        if (!ctx.kycPassEffective()) {
            @SuppressWarnings("unchecked")
            Object stepSummary = kycOutcome.getOrDefault("stepSummary", List.of());
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "BUREAU_BLOCKED",
                    null,
                    Map.of(
                            "status", app.getStatus().name(),
                            "reason", "KYC_OUTCOME_NOT_PASS",
                            "action", "BUREAU_PULL",
                            "kycOutcome", outcome,
                            "kycSource", ctx.kycSource(),
                            "stepSummary", stepSummary),
                    null,
                    "Bureau pull blocked: effective KYC not pass");
            throw new BusinessRuleException(
                    "Bureau pull blocked: KYC outcome is not acceptable for the selected KYC source",
                    "KYC_OUTCOME_NOT_PASS",
                    "BUREAU_PULL",
                    Map.of(
                            "status", app.getStatus().name(),
                            "kycOutcome", outcome,
                            "kycSource", ctx.kycSource(),
                            "stepSummary", stepSummary)
            );
        }

        Map<String, Object> borrowerInfo = ApplicantIdentityResolver.buildBureauBorrowerInfo(app);
        if (context != null) {
            Object fixtureSource = context.get(com.los.core.service.integration.providers.impl.EquifaxBureauProvider.FIXTURE_SOURCE_KEY);
            if (fixtureSource != null && !String.valueOf(fixtureSource).isBlank()) {
                borrowerInfo.put(
                        com.los.core.service.integration.providers.impl.EquifaxBureauProvider.FIXTURE_SOURCE_KEY,
                        String.valueOf(fixtureSource).trim());
            }
        }

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
                .attemptNumber(latestBureauAttempt != null ? latestBureauAttempt.getAttemptNumber() + 1 : 1)
                .completedAt(Instant.now())
                .build();
        kycStepResultRepository.save(stepResult);

        if (bureauResult.success()) {
            app.setBureauScore(bureauResult.creditScore());
            // BUREAU-P0-3: simulated pulls must not masquerade as PROVIDER in CreditControl.
            if (isSimulatedBureauResult(bureauResult.reportData())) {
                stampBureauScoreSource(app, CreditControlKeys.SRC_SIMULATED);
            } else {
                stampBureauScoreSource(app, CreditControlKeys.SRC_PROVIDER);
            }
            applicationRepository.save(app);
            // Phase C1: canonical bureau ingestion (never fail the pull)
            try {
                if (bureauIngestionService.isEnabledFor(app)) {
                    bureauIngestionService.ingestFromPull(
                            app,
                            bureauResult.reportData(),
                            bureauResult.transactionId(),
                            stepResult.getId());
                }
            } catch (Exception e) {
                log.warn("Bureau canonicalization hook failed (non-fatal) for {}: {}",
                        applicationId, e.getMessage());
            }
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

    private static boolean isSimulatedBureauResult(Map<String, Object> reportData) {
        if (reportData == null) {
            return false;
        }
        if (Boolean.TRUE.equals(reportData.get("simulated"))) {
            return true;
        }
        Object prov = reportData.get("dataProvenance");
        return prov != null && "SIMULATED".equalsIgnoreCase(String.valueOf(prov));
    }

    @SuppressWarnings("unchecked")
    private static void stampBureauScoreSource(LoanApplication app, String source) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        Map<String, Object> cc = fi.get(CreditControlKeys.ROOT) instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : new LinkedHashMap<>();
        Map<String, Object> ds = cc.get(CreditControlKeys.DECISION_SOURCES) instanceof Map<?, ?> rawDs
                ? new LinkedHashMap<>((Map<String, Object>) rawDs)
                : new LinkedHashMap<>();
        ds.put("bureauScoreSource", source);
        cc.put(CreditControlKeys.DECISION_SOURCES, ds);
        fi.put(CreditControlKeys.ROOT, cc);
        app.setFinancialInfo(fi);
    }
}
