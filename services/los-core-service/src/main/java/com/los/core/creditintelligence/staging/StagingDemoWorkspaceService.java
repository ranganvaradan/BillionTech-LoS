package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwriterViewBuilder;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwritingProvider;
import com.los.core.creditintelligence.aiunderwriter.service.StubAiUnderwritingProvider;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.service.CreditDecisionViewBuilder;
import com.los.core.creditintelligence.decision.service.DecisionComparisonService;
import com.los.core.creditintelligence.decision.service.DecisionRecommendationExplanationBuilder;
import com.los.core.creditintelligence.decision.service.LimitMethodEngine;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.fixture.GoldenShadowPackageFactory;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationRunResult;
import com.los.core.creditintelligence.validation.service.CreditEvidenceViewBuilder;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import com.los.core.creditintelligence.validation.service.ValidationBundleLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates existing CI harness / shadow engines into a CEO review workspace.
 * Never enables canonical authority; never mutates production CAM / sanction.
 */
@Slf4j
@Service
public class StagingDemoWorkspaceService {

    public static final BigDecimal DEMO_REQUESTED_AMOUNT = new BigDecimal("1000000");
    public static final int DEMO_TENURE_MONTHS = 24;

    private final CreditIntelligenceProperties properties;
    private final MultiSourceValidationHarness harness;
    private final ValidationBundleLoader bundleLoader;
    private final ShadowDecisionEngine shadowDecisionEngine;
    private final LimitMethodEngine limitMethodEngine;
    private final CreditDecisionViewBuilder decisionViewBuilder;
    private final DecisionRecommendationExplanationBuilder explanationBuilder;
    private final DecisionComparisonService comparisonService;
    private final CreditEvidenceViewBuilder evidenceViewBuilder;
    private final StubAiUnderwritingProvider stubAiProvider;
    private final AiUnderwriterViewBuilder aiViewBuilder;
    private final ShadowPolicyEngine shadowPolicyEngine;

    public StagingDemoWorkspaceService(
            CreditIntelligenceProperties properties,
            MultiSourceValidationHarness harness) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.harness = harness;
        this.bundleLoader = new ValidationBundleLoader();
        this.shadowDecisionEngine = new ShadowDecisionEngine();
        this.limitMethodEngine = new LimitMethodEngine();
        this.decisionViewBuilder = new CreditDecisionViewBuilder();
        this.explanationBuilder = new DecisionRecommendationExplanationBuilder();
        this.comparisonService = new DecisionComparisonService();
        this.evidenceViewBuilder = new CreditEvidenceViewBuilder();
        this.stubAiProvider = new StubAiUnderwritingProvider();
        this.aiViewBuilder = new AiUnderwriterViewBuilder();
        this.shadowPolicyEngine = new ShadowPolicyEngine();
    }

    public List<Map<String, Object>> listCases() {
        return StagingCaseCatalog.all().stream().map(StagingCaseCatalog::listEntry).toList();
    }

    public Map<String, Object> buildWorkspace(String caseCode) {
        StagingCaseCatalog.CaseMeta meta = StagingCaseCatalog.require(caseCode);
        UUID tenantId = properties.getDefaultTenantId();
        UUID applicationId = UUID.randomUUID();

        ValidationRunResult run = harness.runCase(meta.enumCode(), tenantId, applicationId);
        var bundle = bundleLoader.load(meta.enumCode());

        CiDecisionStrategy strategy = DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);
        Map<String, Object> decisionMetrics = mapMetricsForDecision(bundle.metricStubs());
        String policyOverall = policyOverallFromDual(run);

        DecisionRuntimeInput decisionInput = DecisionRuntimeInput.builder()
                .tenantId(tenantId)
                .applicationId(run.applicationId())
                .evaluationContextId(run.evaluationContextId())
                .policyEvaluationId(UUID.randomUUID())
                .productCode("SCF_DEMO")
                .policyOverallOutcome(policyOverall)
                .scoreResult(Map.of("grade", "B", "score", 720))
                .requestedAmount(DEMO_REQUESTED_AMOUNT)
                .requestedTenureMonths(DEMO_TENURE_MONTHS)
                .metrics(decisionMetrics)
                .reconciliations(run.reconciliations())
                .metadata(Map.of(
                        "validationFixture", true,
                        "fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER,
                        "caseCode", meta.caseCode()))
                .build();

        CiCreditRecommendation recommendation = shadowDecisionEngine.recommend(strategy, decisionInput);

        Map<String, Object> legacyCmp = comparisonService.compare(
                Map.of(
                        "amount", DEMO_REQUESTED_AMOUNT,
                        "tenure", DEMO_TENURE_MONTHS,
                        "authority", "L1",
                        "outcome", "APPROVE"),
                Map.of(
                        "legacyUsedDefault", meta.enumCode() == ValidationCaseCode.CASE_B_LEGACY_DEFAULT,
                        "legacyDefaultExposure", run.legacyDefaultExposure()),
                recommendation);

        Map<String, Object> creditDecisionView = decisionViewBuilder.build(
                recommendation, decisionInput, legacyCmp);

        LimitMethodEngine.LimitResult limits = limitMethodEngine.compute(
                strategy.getContent() == null ? Map.of()
                        : asMap(strategy.getContent().get("limitStrategy")),
                decisionInput);

        Map<String, Object> decisionExplanation = explanationBuilder.build(recommendation);
        decisionExplanation = new LinkedHashMap<>(decisionExplanation);
        decisionExplanation.put("candidateLimits", limitCandidates(limits));
        decisionExplanation.put("selectedMethod", limits.selectedMethod());
        decisionExplanation.put("recommendedAmount", limits.recommendedAmount());

        Map<String, Object> aiView = buildAiView(run, creditDecisionView, recommendation, decisionMetrics);

        Map<String, Object> evidence = new LinkedHashMap<>(run.evidenceView());
        evidence = evidenceViewBuilder.withDecisionView(evidence, creditDecisionView);
        evidence = evidenceViewBuilder.withAiUnderwriterView(evidence, aiView);

        Map<String, Object> policyResult = buildPolicyResult(tenantId, run, decisionMetrics);

        Map<String, Object> workspace = new LinkedHashMap<>();
        stampSafety(workspace);
        workspace.put("caseCode", meta.caseCode());
        workspace.put("enumCode", meta.enumCode().name());
        workspace.put("title", meta.title());
        workspace.put("origin", meta.origin());
        workspace.put("description", meta.description());
        workspace.put("runId", run.runId().toString());
        workspace.put("applicationId", run.applicationId().toString());
        workspace.put("evaluationContextId", run.evaluationContextId().toString());
        workspace.put("dataOrigin", run.dataOrigin() == null ? null : run.dataOrigin().name());
        workspace.put("creditEvidenceView", evidence);
        workspace.put("creditDecisionView", creditDecisionView);
        workspace.put("aiUnderwriterView", aiView);
        workspace.put("legacyVsCanonical", buildLegacyVsCanonical(run));
        workspace.put("decisionExplanation", decisionExplanation);
        workspace.put("policyResult", policyResult);
        workspace.put("recommendation", recommendationSummary(recommendation));
        Map<String, Object> cutover = new LinkedHashMap<>();
        cutover.put("outcome", run.cutoverOutcome() == null ? null : run.cutoverOutcome().name());
        cutover.put("dimensions", run.cutoverDimensions());
        cutover.put("blockers", run.blockers());
        cutover.put("allowCanonicalAuthority", false);
        workspace.put("cutover", cutover);
        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("originalHash", run.deterministicEvaluationHash());
        replay.put("replayHash", run.replayHash());
        replay.put("match", run.replayIdentical());
        workspace.put("replay", replay);
        workspace.put("summary", run.summary());
        workspace.put("investigationQuestions", run.investigationQuestions());
        workspace.put("strategyCode", DecisionStrategyFactory.STRATEGY_CODE);
        workspace.put("strategyLabel", DecisionStrategyFactory.LABEL);
        return workspace;
    }

    private Map<String, Object> buildAiView(
            ValidationRunResult run,
            Map<String, Object> creditDecisionView,
            CiCreditRecommendation recommendation,
            Map<String, Object> decisionMetrics) {
        Map<String, Object> knownMetrics = new LinkedHashMap<>();
        decisionMetrics.forEach((k, v) -> {
            if (v instanceof Map<?, ?> m && m.get("value") != null) {
                knownMetrics.put(k, m.get("value"));
            }
        });
        Map<String, Object> policyFacts = new LinkedHashMap<>();
        policyFacts.put("overallOutcome", creditDecisionView.get("PolicyOutcome"));
        policyFacts.put("recommendationOutcome", recommendation.getRecommendationOutcome());

        Map<String, Object> recommendationFacts = new LinkedHashMap<>();
        recommendationFacts.put("amount", recommendation.getRecommendedAmount());
        recommendationFacts.put("outcome", recommendation.getRecommendationOutcome());

        AiUnderwritingContext ctx = new AiUnderwritingContext(
                run.tenantId(),
                run.applicationId(),
                run.evaluationContextId(),
                UUID.randomUUID(),
                recommendation.getId(),
                "A1_V1",
                Map.of("caseCode", run.caseCode().name(), "fixture", true),
                Map.of("masked", true, "note", "VALIDATION FIXTURE — no real borrower PII"),
                run.evidenceView(),
                Map.of(),
                creditDecisionView,
                run.reconciliations(),
                policyFacts,
                recommendationFacts,
                run.investigationQuestions().stream()
                        .map(q -> Map.<String, Object>of("question", q == null ? "" : q))
                        .toList(),
                knownMetrics,
                Map.of(),
                List.of("metrics", "reconciliations", "policyOutcome"));

        AiUnderwritingProvider.ProviderResponse resp = stubAiProvider.analyze(
                ctx,
                List.of("NARRATIVE", "EXPLANATION", "QUESTION", "CAM_DRAFT"),
                Map.of());
        List<Map<String, Object>> suggestionMaps = new ArrayList<>();
        if (resp.suggestions() != null) {
            for (AiUnderwritingProvider.RawSuggestion s : resp.suggestions()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", s.type());
                m.put("title", s.title());
                m.put("content", s.content());
                m.put("authoritative", false);
                m.put("outputMarker", "AI_SUGGESTION");
                suggestionMaps.add(m);
            }
        }
        Map<String, Object> view = aiViewBuilder.buildFromSuggestions(suggestionMaps);
        view.put("provider", StubAiUnderwritingProvider.PROVIDER);
        view.put("model", StubAiUnderwritingProvider.MODEL);
        return view;
    }

    private Map<String, Object> buildPolicyResult(
            UUID tenantId, ValidationRunResult run, Map<String, Object> decisionMetrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", ShadowPolicyEngine.ENGINE_NAME);
        out.put("engineVersion", ShadowPolicyEngine.ENGINE_VERSION);
        out.put("authoritative", false);
        out.put("status", "SHADOW");
        try {
            var pkg = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenantId);
            PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(
                    tenantId, run.evaluationContextId(), Map.of(), decisionMetrics, Map.of());
            CiPolicyEvaluation eval = shadowPolicyEngine.evaluate(pkg, input);
            out.put("packageCode", pkg.getPolicyCode());
            out.put("overallOutcome", eval.getOverallOutcome());
            out.put("deterministicHash", eval.getDeterministicHash());
            out.put("source", "GoldenShadowPackageFactory.canonicalShadowPolicyV1");
        } catch (Exception e) {
            log.debug("Shadow policy evaluate fell back to dual-policy summary: {}", e.getMessage());
            out.put("source", "dualPolicyComparison");
            out.put("overallOutcome", policyOverallFromDual(run));
            out.put("dualPolicySummary", run.dualPolicyComparison());
            out.put("fallbackReason", e.getMessage());
        }
        out.put("dualPolicy", run.dualPolicyComparison());
        return out;
    }

    private Map<String, Object> buildLegacyVsCanonical(ValidationRunResult run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER);
        out.put("authoritative", false);
        out.put("allowCanonicalAuthority", false);
        out.put("productionActive", false);
        out.put("dualPolicyComparison", run.dualPolicyComparison());
        out.put("legacyDefaultExposure", run.legacyDefaultExposure());
        out.put("comparisons", run.dualPolicyComparison().get("comparisons"));
        return out;
    }

    private static Map<String, Object> recommendationSummary(CiCreditRecommendation rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rec.getId() == null ? null : rec.getId().toString());
        m.put("outcome", rec.getRecommendationOutcome());
        m.put("status", rec.getStatus());
        m.put("amount", rec.getRecommendedAmount());
        m.put("tenureMonths", rec.getRecommendedTenureMonths());
        m.put("finalRate", rec.getRecommendedFinalRate());
        m.put("authoritative", Boolean.FALSE.equals(rec.getAuthoritative()) ? false : rec.getAuthoritative());
        m.put("engineVersion", ShadowDecisionEngine.ENGINE_VERSION);
        m.put("deterministicDecisionHash", rec.getDeterministicDecisionHash());
        return m;
    }

    private static List<Map<String, Object>> limitCandidates(LimitMethodEngine.LimitResult limits) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (limits == null || limits.candidates() == null) {
            return out;
        }
        for (var c : limits.candidates()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", c.getMethodCode());
            row.put("eligibleAmount", c.getEligibleAmount());
            row.put("dataStatus", c.getDataStatus());
            row.put("selected", c.getMethodCode() != null && c.getMethodCode().equals(limits.selectedMethod()));
            out.add(row);
        }
        return out;
    }

    private static String policyOverallFromDual(ValidationRunResult run) {
        Object o = run.dualPolicyComparison().get("canonicalOverall");
        if (o == null) {
            o = run.dualPolicyComparison().get("overallCanonical");
        }
        if (o == null && run.summary() != null) {
            o = run.summary().get("canonicalOutcome");
        }
        if (o == null) {
            return run.caseCode() == ValidationCaseCode.CASE_A_STRONG ? "PASS"
                    : run.caseCode() == ValidationCaseCode.CASE_E_INCOMPLETE ? "DATA_INSUFFICIENT"
                    : "REFER";
        }
        String s = String.valueOf(o).toUpperCase();
        if (s.contains("PASS") || s.contains("APPROVE")) {
            return "PASS";
        }
        if (s.contains("INSUFFICIENT")) {
            return "DATA_INSUFFICIENT";
        }
        if (s.contains("FAIL") || s.contains("DECLINE")) {
            return "FAIL";
        }
        return "REFER";
    }

    /**
     * Map validation metric stubs into DecisionRuntimeInput nested metric maps.
     */
    static Map<String, Object> mapMetricsForDecision(Map<String, BigDecimal> stubs) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (stubs == null) {
            return metrics;
        }
        BigDecimal gst = stubs.get("gst.turnover.trailing_12m");
        BigDecimal bank = stubs.get("bank.turnover.trailing_12m");
        BigDecimal itr = stubs.get("itr.turnover.trailing_12m");
        BigDecimal turnover = minPositive(gst, bank, itr);
        putMetric(metrics, "turnover.triangulated", turnover);
        putMetric(metrics, "banking.avg_daily_balance_3m", stubs.get("bank.abb.average"));
        putMetric(metrics, "obligation.total_emi", stubs.get("bureau.emi.monthly"));
        BigDecimal income = stubs.get("itr.income.total");
        if (income != null) {
            putMetric(metrics, "income.eligible_monthly",
                    income.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
            putMetric(metrics, "cashflow.eligible_annual", income);
        }
        putMetric(metrics, "collateral.value", new BigDecimal("2000000"));
        return metrics;
    }

    private static BigDecimal minPositive(BigDecimal... vals) {
        BigDecimal min = null;
        for (BigDecimal v : vals) {
            if (v == null) {
                continue;
            }
            if (min == null || v.compareTo(min) < 0) {
                min = v;
            }
        }
        return min;
    }

    private static void putMetric(Map<String, Object> metrics, String key, BigDecimal value) {
        if (value == null) {
            return;
        }
        metrics.put(key, Map.of("value", value, "dataStatus", "AVAILABLE"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    static void stampSafety(Map<String, Object> target) {
        target.put("fixtureBanner", StagingCaseCatalog.FIXTURE_BANNER);
        target.put("productionActive", false);
        target.put("authoritative", false);
        target.put("allowCanonicalAuthority", false);
    }
}
