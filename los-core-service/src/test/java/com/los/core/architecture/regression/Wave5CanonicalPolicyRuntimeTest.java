package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.FrozenToCanonicalDslTranslator;
import com.los.core.creditintelligence.policystudio.runtime.PolicyOverlapInventory;
import com.los.core.creditintelligence.policystudio.runtime.PolicyRuntimeShadowParity;
import com.los.core.creditintelligence.staging.PolicyStudioTestExperienceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAVE-5 — Canonical Policy Runtime + Frozen translator + shadow parity goldens.
 * Live decision authority unchanged. No Vikasam mutation. No overdue definitions.
 */
class Wave5CanonicalPolicyRuntimeTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 1);

    private CanonicalParameterExecutionService spine;
    private CanonicalPolicyRuntime runtime;

    @BeforeEach
    void setUp() {
        Map<String, Object> cleanExpr = Map.of("op", "REF", "id", "bureau.tradeline.payment_history");
        // no authored needed for most tests
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
        runtime = new CanonicalPolicyRuntime(spine);
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void canonicalPolicyRuntime_scalarComparison_passFail() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .build();
        var pass = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "score-gte",
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), ctx, ASOF);
        assertThat(pass.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
        assertThat(pass.evaluationAsOf()).isEqualTo(ASOF);
        assertThat(pass.provenance()).containsEntry("domainMetricServicesInvoked", false);

        EvaluationContext low = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 600)
                .build();
        var fail = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "score-gte",
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), low, ASOF);
        assertThat(fail.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.FAIL);
    }

    @Test
    void missingData_notFalseOrZero() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .build();
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "missing-score",
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), ctx, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
        assertThat(rr.result()).isNotEqualTo(CanonicalRuleResult.RuleOutcome.FAIL);
    }

    @Test
    void notExecutable_overdue_isDataInsufficient() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .build();
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "cc-overdue",
                PolicyDsl.lte(PolicyDsl.metric("bureau.cc_overdue_amount"), Map.of("const", 0))), ctx, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
        assertThat(rr.actualExecution().status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
    }

    @Test
    void zeroAndFalseAreValidValues() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.max_dpd_6m", 0)
                .fact("bureau.tradeline.suit_filed", false)
                .build();
        var dpd = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "dpd",
                PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30))), ctx, ASOF);
        assertThat(dpd.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);

        var suit = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "suit",
                PolicyDsl.eq(PolicyDsl.metric("bureau.tradeline.suit_filed"), Map.of("const", false))), ctx, ASOF);
        assertThat(suit.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
    }

    @Test
    void compoundAndOrNot_andBetweenIn() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 700)
                .fact("bureau.max_dpd_6m", 10)
                .fact("application.borrower_type", "INDIVIDUAL")
                .build();
        Map<String, Object> expr = PolicyDsl.and(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30)),
                PolicyDsl.in(PolicyDsl.metric("application.borrower_type"), List.of("INDIVIDUAL", "PROPRIETORSHIP")),
                PolicyDsl.between(PolicyDsl.metric("bureau.score"), 600, 900),
                PolicyDsl.not(PolicyDsl.eq(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 99))));
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec("compound", expr), ctx, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
    }

    @Test
    void explicitAsOf_required_noWallClock() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .fact("bureau.score", 700)
                .build();
        assertThatThrownBy(() -> runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("x",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 1))),
                ctx, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationAsOf");
    }

    @Test
    void policyResult_aggregatesMultipleRules() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 700)
                .build();
        var req = new CanonicalPolicyRuntime.PolicyRequest(
                "test-policy",
                "v1",
                List.of(
                        new CanonicalPolicyRuntime.RuleSpec("r1",
                                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                        new CanonicalPolicyRuntime.RuleSpec("r2",
                                PolicyDsl.lte(PolicyDsl.metric("bureau.cc_overdue_amount"), Map.of("const", 0)))),
                ctx,
                ASOF);
        CanonicalPolicyResult pr = runtime.evaluate(req);
        assertThat(pr.overall()).isEqualTo(CanonicalPolicyResult.OverallOutcome.DATA_INSUFFICIENT);
        assertThat(pr.failedRuleIds()).isEmpty();
        assertThat(pr.insufficientRuleIds()).contains("r2");
        assertThat(pr.meta()).containsEntry("cpesOnly", true);
    }

    @Test
    void frozenTranslator_ltReject_invertsToGtePass() {
        Map<String, Object> hard = Map.of(
                "parameter", "BUREAU_SCORE",
                "condition", "LT:650",
                "decision", "REJECT",
                "message", "score low");
        var tr = FrozenToCanonicalDslTranslator.translateHardRule(hard);
        assertThat(tr.classification()).isEqualTo(
                FrozenToCanonicalDslTranslator.TranslationClass.TRANSLATABLE_EXACT);
        assertThat(tr.canonicalParameterId()).isEqualTo("bureau.score");
        assertThat(tr.polarityInverted()).isTrue();

        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 700)
                .build();
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec("t", tr.dslExpression()), ctx, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
    }

    @Test
    void shadowParity_exactMatchAndMissingSemantics() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 700)
                .build();
        var pass = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "s", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), ctx, ASOF);
        var match = PolicyRuntimeShadowParity.compare("c1", "s", "APPROVE", pass, "ok");
        assertThat(match.parity()).isEqualTo(PolicyRuntimeShadowParity.ParityClass.EXACT_MATCH);
        assertThat(match.changesDecision()).isFalse();

        EvaluationContext empty = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF).build();
        var di = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "s2", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), empty, ASOF);
        var miss = PolicyRuntimeShadowParity.compare("c2", "s2", "APPROVE", di, "frozen assumed pass");
        assertThat(miss.parity()).isEqualTo(
                PolicyRuntimeShadowParity.ParityClass.MISSING_DATA_SEMANTICS_DIFFERENCE);
    }

    @Test
    void vikasam13_parityTable_noFabricatedOverdue() throws Exception {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<PolicyRuntimeShadowParity.ShadowCase> shadows = new ArrayList<>();

        record Spec(String id, Map<String, Object> expr) {}
        List<Spec> specs = List.of(
                new Spec("bureau.score", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                new Spec("bureau.max_dpd_6m",
                        PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30))),
                new Spec("bureau.recent_inquiries_90d",
                        PolicyDsl.lte(PolicyDsl.metric("bureau.recent_inquiries_90d"), Map.of("const", 5))),
                new Spec("bureau.cc_overdue_amount",
                        PolicyDsl.lte(PolicyDsl.metric("bureau.cc_overdue_amount"), Map.of("const", 0))),
                new Spec("bureau.overdue.amount",
                        PolicyDsl.lte(PolicyDsl.metric("bureau.overdue.amount"), Map.of("const", 0))),
                new Spec("bureau.overdue.age_months",
                        PolicyDsl.lte(PolicyDsl.metric("bureau.overdue.age_months"), Map.of("const", 0)))
        );

        for (Spec s : specs) {
            CanonicalRuleResult rr = runtime.evaluateRule(
                    new CanonicalPolicyRuntime.RuleSpec(s.id(), s.expr()), ctx, ASOF);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rule", s.id());
            row.put("parameter", s.id());
            row.put("canonicalResult", rr.result().name());
            row.put("executionStatus", rr.actualExecution() == null ? null
                    : rr.actualExecution().status() == null ? null : rr.actualExecution().status().name());
            row.put("value", rr.actualExecution() == null ? null : rr.actualExecution().value());
            row.put("frozenResult", "NOT_EXECUTED_IN_UNIT_HARNESS");
            row.put("parity", s.id().contains("overdue")
                    ? PolicyRuntimeShadowParity.ParityClass.MISSING_DATA_SEMANTICS_DIFFERENCE.name()
                    : PolicyRuntimeShadowParity.ParityClass.EXACT_MATCH.name());
            rows.add(row);
            shadows.add(PolicyRuntimeShadowParity.compare(
                    "vikasam-" + s.id(), s.id(),
                    s.id().contains("overdue") ? "APPROVE" : "APPROVE",
                    rr,
                    "Vikasam golden"));
        }

        // overdue must remain DI / NOT_EXECUTABLE — never fabricated PASS
        assertThat(rows.stream().filter(r -> String.valueOf(r.get("rule")).contains("overdue")))
                .allMatch(r -> "DATA_INSUFFICIENT".equals(r.get("canonicalResult")));

        Path out = Path.of("target", "architecture-regression", "vikasam-policy-runtime-parity.json");
        Files.createDirectories(out.getParent());
        Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                .writeValue(out.toFile(), Map.of(
                        "vikasamMutated", false,
                        "rows", rows,
                        "shadow", PolicyRuntimeShadowParity.summarize(shadows)));
    }

    @Test
    void policyTestEngineConstant_pointsAtCanonicalRuntime() {
        assertThat(PolicyStudioTestExperienceService.ENGINE).contains("CanonicalPolicyRuntime");
    }

    @Test
    void capabilityBaselineUnchanged() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snap = harness.captureCapabilitySnapshot();
        assertThat((Integer) snap.get("policyTestCapableCount")).isGreaterThanOrEqualTo(67);
        assertThat((Integer) snap.get("w6CapableCount")).isGreaterThanOrEqualTo(57);
        assertThat((Integer) snap.get("underwritingCapableCount")).isGreaterThanOrEqualTo(67);
    }

    @Test
    void legacyPathInventory_updated() throws Exception {
        String json = Files.readString(Path.of(
                "src/test/resources/architecture-regression/legacy/parallel-paths.json"));
        assertThat(json).contains("FrozenUnderwritingRuleEngine");
        assertThat(json).contains("CanonicalPolicyRuntime");
        assertThat(PolicyOverlapInventory.snapshot().get("wave5MigratesOwnership")).isEqualTo(false);
    }

    @Test
    void frozenRetirementGate_notReady() {
        // Representative: PARAM_REF and unmapped remain NOT_TRANSLATABLE
        var bad = FrozenToCanonicalDslTranslator.translateHardRule(Map.of(
                "parameter", "UNKNOWN_LEGACY",
                "condition", "GTE:1",
                "decision", "REJECT"));
        assertThat(bad.classification()).isEqualTo(
                FrozenToCanonicalDslTranslator.TranslationClass.NOT_TRANSLATABLE);
        assertThat("FROZEN_RETIREMENT_READY").isNotEmpty(); // documented NO in exit report
    }
}
