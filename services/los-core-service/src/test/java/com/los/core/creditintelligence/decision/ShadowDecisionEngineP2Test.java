package com.los.core.creditintelligence.decision;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.CiPolicyDeviation;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.domain.DeviationStatus;
import com.los.core.creditintelligence.decision.domain.RecommendationOutcome;
import com.los.core.creditintelligence.decision.domain.RecommendationStatus;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.service.AuthorityMatrixEngine;
import com.los.core.creditintelligence.decision.service.CollateralEngine;
import com.los.core.creditintelligence.decision.service.ConditionEngine;
import com.los.core.creditintelligence.decision.service.CreditDecisionViewBuilder;
import com.los.core.creditintelligence.decision.service.DecisionComparisonService;
import com.los.core.creditintelligence.decision.service.DecisionHistoricalReplayService;
import com.los.core.creditintelligence.decision.service.DecisionImpactAnalysisService;
import com.los.core.creditintelligence.decision.service.DecisionReplayService;
import com.los.core.creditintelligence.decision.service.LimitMethodEngine;
import com.los.core.creditintelligence.decision.service.PricingEngine;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.decision.service.TenureEngine;
import com.los.core.creditintelligence.validation.service.CreditEvidenceViewBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowDecisionEngineP2Test {

    private ShadowDecisionEngine engine;
    private LimitMethodEngine limitEngine;
    private PricingEngine pricingEngine;
    private CollateralEngine collateralEngine;
    private TenureEngine tenureEngine;
    private AuthorityMatrixEngine authorityEngine;
    private UUID tenant;

    @BeforeEach
    void setUp() {
        engine = new ShadowDecisionEngine();
        limitEngine = new LimitMethodEngine();
        pricingEngine = new PricingEngine();
        collateralEngine = new CollateralEngine();
        tenureEngine = new TenureEngine();
        authorityEngine = new AuthorityMatrixEngine();
        tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private CiDecisionStrategy strategy() {
        return DecisionStrategyFactory.p2ValidationStrategyV1(tenant);
    }

    private DecisionRuntimeInput.Builder baseInput() {
        return DecisionRuntimeInput.builder()
                .tenantId(tenant)
                .applicationId(UUID.randomUUID())
                .evaluationContextId(UUID.randomUUID())
                .policyEvaluationId(UUID.randomUUID())
                .policyOverallOutcome("PASS")
                .scoreResult(Map.of("grade", "B", "score", 720))
                .requestedAmount(new BigDecimal("1000000"))
                .requestedTenureMonths(24)
                .metrics(Map.of(
                        "income.eligible_monthly", Map.of("value", 200000, "dataStatus", "AVAILABLE"),
                        "obligation.total_emi", Map.of("value", 20000, "dataStatus", "AVAILABLE"),
                        "banking.avg_daily_balance_3m", Map.of("value", 300000, "dataStatus", "AVAILABLE"),
                        "turnover.triangulated", Map.of("value", 6000000, "dataStatus", "AVAILABLE"),
                        "cashflow.eligible_annual", Map.of("value", 2400000, "dataStatus", "AVAILABLE"),
                        "collateral.value", Map.of("value", 2000000, "dataStatus", "AVAILABLE")));
    }

    // --- Decision aggregation ---

    @Test
    void policyPassContinuesToStructuring() {
        CiCreditRecommendation rec = engine.recommend(strategy(), baseInput().build());
        assertThat(rec.getRecommendationOutcome()).isIn(
                RecommendationOutcome.APPROVE.name(),
                RecommendationOutcome.APPROVE_WITH_CONDITIONS.name(),
                RecommendationOutcome.COUNTER_OFFER.name());
        assertThat(rec.getAuthoritative()).isFalse();
        assertThat(rec.getStatus()).isEqualTo(RecommendationStatus.RECOMMENDED.name());
        assertThat(rec.getDimensions()).containsKeys(
                "Eligibility", "RiskGrade", "Limit", "Tenure", "Pricing",
                "Collateral", "Conditions", "Covenants", "Authority", "FinalRecommendation");
    }

    @Test
    void policyFailDeclines() {
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("FAIL")
                .policyRuleResults(List.of(Map.of("ruleId", "H1", "ruleType", "HARD", "outcome", "FAIL")))
                .build();
        CiCreditRecommendation rec = engine.recommend(strategy(), input);
        assertThat(rec.getRecommendationOutcome()).isEqualTo(RecommendationOutcome.DECLINE.name());
        assertThat(rec.getAuthoritative()).isFalse();
    }

    @Test
    void policyReferMapsToRefer() {
        CiCreditRecommendation rec = engine.recommend(strategy(),
                baseInput().policyOverallOutcome("REFER").build());
        assertThat(rec.getRecommendationOutcome()).isEqualTo(RecommendationOutcome.REFER.name());
    }

    @Test
    void policyDataInsufficientMaps() {
        CiCreditRecommendation rec = engine.recommend(strategy(),
                baseInput().policyOverallOutcome("DATA_INSUFFICIENT").build());
        assertThat(rec.getRecommendationOutcome()).isEqualTo(RecommendationOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void knockoutFailDeclines() {
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("PASS")
                .policyRuleResults(List.of(Map.of(
                        "ruleId", "KO1", "ruleType", "KNOCKOUT", "outcome", "FAIL", "stageCode", "BUREAU")))
                .build();
        CiCreditRecommendation rec = engine.recommend(strategy(), input);
        assertThat(rec.getRecommendationOutcome()).isEqualTo(RecommendationOutcome.DECLINE.name());
    }

    // --- Limit ---

    @Test
    void limitOneMethodRequestedWithinEligible() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("POLICY_CAP"),
                "params", Map.of("policyCap", 900000));
        var result = limitEngine.compute(ls, baseInput()
                .requestedAmount(new BigDecimal("500000")).build());
        assertThat(result.recommendedAmount()).isEqualByComparingTo("500000");
        assertThat(result.amountOutcome()).isEqualTo(RecommendationOutcome.APPROVE);
    }

    @Test
    void limitMultipleMethodsMinAndCounterOffer() {
        var result = limitEngine.compute(
                DecisionValueHelperSafe(strategy().getContent().get("limitStrategy")),
                baseInput().requestedAmount(new BigDecimal("1000000")).build());
        assertThat(result.candidates().size()).isGreaterThanOrEqualTo(5);
        assertThat(result.recommendedAmount()).isLessThan(new BigDecimal("1000000"));
        assertThat(result.amountOutcome()).isEqualTo(RecommendationOutcome.COUNTER_OFFER);
        assertThat(result.selectedMethod()).isNotBlank();
    }

    @Test
    void limitPolicyCapAndMissingMethod() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("POLICY_CAP", "UNKNOWN_X"),
                "params", Map.of("policyCap", 100000));
        var result = limitEngine.compute(ls, baseInput().requestedAmount(new BigDecimal("50000")).build());
        assertThat(result.selectedEligibleAmount()).isEqualByComparingTo("100000");
        assertThat(result.candidates().stream()
                .anyMatch(c -> "UNKNOWN_X".equals(c.getMethodCode())
                        && "NOT_APPLICABLE".equals(c.getDataStatus()))).isTrue();
    }

    @Test
    void limitRequestedBelowEligible() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("POLICY_CAP"),
                "params", Map.of("policyCap", 900000));
        var result = limitEngine.compute(ls, baseInput().requestedAmount(new BigDecimal("100000")).build());
        assertThat(result.recommendedAmount()).isEqualByComparingTo("100000");
        assertThat(result.amountOutcome()).isEqualTo(RecommendationOutcome.APPROVE);
    }

    @Test
    void limitRequestedAboveEligible() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("POLICY_CAP"),
                "params", Map.of("policyCap", 200000));
        var result = limitEngine.compute(ls, baseInput().requestedAmount(new BigDecimal("500000")).build());
        assertThat(result.recommendedAmount()).isEqualByComparingTo("200000");
        assertThat(result.amountOutcome()).isEqualTo(RecommendationOutcome.COUNTER_OFFER);
    }

    // --- FOIR ---

    @Test
    void foirExactBoundaryAndNoCapacity() {
        Map<String, Object> params = Map.of(
                "allowedFoir", 0.50,
                "interestRateForFoirSizing", 0.14,
                "amortizationVersion", "EMI_FLAT_V1",
                "defaultTenureMonths", 18);
        Map<String, Object> ls = Map.of("combine", "MIN", "methods", List.of("FOIR_LIMIT"), "params", params);

        DecisionRuntimeInput ok = baseInput()
                .requestedAmount(new BigDecimal("100000"))
                .requestedTenureMonths(18)
                .metrics(Map.of(
                        "income.eligible_monthly", Map.of("value", 100000),
                        "obligation.total_emi", Map.of("value", 10000)))
                .build();
        var r1 = limitEngine.compute(ls, ok);
        assertThat(r1.candidates().get(0).getDataStatus()).isEqualTo("AVAILABLE");
        assertThat(r1.candidates().get(0).getEligibleAmount()).isPositive();

        DecisionRuntimeInput noCap = baseInput()
                .metrics(Map.of(
                        "income.eligible_monthly", Map.of("value", 100000),
                        "obligation.total_emi", Map.of("value", 60000)))
                .build();
        var r2 = limitEngine.compute(ls, noCap);
        assertThat(r2.candidates().get(0).getEligibleAmount()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void foirMissingIncome() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("FOIR_LIMIT"),
                "params", Map.of("allowedFoir", 0.50, "interestRateForFoirSizing", 0.14,
                        "amortizationVersion", "EMI_FLAT_V1"));
        var r = limitEngine.compute(ls, baseInput().metrics(Map.of()).build());
        assertThat(r.candidates().get(0).getDataStatus()).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void foirReducingAmortization() {
        BigDecimal principal = limitEngine.principalFromEmi(
                new BigDecimal("10000"), new BigDecimal("0.12"), 24, "EMI_REDUCING_V1");
        assertThat(principal).isPositive();
        BigDecimal emi = limitEngine.emiFromPrincipal(principal, new BigDecimal("0.12"), 24, "EMI_REDUCING_V1");
        assertThat(emi).isCloseTo(new BigDecimal("10000"), org.assertj.core.data.Offset.offset(new BigDecimal("1")));
    }

    // --- DSCR ---

    @Test
    void dscrStrongWeakMissing() {
        Map<String, Object> ls = Map.of(
                "combine", "MIN",
                "methods", List.of("DSCR_LIMIT"),
                "params", Map.of("minDscr", 1.25, "interestRateForFoirSizing", 0.14,
                        "amortizationVersion", "EMI_FLAT_V1"));
        var strong = limitEngine.compute(ls, baseInput()
                .metrics(Map.of("cashflow.eligible_annual", Map.of("value", 5000000))).build());
        assertThat(strong.candidates().get(0).getEligibleAmount()).isPositive();

        var weak = limitEngine.compute(ls, baseInput()
                .metrics(Map.of("cashflow.eligible_annual", Map.of("value", 100000))).build());
        assertThat(weak.candidates().get(0).getEligibleAmount())
                .isLessThan(strong.candidates().get(0).getEligibleAmount());

        var missing = limitEngine.compute(ls, baseInput().metrics(Map.of()).build());
        assertThat(missing.candidates().get(0).getDataStatus()).isEqualTo("DATA_INSUFFICIENT");
    }

    // --- Pricing ---

    @Test
    void pricingComponentsFloorCapMissingGrade() {
        Map<String, Object> ps = Map.of(
                "baseRate", 0.125,
                "floor", 0.11,
                "cap", 0.24,
                "components", List.of(
                        Map.of("code", "RISK_GRADE_PREMIUM",
                                "bpsByGrade", Map.of("A", 50, "B", 125, "DATA_INSUFFICIENT", 0)),
                        Map.of("code", "UNSECURED_PREMIUM", "bps", 50),
                        Map.of("code", "STRONG_COLLATERAL_DISCOUNT", "bps", 25)));
        var priced = pricingEngine.compute(ps, baseInput().scoreResult(Map.of("grade", "B")).build(), 24);
        assertThat(priced.components()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(priced.finalRate()).isGreaterThan(priced.baseRate());

        var missing = pricingEngine.compute(ps, baseInput().scoreResult(Map.of()).build(), 12);
        assertThat(missing.limitations()).contains("MISSING_RISK_GRADE");

        Map<String, Object> high = Map.of(
                "baseRate", 0.30, "floor", 0.11, "cap", 0.24, "components", List.of());
        var capped = pricingEngine.compute(high, baseInput().build(), 12);
        assertThat(capped.finalRate()).isEqualByComparingTo("0.240000");
    }

    // --- Collateral ---

    @Test
    void collateralAdequateShortfallMissing() {
        Map<String, Object> cs = Map.of("required", true, "maxLtv", 0.75, "haircut", 0.10);
        var ok = collateralEngine.compute(cs, baseInput().build(), new BigDecimal("500000"));
        assertThat(ok.adequate()).isTrue();

        var shortfall = collateralEngine.compute(cs, baseInput()
                .metrics(Map.of("collateral.value", Map.of("value", 100000))).build(),
                new BigDecimal("500000"));
        assertThat(shortfall.shortfall()).isPositive();

        var missing = collateralEngine.compute(cs, baseInput().metrics(Map.of()).build(),
                new BigDecimal("500000"));
        assertThat(missing.reasonCodes()).contains("COLLATERAL_VALUATION_MISSING");
    }

    // --- Conditions ---

    @Test
    void conditionsFromDiReconCollateral() {
        ConditionEngine ce = new ConditionEngine();
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("DATA_INSUFFICIENT")
                .reconciliations(Map.of("XSRC_GST_BANK_TURNOVER",
                        Map.of("outcome", "CONFLICT")))
                .build();
        var coll = collateralEngine.compute(Map.of("required", true, "maxLtv", 0.75, "haircut", 0.10),
                baseInput().metrics(Map.of("collateral.value", Map.of("value", 10000))).build(),
                new BigDecimal("500000"));
        var result = ce.compute(Map.of("autoFromDi", true, "autoFromReconConflict", true,
                "autoFromCollateralShortfall", true), input, coll, true);
        assertThat(result.reasonCodes()).anyMatch(r -> r.contains("DI") || r.contains("RECON") || r.contains("COLLATERAL"));
        assertThat(result.conditions()).isNotEmpty();
    }

    // --- Authority ---

    @Test
    void authorityAmountBandExceptionGrade() {
        Map<String, Object> auth = DecisionValueHelperSafe(strategy().getContent().get("authorityStrategy"));
        var l1 = authorityEngine.compute(auth, baseInput().scoreResult(Map.of("grade", "A")).build(),
                new BigDecimal("400000"), 0, false);
        assertThat(l1.level()).isEqualTo("CREDIT_MANAGER_L1");

        var l2 = authorityEngine.compute(auth, baseInput().scoreResult(Map.of("grade", "C")).build(),
                new BigDecimal("1000000"), 0, false);
        assertThat(l2.level()).isEqualTo("CREDIT_MANAGER_L2");

        var esc = authorityEngine.compute(auth, baseInput().build(),
                new BigDecimal("100000"), 0, true);
        assertThat(esc.level()).isEqualTo("CREDIT_COMMITTEE");
    }

    // --- Replay / never authoritative / never human write ---

    @Test
    void replaySameHashDespiteMetadataMutation() {
        DecisionRuntimeInput input = baseInput().build();
        CiCreditRecommendation a = engine.recommend(strategy(), input);
        DecisionRuntimeInput mutated = input.withMetadata(Map.of("liveNoise", UUID.randomUUID().toString()));
        CiCreditRecommendation b = engine.recommend(strategy(), mutated);
        assertThat(a.getDeterministicDecisionHash()).isEqualTo(b.getDeterministicDecisionHash());

        Map<String, Object> replay = new DecisionReplayService(engine).replay(strategy(), input);
        assertThat(replay.get("hashMatch")).isEqualTo(true);
    }

    @Test
    void neverAuthoritativeAndNeverWritesHumanDecision() {
        CiCreditRecommendation rec = engine.recommend(strategy(), baseInput().build());
        assertThat(rec.getAuthoritative()).isFalse();
        assertThat(rec.getStatus()).isEqualTo("RECOMMENDED");
        // Engine has no CiHumanCreditDecision field / write path — deviations stay REQUESTED
        for (CiPolicyDeviation d : rec.getDeviations()) {
            assertThat(d.getStatus()).isEqualTo(DeviationStatus.REQUESTED.name());
            assertThat(d.getStatus()).isNotEqualTo(DeviationStatus.APPROVED.name());
        }
    }

    @Test
    void crossTenantRejected() {
        CiDecisionStrategy s = strategy();
        DecisionRuntimeInput other = baseInput().tenantId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> engine.recommend(s, other))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cross-tenant");
    }

    // --- C6 CASE_A–E ---

    @Test
    void c6CasesWithValidationStrategy() {
        CiDecisionStrategy s = strategy();
        assertThat(s.getContent().get("label")).isEqualTo("VALIDATION_FIXTURE_DECISION");
        assertThat(s.getContent().get("validationFixture")).isEqualTo(true);

        Map<String, DecisionRuntimeInput> cases = Map.of(
                "CASE_A", caseInput("PASS", "A", new BigDecimal("500000"), true, false),
                "CASE_B", caseInput("PASS", "B", new BigDecimal("800000"), true, true),
                "CASE_C", caseInput("PASS", "C", new BigDecimal("1000000"), true, false)
                        .withMetadata(Map.of()),
                "CASE_D", caseInput("REFER", "C", new BigDecimal("600000"), true, false),
                "CASE_E", caseInput("DATA_INSUFFICIENT", "DATA_INSUFFICIENT",
                        new BigDecimal("400000"), false, false));

        // CASE_C with recon conflict
        DecisionRuntimeInput caseC = baseInput()
                .policyOverallOutcome("PASS")
                .scoreResult(Map.of("grade", "C", "score", 600))
                .requestedAmount(new BigDecimal("1000000"))
                .reconciliations(Map.of("XSRC_GST_ITR_TURNOVER", Map.of("outcome", "MATERIAL_VARIANCE")))
                .metrics(baseInput().build().metrics())
                .build();

        for (var e : List.of(
                Map.entry("CASE_A", cases.get("CASE_A")),
                Map.entry("CASE_B", cases.get("CASE_B")),
                Map.entry("CASE_C", caseC),
                Map.entry("CASE_D", cases.get("CASE_D")),
                Map.entry("CASE_E", cases.get("CASE_E")))) {
            CiCreditRecommendation rec = engine.recommend(s, e.getValue());
            assertThat(rec.getAuthoritative()).as(e.getKey()).isFalse();
            assertThat(rec.getDeterministicDecisionHash()).as(e.getKey()).isNotBlank();
            assertThat(rec.getRecommendationOutcome()).as(e.getKey()).isNotBlank();
        }
    }

    private DecisionRuntimeInput caseInput(String policy, String grade, BigDecimal amount,
                                           boolean fullMetrics, boolean legacyDefault) {
        DecisionRuntimeInput.Builder b = baseInput()
                .policyOverallOutcome(policy)
                .scoreResult(Map.of("grade", grade, "score", 650))
                .requestedAmount(amount);
        if (!fullMetrics) {
            b.metrics(Map.of());
        }
        DecisionRuntimeInput input = b.build();
        if (legacyDefault) {
            return input.withMetadata(Map.of("legacyUsedDefault", true));
        }
        return input;
    }

    // --- Banking / Bureau ---

    @Test
    void bankingBrePassStructuresWithTestStrategy() {
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("PASS")
                .policyRuleResults(List.of(Map.of(
                        "ruleId", "BANK_1", "ruleType", "HARD", "outcome", "PASS", "stageCode", "BANKING")))
                .build();
        CiCreditRecommendation rec = engine.recommend(strategy(), input);
        assertThat(rec.getDimensions().get("Eligibility")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> elig = (Map<String, Object>) rec.getDimensions().get("Eligibility");
        assertThat(elig.get("bankingBre")).isEqualTo("ELIGIBLE");
        assertThat(rec.getRecommendationOutcome()).isNotEqualTo(RecommendationOutcome.DECLINE.name());
        assertThat(rec.getRecommendedAmount()).isNotNull();
    }

    @Test
    void bureauFailDeclines() {
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("FAIL")
                .policyRuleResults(List.of(Map.of(
                        "ruleId", "BUR_1", "ruleType", "KNOCKOUT", "outcome", "FAIL", "stageCode", "BUREAU")))
                .build();
        CiCreditRecommendation rec = engine.recommend(strategy(), input);
        assertThat(rec.getRecommendationOutcome()).isEqualTo(RecommendationOutcome.DECLINE.name());
        assertThat(rec.getReasonCodes().stream().map(String::valueOf))
                .anyMatch(r -> r.contains("BUREAU") || r.contains("POLICY_OUTCOME") || r.contains("KNOCKOUT")
                        || r.contains("DECLINE"));
    }

    @Test
    void bankingWithoutParamsReportsLimitationUnlessFixture() {
        // Non-fixture strategy without amount params
        Map<String, Object> content = new LinkedHashMap<>(strategy().getContent());
        content.put("validationFixture", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> limit = new LinkedHashMap<>((Map<String, Object>) content.get("limitStrategy"));
        Map<String, Object> params = new LinkedHashMap<>();
        limit.put("params", params);
        content.put("limitStrategy", limit);
        CiDecisionStrategy s = CiDecisionStrategy.builder()
                .id(UUID.randomUUID()).tenantId(tenant).strategyCode("CUSTOM").version("1")
                .status("SHADOW").content(content).contentHash("x").build();
        DecisionRuntimeInput input = baseInput()
                .requestedAmount(null)
                .policyRuleResults(List.of(Map.of(
                        "ruleId", "B1", "outcome", "PASS", "stageCode", "BANKING", "ruleType", "HARD")))
                .metrics(Map.of())
                .build();
        CiCreditRecommendation rec = engine.recommend(s, input);
        assertThat(rec.getLimitations().stream().map(String::valueOf))
                .anyMatch(l -> l.contains("POLICY_PARAMETER_REQUIRED"));
    }

    // --- Comparison / view / impact / historical ---

    @Test
    void comparisonAndDecisionView() {
        CiCreditRecommendation rec = engine.recommend(strategy(), baseInput().build());
        Map<String, Object> cmp = new DecisionComparisonService().compare(
                Map.of("amount", new BigDecimal("1000000"), "tenure", 24, "authority", "L1"),
                Map.of("legacyUsedDefault", true),
                rec);
        assertThat(cmp.get("comparisonClasses")).isInstanceOf(List.class);

        Map<String, Object> view = new CreditDecisionViewBuilder().build(rec, baseInput().build(), cmp);
        assertThat(view).containsKeys("PolicyOutcome", "Amount", "Tenure", "Pricing",
                "Conditions", "ApprovalAuthority", "LegacyComparison");

        Map<String, Object> evidence = new CreditEvidenceViewBuilder().withDecisionView(Map.of(), view);
        assertThat(evidence).containsKey("CreditDecisionView");
    }

    @Test
    void historicalReplayAndImpact() {
        var hist = new DecisionHistoricalReplayService(engine).replay(
                strategy(), List.of(baseInput().build(), baseInput().requestedAmount(new BigDecimal("200000")).build()),
                "test");
        assertThat(hist.getSummary().get("type")).isEqualTo("DECISION_HISTORICAL_REPLAY");
        assertThat(hist.getSummary().get("notCreditPerformanceValidation")).isEqualTo(true);

        CiCreditRecommendation rec = engine.recommend(strategy(), baseInput().build());
        Map<String, Object> impact = new DecisionImpactAnalysisService().analyze(List.of(
                Map.of("legacy", Map.of("amount", new BigDecimal("1000000"), "outcome", "APPROVE", "authority", "L1"),
                        "canonical", rec)));
        assertThat(impact.get("cutoverReady")).isEqualTo(false);
    }

    // --- Performance ---

    @Test
    void performanceLightweight() {
        Map<String, Object> content = new LinkedHashMap<>(strategy().getContent());
        List<String> methods = List.of(
                "TURNOVER_LIMIT", "BANKING_CREDIT_LIMIT", "CASH_FLOW_LIMIT", "FOIR_LIMIT",
                "DSCR_LIMIT", "COLLATERAL_LIMIT", "INVOICE_FINANCE_LIMIT", "POLICY_CAP",
                "REQUESTED_AMOUNT", "TURNOVER_LIMIT");
        content.put("limitStrategy", Map.of(
                "combine", "MIN",
                "methods", methods,
                "params", DecisionValueHelperSafe(
                        DecisionValueHelperSafe(strategy().getContent().get("limitStrategy")).get("params"))));
        List<Map<String, Object>> comps = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            comps.add(Map.of("code", "COMP_" + i, "bps", i));
        }
        content.put("pricingStrategy", Map.of(
                "baseRate", 0.125, "floor", 0.11, "cap", 0.24, "components", comps));

        List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rules.add(Map.of("ruleId", "R" + i, "ruleType", "SOFT", "outcome", i % 10 == 0 ? "REFER" : "PASS"));
        }
        // 20 conditions via recon conflicts
        Map<String, Object> recons = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            recons.put("RECON_" + i, Map.of("outcome", "CONFLICT"));
        }

        CiDecisionStrategy s = CiDecisionStrategy.builder()
                .id(UUID.randomUUID()).tenantId(tenant).strategyCode("PERF").version("1")
                .status("SHADOW").content(content).contentHash("perf").build();
        DecisionRuntimeInput input = baseInput()
                .policyRuleResults(rules)
                .reconciliations(recons)
                .build();

        long start = System.nanoTime();
        CiCreditRecommendation rec = engine.recommend(s, input);
        long ms = (System.nanoTime() - start) / 1_000_000L;
        assertThat(rec.getAuthoritative()).isFalse();
        assertThat(rec.getLimitMethodResults().size()).isGreaterThanOrEqualTo(9);
        assertThat(rec.getPricingComponentResults().size()).isEqualTo(20);
        assertThat(rec.getConditionRecommendations().size()).isGreaterThanOrEqualTo(20);
        assertThat(ms).as("lightweight relative to canonicalization").isLessThan(5000L);
        System.out.println("P2 ShadowDecisionEngine perf latencyMs=" + ms);
    }

    @Test
    void tenureClamp() {
        var t = tenureEngine.compute(Map.of("minMonths", 6, "maxMonths", 36, "defaultMonths", 18),
                baseInput().requestedTenureMonths(60).build());
        assertThat(t.recommendedTenure()).isEqualTo(36);
    }

    @Test
    void deviationsNeverAutoApproved() {
        DecisionRuntimeInput input = baseInput()
                .policyOverallOutcome("PASS")
                .policyRuleResults(List.of(Map.of(
                        "ruleId", "SOFT1", "ruleType", "SOFT", "outcome", "FAIL")))
                .build();
        CiCreditRecommendation rec = engine.recommend(strategy(), input);
        assertThat(rec.getDeviations()).isNotEmpty();
        assertThat(rec.getDeviations()).allMatch(d -> DeviationStatus.REQUESTED.name().equals(d.getStatus()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> DecisionValueHelperSafe(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
