package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.domain.MismatchClassification;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator;
import com.los.core.creditintelligence.banking.domain.BankingMismatchClassification;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.banking.service.CanonicalBankingRuleEvaluator;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.evaluation.ConfigFreezeService;
import com.los.core.creditintelligence.evaluation.EvaluationContextFactory;
import com.los.core.creditintelligence.evaluation.EvaluationContextHolder;
import com.los.core.creditintelligence.evaluation.FrozenPolicyExecutionAdapter;
import com.los.core.creditintelligence.evaluation.MetricResultSetService;
import com.los.core.creditintelligence.evaluation.PureCanonicalEvaluationService;
import com.los.core.creditintelligence.evaluation.ReconciliationResultSetService;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import com.los.core.creditintelligence.evaluation.repository.CiEvaluationContextRepository;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.GstMismatchClassification;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository;
import com.los.core.creditintelligence.gst.service.CanonicalGstRuleEvaluator;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.TaxMismatchClassification;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.service.CanonicalTaxRuleEvaluator;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationMismatchClassification;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.service.CanonicalReconciliationRuleEvaluator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationOrchestrator;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.CiEvaluationStage;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.domain.ComparisonStatus;
import com.los.core.creditintelligence.domain.DataStatus;
import com.los.core.creditintelligence.domain.EvaluationStatus;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.repository.CiEvaluationStageRepository;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.repository.CiStandardRuleResultRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import com.los.core.service.underwriting.UnderwritingRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Non-authoritative shadow evaluation from frozen snapshot + policy.
 * Must never mutate LoanApplication, CAM, sanction, notifications, or AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowCreditEvaluationService {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CiEvaluationStageRepository stageRepository;
    private final CiStandardRuleResultRepository ruleResultRepository;
    private final LegacyUnderwritingContextAdapter adapter;
    private final UnderwritingRuleEngine underwritingRuleEngine;
    private final ScorecardPolicyEngine scorecardPolicyEngine;
    private final CreditIntelligenceProperties properties;
    private final AuditService auditService;
    private final CanonicalBureauRuleEvaluator canonicalBureauRuleEvaluator;
    private final CiMetricResultRepository metricResultRepository;
    private final CanonicalGstRuleEvaluator canonicalGstRuleEvaluator;
    private final CiGstRegistrationRepository gstRegistrationRepository;
    private final CanonicalBankingRuleEvaluator canonicalBankingRuleEvaluator;
    private final CiBankAccountRepository bankAccountRepository;
    private final CanonicalTaxRuleEvaluator canonicalTaxRuleEvaluator;
    private final CiItrReturnRepository itrReturnRepository;
    private final ReconciliationIngestionService reconciliationIngestionService;
    private final CanonicalReconciliationRuleEvaluator canonicalReconciliationRuleEvaluator;
    private final ConfigFreezeService configFreezeService;
    private final MetricResultSetService metricResultSetService;
    private final ReconciliationResultSetService reconciliationResultSetService;
    private final EvaluationContextFactory evaluationContextFactory;
    private final FrozenPolicyExecutionAdapter frozenPolicyExecutionAdapter;
    private final PureCanonicalEvaluationService pureCanonicalEvaluationService;
    private final CiEvaluationContextRepository evaluationContextRepository;

    public record ShadowResult(CiCreditEvaluation shadow, CiCreditEvaluation productionReference) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ShadowResult> evaluate(
            UUID snapshotId,
            UUID policyVersionId,
            UUID applicationId,
            UnderwritingEvaluation productionEval,
            String productionOutcome) {
        return evaluateInternal(snapshotId, policyVersionId, applicationId, productionEval, productionOutcome, null);
    }

    /**
     * REPLAY path: evaluate solely from an existing EvaluationContext id (flags on).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ShadowResult> replay(UUID evaluationContextId) {
        CiEvaluationContext ctx = evaluationContextRepository.findById(evaluationContextId)
                .orElseThrow(() -> new IllegalArgumentException("EvaluationContext not found: " + evaluationContextId));
        return evaluateInternal(
                ctx.getFactSnapshotId(),
                ctx.getPolicyVersionId(),
                ctx.getApplicationId(),
                null,
                null,
                ctx);
    }

    private Optional<ShadowResult> evaluateInternal(
            UUID snapshotId,
            UUID policyVersionId,
            UUID applicationId,
            UnderwritingEvaluation productionEval,
            String productionOutcome,
            CiEvaluationContext existingContext) {
        Instant started = Instant.now();
        UUID tenantId = properties.getDefaultTenantId();
        CiCreditEvaluation shadow = null;
        CiEvaluationContext evalCtx = existingContext;
        try {
            CiFactSnapshot snapshot = snapshotRepository.findById(snapshotId)
                    .orElseThrow(() -> new IllegalArgumentException("Snapshot not found"));
            CiPolicyVersion policyVersion = policyVersionId != null
                    ? policyVersionRepository.findById(policyVersionId)
                            .orElseThrow(() -> new IllegalArgumentException("Policy version not found"))
                    : (evalCtx != null && evalCtx.getPolicyVersionId() != null
                            ? policyVersionRepository.findById(evalCtx.getPolicyVersionId()).orElse(null)
                            : null);
            if (policyVersion == null) {
                throw new IllegalArgumentException("Policy version not found");
            }
            if (policyVersionId == null) {
                policyVersionId = policyVersion.getId();
            }

            if (evalCtx == null && isEvaluationContextEnabled()) {
                evalCtx = createEvaluationContext(tenantId, applicationId, snapshotId, policyVersionId);
            }
            if (evalCtx != null) {
                EvaluationContextHolder.set(evalCtx);
            }

            String evaluationType = existingContext != null
                    ? EvaluationType.REPLAY.name()
                    : EvaluationType.SHADOW.name();

            auditService.logEvent(
                    applicationId,
                    "CREDIT_INTELLIGENCE",
                    existingContext != null ? "underwriting.replay.started" : "underwriting.shadow.started",
                    null,
                    null,
                    Map.of(
                            "snapshotId", snapshotId.toString(),
                            "policyVersionId", policyVersionId.toString(),
                            "evaluationContextId", evalCtx != null ? evalCtx.getId().toString() : ""),
                    existingContext != null ? "Replay evaluation started" : "Shadow evaluation started");

            Map<String, Object> purityMeta = new LinkedHashMap<>();
            if (evalCtx != null) {
                purityMeta.put("evaluationContextId", evalCtx.getId().toString());
                purityMeta.put("configFreezeId", evalCtx.getConfigFreezeId().toString());
                if (evalCtx.getMetricResultSetId() != null) {
                    purityMeta.put("metricResultSetId", evalCtx.getMetricResultSetId().toString());
                }
            }

            shadow = evaluationRepository.save(CiCreditEvaluation.builder()
                    .tenantId(tenantId)
                    .applicationId(applicationId)
                    .factSnapshotId(snapshotId)
                    .policyVersionId(policyVersionId)
                    .evaluationType(evaluationType)
                    .status(EvaluationStatus.STARTED.name())
                    .authoritative(false)
                    .productionEvaluationId(productionEval != null ? productionEval.getId() : null)
                    .orchestrationVersion(PolicyVersionResolver.ORCHESTRATION_VERSION)
                    .startedAt(started)
                    .metadata(purityMeta)
                    .build());

            CiCreditEvaluation prodRef = null;
            if (productionEval != null) {
                prodRef = evaluationRepository.save(CiCreditEvaluation.builder()
                        .tenantId(tenantId)
                        .applicationId(applicationId)
                        .factSnapshotId(snapshotId)
                        .policyVersionId(policyVersionId)
                        .evaluationType(EvaluationType.PRODUCTION_REFERENCE.name())
                        .status(EvaluationStatus.COMPLETED.name())
                        .authoritative(true)
                        .productionEvaluationId(productionEval.getId())
                        .orchestrationVersion(PolicyVersionResolver.ORCHESTRATION_VERSION)
                        .startedAt(started)
                        .completedAt(Instant.now())
                        .overallOutcome(normalizeOutcome(
                                productionOutcome != null ? productionOutcome : productionEval.getAggregateDecision()))
                        .comparisonStatus(ComparisonStatus.NOT_COMPARED.name())
                        .metadata(Map.of("productionEvaluationId", productionEval.getId().toString()))
                        .build());
            }

            LegacyUnderwritingContextAdapter.AdapterResult adapted = adapter.adapt(snapshotId, evalCtx);
            EffectiveUnderwritingContext ctx = adapted.context();
            LoanApplication stub = adapter.toLoanApplicationStub(applicationId, adapted);
            String kycOutcome = adapted.kycOutcome();

            Instant stageStart = Instant.now();
            MultiRuleEvalResult ruleMulti;
            if (properties.getFrozenPolicyExecution().isEnabled() && policyVersion.getPolicyContent() != null) {
                ruleMulti = frozenPolicyExecutionAdapter.evaluateHardRules(
                        stub, ctx, kycOutcome, policyVersion.getPolicyContent());
            } else {
                ruleMulti = underwritingRuleEngine.evaluateAll(stub, ctx, kycOutcome);
            }
            boolean hardRuleTriggered = ruleMulti.perRule() != null
                    && ruleMulti.perRule().stream().anyMatch(p -> "HARD_RULE".equalsIgnoreCase(p.kind()));

            Optional<ScorecardPolicyEngine.ScorecardEvalResult> scOpt =
                    properties.getFrozenPolicyExecution().isEnabled()
                            ? Optional.empty()
                            : scorecardPolicyEngine.evaluate(stub, ctx, kycOutcome);

            MultiRuleEvalResult multi;
            if (hardRuleTriggered && ruleMulti.hasAnyRule()) {
                multi = ruleMulti;
            } else if (scOpt.isPresent()) {
                multi = scOpt.get().multi();
            } else {
                multi = ruleMulti;
            }

            if (evalCtx != null && isEvaluationContextEnabled()) {
                try {
                    PureCanonicalEvaluationService.PureEvalResult pure =
                            pureCanonicalEvaluationService.evaluate(evalCtx);
                    purityMeta.put("deterministicEvaluationHash", pure.deterministicEvaluationHash());
                } catch (Exception ex) {
                    log.warn("Pure canonical hash skipped for {}: {}", applicationId, ex.getMessage());
                }
            }

            String overall = normalizeOutcome(multi.aggregateCreditDecision());

            CiEvaluationStage rulesStage = stageRepository.save(CiEvaluationStage.builder()
                    .evaluationId(shadow.getId())
                    .stageCode("HARD_RULES_AND_RULES")
                    .sequence(1)
                    .status(EvaluationStatus.COMPLETED.name())
                    .outcome(mapRuleOutcome(overall))
                    .startedAt(stageStart)
                    .completedAt(Instant.now())
                    .trace(Map.of(
                            "engine", "UnderwritingRuleEngine",
                            "hardRuleTriggered", hardRuleTriggered,
                            "ruleCount", multi.perRule() != null ? multi.perRule().size() : 0))
                    .build());

            CiEvaluationStage scorecardStage = null;
            if (scOpt.isPresent()) {
                scorecardStage = stageRepository.save(CiEvaluationStage.builder()
                        .evaluationId(shadow.getId())
                        .stageCode("SCORECARD")
                        .sequence(2)
                        .status(EvaluationStatus.COMPLETED.name())
                        .outcome(mapRuleOutcome(overall))
                        .startedAt(stageStart)
                        .completedAt(Instant.now())
                        .trace(Map.of(
                                "engine", "ScorecardPolicyEngine",
                                "scorecardId", scOpt.get().scorecardId() != null
                                        ? scOpt.get().scorecardId().toString() : null,
                                "mergePrecedence", PolicyVersionResolver.MERGE_PRECEDENCE))
                        .build());
            }

            persistRuleResults(shadow, policyVersionId, rulesStage, scorecardStage, multi, adapted.defaultedPaths());

            Map<String, Object> bureauComparison = evaluateCanonicalBureauShadow(
                    shadow, policyVersionId, rulesStage, applicationId, adapted);
            Map<String, Object> gstComparison = evaluateCanonicalGstShadow(
                    shadow, policyVersionId, rulesStage, applicationId, adapted);
            Map<String, Object> bankingComparison = evaluateCanonicalBankingShadow(
                    shadow, policyVersionId, rulesStage, applicationId, adapted);
            Map<String, Object> taxComparison = evaluateCanonicalTaxShadow(
                    shadow, policyVersionId, rulesStage, applicationId, adapted);
            Map<String, Object> reconciliationComparison = evaluateCanonicalReconciliationShadow(
                    shadow, policyVersionId, rulesStage, applicationId, adapted);

            String comparison = ComparisonStatus.NOT_COMPARED.name();
            String prodOutcomeNorm = normalizeOutcome(
                    productionOutcome != null
                            ? productionOutcome
                            : (productionEval != null ? productionEval.getAggregateDecision() : null));
            if (prodOutcomeNorm != null) {
                comparison = outcomesMatch(overall, prodOutcomeNorm)
                        ? ComparisonStatus.MATCH.name()
                        : ComparisonStatus.MISMATCH.name();
            } else {
                comparison = ComparisonStatus.PRODUCTION_UNAVAILABLE.name();
            }

            shadow.setStatus(EvaluationStatus.COMPLETED.name());
            shadow.setCompletedAt(Instant.now());
            shadow.setOverallOutcome(overall);
            shadow.setComparisonStatus(comparison);
            Map<String, Object> shadowMeta = new LinkedHashMap<>(purityMeta);
            shadowMeta.put("defaultedPathCount", adapted.defaultedPaths().size());
            shadowMeta.put("durationMs", Instant.now().toEpochMilli() - started.toEpochMilli());
            shadowMeta.put("aggregateRiskScore", multi.aggregateRiskScore());
            if (bureauComparison != null && !bureauComparison.isEmpty()) {
                shadowMeta.put("bureauComparison", bureauComparison);
            }
            if (gstComparison != null && !gstComparison.isEmpty()) {
                shadowMeta.put("gstComparison", gstComparison);
            }
            if (bankingComparison != null && !bankingComparison.isEmpty()) {
                shadowMeta.put("bankingComparison", bankingComparison);
            }
            if (taxComparison != null && !taxComparison.isEmpty()) {
                shadowMeta.put("taxComparison", taxComparison);
            }
            if (reconciliationComparison != null && !reconciliationComparison.isEmpty()) {
                shadowMeta.put("reconciliationComparison", reconciliationComparison);
                if (reconciliationComparison.get("creditEvidenceSummary") != null) {
                    shadowMeta.put("creditEvidenceSummary", reconciliationComparison.get("creditEvidenceSummary"));
                }
            }
            if (adapted.metadata() != null && adapted.metadata().containsKey("adapterFlags")) {
                shadowMeta.put("adapterFlags", adapted.metadata().get("adapterFlags"));
            }
            shadow.setMetadata(shadowMeta);
            shadow = evaluationRepository.save(shadow);

            auditService.logEvent(
                    applicationId,
                    "CREDIT_INTELLIGENCE",
                    "underwriting.shadow.completed",
                    null,
                    null,
                    Map.of(
                            "evaluationId", shadow.getId().toString(),
                            "snapshotId", snapshotId.toString(),
                            "policyVersionId", policyVersionId.toString(),
                            "overallOutcome", overall,
                            "comparisonStatus", comparison),
                    "Shadow evaluation completed");

            if (ComparisonStatus.MISMATCH.name().equals(comparison)) {
                auditService.logEvent(
                        applicationId,
                        "CREDIT_INTELLIGENCE",
                        "underwriting.shadow.mismatch",
                        null,
                        null,
                        Map.of(
                                "evaluationId", shadow.getId().toString(),
                                "shadowOutcome", overall,
                                "productionOutcome", prodOutcomeNorm != null ? prodOutcomeNorm : ""),
                        "Shadow vs production mismatch");
            }

            return Optional.of(new ShadowResult(shadow, prodRef));
        } catch (Exception ex) {
            log.warn("Shadow evaluation failed for application {}: {}", applicationId, ex.getMessage());
            try {
                if (shadow != null && shadow.getId() != null) {
                    shadow.setStatus(EvaluationStatus.FAILED.name());
                    shadow.setCompletedAt(Instant.now());
                    shadow.setErrorCode("SHADOW_FAILED");
                    shadow.setErrorDetails(Map.of("message", safeMessage(ex)));
                    shadow.setComparisonStatus(ComparisonStatus.NOT_COMPARED.name());
                    evaluationRepository.save(shadow);
                } else {
                    evaluationRepository.save(CiCreditEvaluation.builder()
                            .tenantId(tenantId)
                            .applicationId(applicationId)
                            .factSnapshotId(snapshotId)
                            .policyVersionId(policyVersionId != null ? policyVersionId : UUID.randomUUID())
                            .evaluationType(EvaluationType.SHADOW.name())
                            .status(EvaluationStatus.FAILED.name())
                            .authoritative(false)
                            .orchestrationVersion(PolicyVersionResolver.ORCHESTRATION_VERSION)
                            .startedAt(started)
                            .completedAt(Instant.now())
                            .errorCode("SHADOW_FAILED")
                            .errorDetails(Map.of("message", safeMessage(ex)))
                            .comparisonStatus(ComparisonStatus.NOT_COMPARED.name())
                            .metadata(Map.of())
                            .build());
                }
            } catch (Exception persistEx) {
                log.warn("Failed to persist shadow failure row: {}", persistEx.getMessage());
            }
            auditService.logEvent(
                    applicationId,
                    "CREDIT_INTELLIGENCE",
                    "underwriting.shadow.failed",
                    null,
                    null,
                    Map.of(
                            "snapshotId", snapshotId != null ? snapshotId.toString() : "",
                            "errorCode", "SHADOW_FAILED"),
                    "Shadow evaluation failed");
            return Optional.empty();
        } finally {
            EvaluationContextHolder.clear();
        }
    }

    private boolean isEvaluationContextEnabled() {
        return properties.getEvaluationContext().isEnabled()
                || properties.getConfigFreeze().isEnabled();
    }

    private CiEvaluationContext createEvaluationContext(
            UUID tenantId, UUID applicationId, UUID snapshotId, UUID policyVersionId) {
        var freeze = configFreezeService.freezeCurrent(tenantId);
        List<String> metricCodes = List.of(
                BureauMetricService.LIVE_UNSECURED,
                GstMetricService.TRAILING_12M,
                BankingMetricService.ADB_3M,
                BankingMetricService.MONTHLY_OBL,
                BankingMetricService.ADJ_12M,
                TaxMetricService.TURNOVER_LATEST,
                TaxMetricService.TOTAL_INCOME_LATEST,
                TaxMetricService.PAT_ABS,
                TaxMetricService.TOL_ABS,
                TaxMetricService.TNW_ABS);
        var metricSet = metricResultSetService.pinLatestForApplication(
                tenantId, applicationId, snapshotId, metricCodes);
        var reconSet = reconciliationResultSetService.pinLatestForApplication(
                tenantId, applicationId, snapshotId, metricSet.getId(), List.of());
        FixedEvaluationClock clock = new FixedEvaluationClock(
                Instant.now(), java.time.ZoneId.of("Asia/Kolkata"));
        return evaluationContextFactory.create(
                tenantId,
                applicationId,
                snapshotId,
                policyVersionId,
                freeze.getId(),
                clock.today(),
                clock,
                metricSet.getId(),
                reconSet != null ? reconSet.getId() : null,
                Map.of("source", "SHADOW"));
    }

    private void persistRuleResults(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            CiEvaluationStage scorecardStage,
            MultiRuleEvalResult multi,
            Set<String> defaultedPaths) {
        if (multi.perRule() == null) {
            return;
        }
        Instant now = Instant.now();
        for (MultiRuleEvalResult.PerRuleEval p : multi.perRule()) {
            boolean fromScorecard = "SCORECARD".equalsIgnoreCase(p.kind());
            UUID stageId = fromScorecard && scorecardStage != null
                    ? scorecardStage.getId()
                    : (rulesStage != null ? rulesStage.getId() : null);
            String engineName = fromScorecard ? "ScorecardPolicyEngine" : "UnderwritingRuleEngine";

            List<Object> factRefs = new ArrayList<>();
            boolean usedDefaulted = false;
            if (p.sourceValuesUsed() != null) {
                for (String key : p.sourceValuesUsed().keySet()) {
                    String path = "compat." + key;
                    factRefs.add(path);
                    if (defaultedPaths.contains(path) || defaultedPaths.contains(key)) {
                        usedDefaulted = true;
                    }
                }
            }
            if (p.matchedConditions() != null) {
                for (String key : p.matchedConditions().keySet()) {
                    String path = "compat." + key;
                    if (!factRefs.contains(path)) {
                        factRefs.add(path);
                    }
                    if (defaultedPaths.contains(path)) {
                        usedDefaulted = true;
                    }
                }
            }

            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("kind", p.kind());
            trace.put("policyDecision", p.policyDecision());
            trace.put("creditDecision", p.creditDecision());
            if (p.reasons() != null) {
                trace.put("reasons", p.reasons());
            }
            if (p.matchedConditions() != null) {
                trace.put("matchedConditions", p.matchedConditions());
            }
            if (p.sourceValuesUsed() != null) {
                trace.put("sourceValuesUsed", p.sourceValuesUsed());
            }
            trace.put("limitations", "Threshold/operator detail may be incomplete for legacy engines");

            ruleResultRepository.save(CiStandardRuleResult.builder()
                    .evaluationId(shadow.getId())
                    .evaluationStageId(stageId)
                    .ruleId(p.ruleId() != null ? p.ruleId() : "UNKNOWN")
                    .ruleVersion("1")
                    .policyVersionId(policyVersionId)
                    .category(p.kind())
                    .ruleType(p.kind())
                    .engineName(engineName)
                    .engineVersion("LEGACY")
                    .outcome(mapRuleOutcome(p.creditDecision()))
                    .severity(null)
                    .actualValue(p.sourceValuesUsed())
                    .reasonCode(p.creditDecision())
                    .explanation(p.reasons() != null && !p.reasons().isEmpty() ? p.reasons().get(0) : null)
                    .factReferences(factRefs)
                    .sourceReferences(List.of())
                    .dataStatus(usedDefaulted ? DataStatus.DEFAULTED.name() : DataStatus.AVAILABLE.name())
                    .overrideAllowed(false)
                    .humanReviewRequired("MANUAL_REVIEW".equalsIgnoreCase(p.creditDecision())
                            || "REFER".equalsIgnoreCase(mapRuleOutcome(p.creditDecision())))
                    .executedAt(now)
                    .trace(trace)
                    .build());
        }
    }

    private Map<String, Object> evaluateCanonicalBureauShadow(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            UUID applicationId,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        Map<String, Object> block = new LinkedHashMap<>();
        try {
            Optional<CiMetricResult> metricOpt = metricResultRepository
                    .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                            applicationId, BureauMetricService.LIVE_UNSECURED);
            int threshold = properties.getCanonicalization().getBureau().getLiveUnsecuredThreshold();
            CanonicalBureauRuleEvaluator.RuleEvalResult eval =
                    canonicalBureauRuleEvaluator.evaluate(metricOpt.orElse(null), threshold);

            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("v", eval.value());
            actual.put("threshold", eval.threshold());

            String dataStatus = RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())
                    ? DataStatus.MISSING.name()
                    : DataStatus.AVAILABLE.name();

            ruleResultRepository.save(CiStandardRuleResult.builder()
                    .evaluationId(shadow.getId())
                    .evaluationStageId(rulesStage != null ? rulesStage.getId() : null)
                    .ruleId(CanonicalBureauRuleEvaluator.RULE_ID)
                    .ruleVersion(CanonicalBureauRuleEvaluator.RULE_VERSION)
                    .policyVersionId(policyVersionId)
                    .category("HARD_RULE")
                    .ruleType("CANONICAL_BUREAU")
                    .engineName("CanonicalBureauRuleEvaluator")
                    .engineVersion(CanonicalBureauRuleEvaluator.RULE_VERSION)
                    .outcome(eval.outcome())
                    .actualValue(actual)
                    .reasonCode(eval.outcome())
                    .explanation("Canonical HARD_LIVE_UNSECURED vs threshold " + threshold)
                    .factReferences(List.of("bureau.live_unsecured_loan_count"))
                    .sourceReferences(List.of())
                    .dataStatus(dataStatus)
                    .overrideAllowed(false)
                    .humanReviewRequired(false)
                    .executedAt(Instant.now())
                    .trace(Map.of(
                            "versions", eval.versions(),
                            "evidence", eval.evidence()))
                    .build());

            BigDecimal legacyValue = adapted.context() != null && adapted.context().scorecard() != null
                    ? adapted.context().scorecard().get("LIVE_UNSECURED_LOAN_COUNT")
                    : null;
            List<String> mismatchClasses = classifyBureauMismatch(legacyValue, eval, adapted);

            block.put("ruleId", CanonicalBureauRuleEvaluator.RULE_ID);
            block.put("canonicalOutcome", eval.outcome());
            block.put("canonicalValue", eval.value());
            block.put("threshold", threshold);
            block.put("legacyValue", legacyValue != null ? legacyValue.intValue() : null);
            block.put("versions", eval.versions());
            block.put("mismatchClassifications", mismatchClasses);
            block.put("useForShadowRules", properties.getCanonicalization().getBureau().isUseForShadowRules());
        } catch (Exception e) {
            log.warn("Canonical bureau shadow comparison failed: {}", e.getMessage());
            block.put("error", "BUREAU_COMPARISON_FAILED");
        }
        return block;
    }

    private static List<String> classifyBureauMismatch(
            BigDecimal legacyValue,
            CanonicalBureauRuleEvaluator.RuleEvalResult eval,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        List<String> classes = new ArrayList<>();
        if (RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())) {
            classes.add(MismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
        }
        if (adapted.metadata() != null) {
            Object flags = adapted.metadata().get("adapterFlags");
            if (flags instanceof Map<?, ?> fm) {
                if (Boolean.TRUE.equals(fm.get("canonicalDataInsufficient"))) {
                    classes.add(MismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
                }
                if (Boolean.TRUE.equals(fm.get("fallbackUsed"))) {
                    classes.add(MismatchClassification.LEGACY_DEFAULT_USED.name());
                }
            }
            if (adapted.defaultedPaths() != null
                    && adapted.defaultedPaths().contains("compat.LIVE_UNSECURED_LOAN_COUNT")) {
                classes.add(MismatchClassification.LEGACY_DEFAULT_USED.name());
            }
        }
        if (legacyValue != null && eval.value() != null
                && legacyValue.intValue() != eval.value()) {
            classes.add(MismatchClassification.CANONICAL_TRADELINE_COUNT_DIFFERENT.name());
        }
        if (classes.isEmpty() && legacyValue != null && eval.value() != null
                && legacyValue.intValue() == eval.value()) {
            return List.of();
        }
        if (classes.isEmpty()) {
            classes.add(MismatchClassification.OTHER.name());
        }
        return classes.stream().distinct().toList();
    }

    private Map<String, Object> evaluateCanonicalGstShadow(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            UUID applicationId,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        Map<String, Object> block = new LinkedHashMap<>();
        try {
            List<CiGstRegistration> regs =
                    gstRegistrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
            Map<String, CiMetricResult> byCode = new LinkedHashMap<>();
            for (CiMetricResult m : metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
                if (m.getMetricCode() != null && m.getMetricCode().startsWith("gst.")) {
                    byCode.putIfAbsent(m.getMetricCode(), m);
                }
            }
            BigDecimal threshold = properties.getCanonicalization().getGst().getTurnoverEligibilityThreshold();
            List<CanonicalGstRuleEvaluator.RuleEvalResult> evals = canonicalGstRuleEvaluator.evaluateAll(
                    regs, byCode, threshold, 60, 3);

            List<Map<String, Object>> ruleSummaries = new ArrayList<>();
            for (CanonicalGstRuleEvaluator.RuleEvalResult eval : evals) {
                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("v", eval.value());
                actual.put("threshold", eval.threshold());
                String dataStatus = RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())
                        ? DataStatus.MISSING.name()
                        : DataStatus.AVAILABLE.name();
                ruleResultRepository.save(CiStandardRuleResult.builder()
                        .evaluationId(shadow.getId())
                        .evaluationStageId(rulesStage != null ? rulesStage.getId() : null)
                        .ruleId(eval.ruleId())
                        .ruleVersion(eval.ruleVersion())
                        .policyVersionId(policyVersionId)
                        .category("HARD_RULE")
                        .ruleType("CANONICAL_GST")
                        .engineName("CanonicalGstRuleEvaluator")
                        .engineVersion(eval.ruleVersion())
                        .outcome(eval.outcome())
                        .actualValue(actual)
                        .reasonCode(eval.outcome())
                        .explanation("Canonical GST shadow rule " + eval.ruleId())
                        .factReferences(List.of("gst.turnover.trailing_12m"))
                        .sourceReferences(List.of())
                        .dataStatus(dataStatus)
                        .overrideAllowed(false)
                        .humanReviewRequired(false)
                        .executedAt(Instant.now())
                        .trace(Map.of(
                                "versions", eval.versions(),
                                "evidence", eval.evidence()))
                        .build());
                ruleSummaries.add(Map.of(
                        "ruleId", eval.ruleId(),
                        "outcome", eval.outcome(),
                        "value", eval.value() != null ? eval.value() : "",
                        "threshold", eval.threshold() != null ? eval.threshold() : ""));
            }

            BigDecimal legacyTurnover = adapted.context() != null && adapted.context().scorecard() != null
                    ? adapted.context().scorecard().get("ANNUAL_GST_TURNOVER")
                    : null;
            CiMetricResult t12 = byCode.get(GstMetricService.TRAILING_12M);
            List<String> mismatchClasses = classifyGstMismatch(legacyTurnover, t12, adapted);

            block.put("rules", ruleSummaries);
            block.put("legacyAnnualGstTurnover", legacyTurnover);
            block.put("canonicalTrailing12m", t12 != null ? t12.getValue() : null);
            block.put("canonicalOutcome", t12 != null ? t12.getOutcome() : null);
            block.put("versions", CanonicalGstRuleEvaluator.frozenVersions());
            block.put("mismatchClassifications", mismatchClasses);
            block.put("useForShadowRules", properties.getCanonicalization().getGst().isUseForShadowRules());
            block.put("note", "Production CreditControl ANNUAL_GST_TURNOVER / SCF 52M gap unchanged");
        } catch (Exception e) {
            log.warn("Canonical GST shadow comparison failed: {}", e.getMessage());
            block.put("error", "GST_COMPARISON_FAILED");
        }
        return block;
    }

    private static List<String> classifyGstMismatch(
            BigDecimal legacyValue,
            CiMetricResult trailing12m,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        List<String> classes = new ArrayList<>();
        if (trailing12m == null
                || GstMetricOutcome.DATA_INSUFFICIENT.name().equals(trailing12m.getOutcome())) {
            classes.add(GstMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
        }
        if (adapted.metadata() != null) {
            Object flags = adapted.metadata().get("adapterFlags");
            if (flags instanceof Map<?, ?> fm) {
                Object gst = fm.get("gst");
                if (gst instanceof Map<?, ?> gf) {
                    if (Boolean.TRUE.equals(gf.get("canonicalDataInsufficient"))) {
                        classes.add(GstMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
                    }
                    if (Boolean.TRUE.equals(gf.get("fallbackUsed"))) {
                        classes.add(GstMismatchClassification.LEGACY_DEFAULT_USED.name());
                    }
                }
            }
            if (adapted.defaultedPaths() != null
                    && adapted.defaultedPaths().contains("compat.ANNUAL_GST_TURNOVER")) {
                classes.add(GstMismatchClassification.LEGACY_DEFAULT_USED.name());
            }
        }
        if (legacyValue != null && trailing12m != null && trailing12m.getValue() != null
                && trailing12m.getValue().get("v") != null
                && !GstMetricOutcome.DATA_INSUFFICIENT.name().equals(trailing12m.getOutcome())) {
            try {
                BigDecimal canon = new BigDecimal(String.valueOf(trailing12m.getValue().get("v")));
                if (legacyValue.compareTo(canon) != 0) {
                    classes.add(GstMismatchClassification.CANONICAL_TURNOVER_DIFFERENT.name());
                }
            } catch (Exception ignored) {
                classes.add(GstMismatchClassification.PARSER_DIFFERENCE.name());
            }
        }
        if (classes.isEmpty()) {
            return List.of();
        }
        return classes.stream().distinct().toList();
    }

    private Map<String, Object> evaluateCanonicalBankingShadow(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            UUID applicationId,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        Map<String, Object> block = new LinkedHashMap<>();
        try {
            List<CiBankAccount> accounts =
                    bankAccountRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
            Map<String, CiMetricResult> byCode = new LinkedHashMap<>();
            for (CiMetricResult m : metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
                if (m.getMetricCode() != null && m.getMetricCode().startsWith("banking.")) {
                    byCode.putIfAbsent(m.getMetricCode(), m);
                }
            }
            var cfg = properties.getCanonicalization().getBanking();
            List<CanonicalBankingRuleEvaluator.RuleEvalResult> evals = canonicalBankingRuleEvaluator.evaluateAll(
                    accounts, byCode,
                    cfg.getAbbMinimum(),
                    null,
                    cfg.getChequeReturnMax3m(),
                    cfg.getNachReturnMax3m(),
                    cfg.getCashDepositRatioWarningPct(),
                    cfg.getOdUtilisationWarningPct());

            List<Map<String, Object>> ruleSummaries = new ArrayList<>();
            for (CanonicalBankingRuleEvaluator.RuleEvalResult eval : evals) {
                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("v", eval.value());
                actual.put("threshold", eval.threshold());
                String dataStatus = RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())
                        ? DataStatus.MISSING.name()
                        : DataStatus.AVAILABLE.name();
                ruleResultRepository.save(CiStandardRuleResult.builder()
                        .evaluationId(shadow.getId())
                        .evaluationStageId(rulesStage != null ? rulesStage.getId() : null)
                        .ruleId(eval.ruleId())
                        .ruleVersion(eval.ruleVersion())
                        .policyVersionId(policyVersionId)
                        .category("HARD_RULE")
                        .ruleType("CANONICAL_BANKING")
                        .engineName("CanonicalBankingRuleEvaluator")
                        .engineVersion(eval.ruleVersion())
                        .outcome(eval.outcome())
                        .actualValue(actual)
                        .reasonCode(eval.outcome())
                        .explanation("Canonical banking shadow rule " + eval.ruleId())
                        .factReferences(List.of("banking.balance.average_3m"))
                        .sourceReferences(List.of())
                        .dataStatus(dataStatus)
                        .overrideAllowed(false)
                        .humanReviewRequired(false)
                        .executedAt(Instant.now())
                        .trace(Map.of(
                                "versions", eval.versions(),
                                "evidence", eval.evidence()))
                        .build());
                ruleSummaries.add(Map.of(
                        "ruleId", eval.ruleId(),
                        "outcome", eval.outcome(),
                        "value", eval.value() != null ? eval.value() : "",
                        "threshold", eval.threshold() != null ? eval.threshold() : ""));
            }

            BigDecimal legacyAbb = adapted.context() != null && adapted.context().scorecard() != null
                    ? adapted.context().scorecard().get("AVERAGE_BANK_BALANCE")
                    : null;
            CiMetricResult adb3 = byCode.get(BankingMetricService.ADB_3M);
            List<String> mismatchClasses = classifyBankingMismatch(legacyAbb, adb3, adapted);

            block.put("rules", ruleSummaries);
            block.put("legacyAverageBankBalance", legacyAbb);
            block.put("canonicalAdb3m", adb3 != null ? adb3.getValue() : null);
            block.put("canonicalOutcome", adb3 != null ? adb3.getOutcome() : null);
            block.put("versions", CanonicalBankingRuleEvaluator.frozenVersions());
            block.put("mismatchClassifications", mismatchClasses);
            block.put("useForShadowRules", cfg.isUseForShadowRules());
            block.put("note", "Production CreditControl banking gap defaults unchanged");
        } catch (Exception e) {
            log.warn("Canonical banking shadow comparison failed: {}", e.getMessage());
            block.put("error", "BANKING_COMPARISON_FAILED");
        }
        return block;
    }

    private static List<String> classifyBankingMismatch(
            BigDecimal legacyAbb,
            CiMetricResult adb3,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        List<String> classes = new ArrayList<>();
        if (adb3 == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(adb3.getOutcome())) {
            classes.add(BankingMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
        }
        if (adapted.defaultedPaths() != null
                && (adapted.defaultedPaths().contains("scorecard.AVERAGE_BANK_BALANCE")
                || adapted.defaultedPaths().contains("AVERAGE_BANK_BALANCE"))) {
            classes.add(BankingMismatchClassification.LEGACY_DEFAULT_USED.name());
        }
        if (legacyAbb != null && adb3 != null
                && BankingMetricOutcome.PASS.name().equals(adb3.getOutcome())
                && adb3.getValue() != null && adb3.getValue().get("v") != null) {
            try {
                BigDecimal canon = new BigDecimal(String.valueOf(adb3.getValue().get("v")));
                if (legacyAbb.subtract(canon).abs().compareTo(new BigDecimal("1")) > 0) {
                    classes.add(BankingMismatchClassification.BALANCE_CALCULATION_DIFFERENCE.name());
                }
            } catch (Exception ignored) {
                classes.add(BankingMismatchClassification.OTHER.name());
            }
        }
        return classes.stream().distinct().toList();
    }

    private Map<String, Object> evaluateCanonicalTaxShadow(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            UUID applicationId,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        Map<String, Object> block = new LinkedHashMap<>();
        try {
            List<CiItrReturn> returns =
                    itrReturnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
            Map<String, CiMetricResult> byCode = new LinkedHashMap<>();
            for (CiMetricResult m : metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
                if (m.getMetricCode() != null
                        && (m.getMetricCode().startsWith("itr.") || m.getMetricCode().startsWith("xsrc.itr_"))) {
                    byCode.putIfAbsent(m.getMetricCode(), m);
                }
            }
            var cfg = properties.getCanonicalization().getTax();
            List<CanonicalTaxRuleEvaluator.RuleEvalResult> evals = canonicalTaxRuleEvaluator.evaluateAll(
                    returns, byCode,
                    cfg.getMinIncomeThreshold(),
                    cfg.getMinTurnoverThreshold(),
                    cfg.isMinPatPositive(),
                    null,
                    java.time.LocalDate.now());

            List<Map<String, Object>> ruleSummaries = new ArrayList<>();
            for (CanonicalTaxRuleEvaluator.RuleEvalResult eval : evals) {
                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("v", eval.value());
                actual.put("threshold", eval.threshold());
                String dataStatus = RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())
                        ? DataStatus.MISSING.name()
                        : DataStatus.AVAILABLE.name();
                ruleResultRepository.save(CiStandardRuleResult.builder()
                        .evaluationId(shadow.getId())
                        .evaluationStageId(rulesStage != null ? rulesStage.getId() : null)
                        .ruleId(eval.ruleId())
                        .ruleVersion(eval.ruleVersion())
                        .policyVersionId(policyVersionId)
                        .category("HARD_RULE")
                        .ruleType("CANONICAL_TAX")
                        .engineName("CanonicalTaxRuleEvaluator")
                        .engineVersion(eval.ruleVersion())
                        .outcome(eval.outcome())
                        .actualValue(actual)
                        .reasonCode(eval.outcome())
                        .explanation("Canonical tax shadow rule " + eval.ruleId())
                        .factReferences(List.of("itr.business.turnover", "itr.income.total"))
                        .sourceReferences(List.of())
                        .dataStatus(dataStatus)
                        .overrideAllowed(false)
                        .humanReviewRequired(false)
                        .executedAt(Instant.now())
                        .trace(Map.of(
                                "versions", eval.versions(),
                                "evidence", eval.evidence()))
                        .build());
                ruleSummaries.add(Map.of(
                        "ruleId", eval.ruleId(),
                        "outcome", eval.outcome(),
                        "value", eval.value() != null ? eval.value() : "",
                        "threshold", eval.threshold() != null ? eval.threshold() : ""));
            }

            BigDecimal legacyIncome = adapted.context() != null && adapted.context().scorecard() != null
                    ? adapted.context().scorecard().get("ITR_INCOME")
                    : null;
            CiMetricResult turnover = byCode.get(TaxMetricService.TURNOVER_LATEST);
            List<String> mismatchClasses = classifyTaxMismatch(legacyIncome, turnover, adapted, returns);

            block.put("rules", ruleSummaries);
            block.put("legacyItrIncome", legacyIncome);
            block.put("canonicalTurnoverLatestFy", turnover != null ? turnover.getValue() : null);
            block.put("canonicalOutcome", turnover != null ? turnover.getOutcome() : null);
            block.put("versions", CanonicalTaxRuleEvaluator.frozenVersions());
            block.put("mismatchClassifications", mismatchClasses);
            block.put("useForShadowRules", cfg.isUseForShadowRules());
            block.put("note", "Production CreditControl ITR_INCOME / SCF_GAP_ITR_INCOME unchanged");
        } catch (Exception e) {
            log.warn("Canonical tax shadow comparison failed: {}", e.getMessage());
            block.put("error", "TAX_COMPARISON_FAILED");
        }
        return block;
    }

    private static List<String> classifyTaxMismatch(
            BigDecimal legacyValue,
            CiMetricResult turnover,
            LegacyUnderwritingContextAdapter.AdapterResult adapted,
            List<CiItrReturn> returns) {
        List<String> classes = new ArrayList<>();
        if (turnover == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(turnover.getOutcome())) {
            classes.add(TaxMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
        }
        if (adapted.metadata() != null) {
            Object flags = adapted.metadata().get("adapterFlags");
            if (flags instanceof Map<?, ?> fm) {
                Object tax = fm.get("tax");
                if (tax instanceof Map<?, ?> tf) {
                    Object income = tf.get("ITR_INCOME");
                    if (income instanceof Map<?, ?> id) {
                        if (Boolean.TRUE.equals(id.get("canonicalDataInsufficient"))) {
                            classes.add(TaxMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
                        }
                        if (Boolean.TRUE.equals(id.get("fallbackUsed"))) {
                            classes.add(TaxMismatchClassification.LEGACY_DEFAULT_USED.name());
                        }
                    }
                }
            }
            if (adapted.defaultedPaths() != null
                    && adapted.defaultedPaths().contains("compat.ITR_INCOME")) {
                classes.add(TaxMismatchClassification.LEGACY_DEFAULT_USED.name());
            }
        }
        if (returns != null && returns.stream().anyMatch(r ->
                "REVISED".equals(r.getReturnVersionType()) || "UPDATED".equals(r.getReturnVersionType()))) {
            classes.add(TaxMismatchClassification.REVISED_RETURN_SELECTED.name());
        }
        if (legacyValue != null && turnover != null && turnover.getValue() != null
                && turnover.getValue().get("v") != null
                && TaxMetricOutcome.PASS.name().equals(turnover.getOutcome())) {
            try {
                BigDecimal canon = new BigDecimal(String.valueOf(turnover.getValue().get("v")));
                if (legacyValue.compareTo(canon) != 0) {
                    classes.add(TaxMismatchClassification.ITR_VALUE_DIFFERENT.name());
                }
            } catch (Exception ignored) {
                classes.add(TaxMismatchClassification.PARSER_DIFFERENCE.name());
            }
        }
        if (classes.isEmpty()) {
            return List.of();
        }
        return classes.stream().distinct().toList();
    }

    private Map<String, Object> evaluateCanonicalReconciliationShadow(
            CiCreditEvaluation shadow,
            UUID policyVersionId,
            CiEvaluationStage rulesStage,
            UUID applicationId,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        Map<String, Object> block = new LinkedHashMap<>();
        try {
            var cfg = properties.getReconciliation();
            if (cfg == null || !cfg.isEnabled()) {
                return block;
            }
            var orch = reconciliationIngestionService.runForShadow(
                    applicationId, shadow.getFactSnapshotId(), shadow.getId());
            if (orch.isEmpty()) {
                return block;
            }
            ReconciliationOrchestrator.OrchestrationResult result = orch.get();
            List<CiReconciliationResult> results = result.results() != null ? result.results() : List.of();
            List<CanonicalReconciliationRuleEvaluator.RuleEvalResult> evals =
                    canonicalReconciliationRuleEvaluator.evaluateAll(results);

            List<Map<String, Object>> ruleSummaries = new ArrayList<>();
            for (CanonicalReconciliationRuleEvaluator.RuleEvalResult eval : evals) {
                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("v", eval.value());
                actual.put("threshold", eval.threshold());
                String dataStatus = RuleOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())
                        ? DataStatus.MISSING.name()
                        : DataStatus.AVAILABLE.name();
                ruleResultRepository.save(CiStandardRuleResult.builder()
                        .evaluationId(shadow.getId())
                        .evaluationStageId(rulesStage != null ? rulesStage.getId() : null)
                        .ruleId(eval.ruleId())
                        .ruleVersion(eval.ruleVersion())
                        .policyVersionId(policyVersionId)
                        .category("HARD_RULE")
                        .ruleType("CANONICAL_RECONCILIATION")
                        .engineName("CanonicalReconciliationRuleEvaluator")
                        .engineVersion(eval.ruleVersion())
                        .outcome(eval.outcome())
                        .actualValue(actual)
                        .reasonCode(eval.outcome())
                        .explanation("Canonical reconciliation shadow rule " + eval.ruleId())
                        .factReferences(List.of("reconciliation.*", "credit.evidence_strength_score"))
                        .sourceReferences(List.of())
                        .dataStatus(dataStatus)
                        .overrideAllowed(false)
                        .humanReviewRequired(RuleOutcome.REFER.name().equals(eval.outcome()))
                        .executedAt(Instant.now())
                        .trace(Map.of(
                                "versions", eval.versions(),
                                "evidence", eval.evidence()))
                        .build());
                ruleSummaries.add(Map.of(
                        "ruleId", eval.ruleId(),
                        "outcome", eval.outcome(),
                        "value", eval.value() != null ? eval.value() : "",
                        "threshold", eval.threshold() != null ? eval.threshold() : ""));
            }

            Map<String, CiReconciliationResult> byCode = new LinkedHashMap<>();
            for (CiReconciliationResult r : results) {
                byCode.putIfAbsent(r.getReconciliationCode(), r);
            }

            BigDecimal legacyTurnover = adapted.context() != null && adapted.context().scorecard() != null
                    ? firstBd(adapted.context().scorecard(), "ANNUAL_GST_TURNOVER", "ANNUAL_BANKING_TURNOVER")
                    : null;

            List<String> mismatchClasses = classifyReconciliationMismatch(legacyTurnover, byCode, adapted);

            block.put("rules", ruleSummaries);
            block.put("legacyTurnover", legacyTurnover);
            block.put("gstTurnover", valueOf(byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER), true));
            block.put("itrTurnover", valueOf(byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER), false));
            block.put("bankTurnover", valueOf(byCode.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER), false));
            CiReconciliationResult tri = byCode.get(ReconciliationConstants.TURNOVER_TRIANGULATION);
            block.put("triangulatedStatus", tri != null && tri.getMetadata() != null
                    ? tri.getMetadata().get("status") : null);
            CiCreditEvidenceSummary summary = result.evidenceSummary();
            if (summary != null) {
                Map<String, Object> ces = new LinkedHashMap<>();
                ces.put("evidenceStrengthScore", summary.getEvidenceStrengthScore());
                ces.put("evidenceStrengthGrade", summary.getEvidenceStrengthGrade());
                ces.put("materialConflicts", summary.getMaterialConflicts());
                ces.put("dataGaps", summary.getDataGaps());
                ces.put("turnoverEvidence", summary.getTurnoverEvidence());
                ces.put("obligationEvidence", summary.getObligationEvidence());
                block.put("creditEvidenceSummary", ces);
                block.put("evidenceStrength", summary.getEvidenceStrengthScore());
            }
            block.put("versions", CanonicalReconciliationRuleEvaluator.frozenVersions());
            block.put("mismatchClassifications", mismatchClasses);
            block.put("useForShadowRules", cfg.isUseForShadowRules());
            block.put("note", "Production underwriting / CreditControl unchanged; recon is shadow-only");
        } catch (Exception e) {
            log.warn("Canonical reconciliation shadow comparison failed: {}", e.getMessage());
            block.put("error", "RECONCILIATION_COMPARISON_FAILED");
        }
        return block;
    }

    private static List<String> classifyReconciliationMismatch(
            BigDecimal legacyTurnover,
            Map<String, CiReconciliationResult> byCode,
            LegacyUnderwritingContextAdapter.AdapterResult adapted) {
        List<String> classes = new ArrayList<>();
        if (legacyTurnover == null) {
            classes.add(ReconciliationMismatchClassification.LEGACY_DEFAULT_USED.name());
        } else {
            classes.add(ReconciliationMismatchClassification.SINGLE_SOURCE_PRODUCTION.name());
        }
        addVarianceClass(classes, byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER),
                ReconciliationMismatchClassification.GST_ITR_VARIANCE);
        addVarianceClass(classes, byCode.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER),
                ReconciliationMismatchClassification.GST_BANK_VARIANCE);
        addVarianceClass(classes, byCode.get(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER),
                ReconciliationMismatchClassification.ITR_BANK_VARIANCE);
        addVarianceClass(classes, byCode.get(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION),
                ReconciliationMismatchClassification.BUREAU_BANK_OBLIGATION_VARIANCE);
        CiReconciliationResult gstItr = byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER);
        if (gstItr != null && ReconciliationOutcome.DATA_INSUFFICIENT.name().equals(gstItr.getOutcome())) {
            classes.add(ReconciliationMismatchClassification.CANONICAL_DATA_INSUFFICIENT.name());
            if ("PERIOD_MISMATCH".equals(gstItr.getDataStatus())) {
                classes.add(ReconciliationMismatchClassification.PERIOD_MISMATCH.name());
            }
        }
        if (adapted != null && adapted.defaultedPaths() != null
                && (adapted.defaultedPaths().contains("compat.ANNUAL_GST_TURNOVER")
                || adapted.defaultedPaths().contains("compat.ANNUAL_BANKING_TURNOVER"))) {
            classes.add(ReconciliationMismatchClassification.LEGACY_DEFAULT_USED.name());
        }
        return classes.stream().distinct().toList();
    }

    private static void addVarianceClass(
            List<String> classes, CiReconciliationResult r, ReconciliationMismatchClassification c) {
        if (r == null) {
            return;
        }
        if (ReconciliationOutcome.MATERIAL_VARIANCE.name().equals(r.getOutcome())
                || ReconciliationOutcome.CONFLICT.name().equals(r.getOutcome())) {
            classes.add(c.name());
        }
    }

    private static BigDecimal valueOf(CiReconciliationResult r, boolean left) {
        if (r == null) {
            return null;
        }
        return left ? r.getLeftValue() : r.getRightValue();
    }

    private static BigDecimal firstBd(Map<String, BigDecimal> scorecard, String... keys) {
        if (scorecard == null) {
            return null;
        }
        for (String k : keys) {
            BigDecimal v = scorecard.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    static String normalizeOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return null;
        }
        String o = outcome.trim().toUpperCase(Locale.ROOT);
        if ("REJECT".equals(o) || "REJECTED".equals(o)) {
            return "REJECTED";
        }
        if ("APPROVE".equals(o) || "APPROVED".equals(o) || "APPROVED_WITH_CONDITIONS".equals(o)) {
            return "APPROVED";
        }
        if ("MANUAL_REVIEW".equals(o) || "REFER".equals(o)) {
            return "MANUAL_REVIEW";
        }
        return o;
    }

    static boolean outcomesMatch(String a, String b) {
        String na = normalizeOutcome(a);
        String nb = normalizeOutcome(b);
        return na != null && na.equals(nb);
    }

    static String mapRuleOutcome(String creditDecision) {
        String n = normalizeOutcome(creditDecision);
        if (n == null) {
            return RuleOutcome.NOT_APPLICABLE.name();
        }
        return switch (n) {
            case "APPROVED" -> RuleOutcome.PASS.name();
            case "REJECTED" -> RuleOutcome.FAIL.name();
            case "MANUAL_REVIEW" -> RuleOutcome.REFER.name();
            default -> n;
        };
    }

    private static String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            return ex.getClass().getSimpleName();
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
