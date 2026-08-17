package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.graph.PolicyGraphPolicyTestService;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalCompatibilityRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.FrozenToCanonicalDslTranslator;
import com.los.core.creditintelligence.policystudio.runtime.PolicyRuntimeShadowParity;
import com.los.core.creditintelligence.policystudio.runtime.SharedCanonicalEvaluationSupport;
import com.los.core.creditintelligence.policystudio.runtime.SimulationOverride;
import com.los.core.creditintelligence.policystudio.runtime.TargetLiveCanonicalPolicyEvaluation;
import com.los.core.creditintelligence.staging.PolicyStudioTestExperienceService;
import com.los.core.service.underwriting.ApplicationScorecardParameterResolver;
import com.los.core.service.underwriting.UnderwritingEvaluationContextFactory;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAVE-6 — Test/Live execution convergence onto CanonicalPolicyRuntime + shared context/asOf.
 * Live decision authority unchanged. No parameter remediation. Capability 40/30/40.
 */
class Wave6CanonicalConvergenceTest {

    private static final LocalDate ASOF = SharedCanonicalEvaluationSupport.CANONICAL_POLICY_TEST_AS_OF;

    private CanonicalParameterExecutionService spine;
    private CanonicalPolicyRuntime runtime;
    private TargetLiveCanonicalPolicyEvaluation targetLive;

    @BeforeEach
    void setUp() {
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
        runtime = new CanonicalPolicyRuntime(spine);
        targetLive = new TargetLiveCanonicalPolicyEvaluation(runtime);
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void sharedEvaluationContextContract_normalizePreservesFacts() {
        EvaluationContext src = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .input("bureau.cc_overdue_amount", 7000)
                .build();
        EvaluationContext n = SharedCanonicalEvaluationSupport.normalize(src, EvaluationMode.POLICY_TEST, ASOF);
        assertThat(n.evaluationAsOf()).isEqualTo(ASOF);
        assertThat(n.mode()).isEqualTo(EvaluationMode.POLICY_TEST);
        assertThat(n.facts().get("bureau.score")).isEqualTo(720);
        assertThat(n.inputs().get("bureau.cc_overdue_amount")).isEqualTo(7000);
        assertThat(n.entities().get("sharedCanonicalContext")).isEqualTo(true);
    }

    @Test
    void policyTestLiveContextParity_sameRuntimeSameOutcome() {
        EvaluationContext shared = SharedCanonicalEvaluationSupport.fromMaterializedFacts(
                EvaluationMode.POLICY_TEST, ASOF,
                Map.of("bureau.score", 700), Map.of());
        var spec = new CanonicalPolicyRuntime.RuleSpec(
                "score", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)));
        CanonicalRuleResult pt = runtime.evaluateRule(spec, shared, ASOF);
        CanonicalPolicyResult live = targetLive.evaluate(
                "demo", "1", List.of(spec),
                SharedCanonicalEvaluationSupport.normalize(shared, EvaluationMode.UNDERWRITING, ASOF),
                ASOF);
        assertThat(pt.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
        assertThat(live.ruleResults().get(0).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
        assertThat(live.meta().get("liveDecisionAuthorityChanged")).isEqualTo(false);
        assertThat(PolicyStudioTestExperienceService.ENGINE).contains("CanonicalPolicyRuntime");
        assertThat(PolicyGraphPolicyTestService.ENGINE).isEqualTo(CanonicalPolicyRuntime.RUNTIME_CLASS);
    }

    @Test
    void simulationOverrideHonesty_doesNotRaiseCapability() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .input("bureau.cc_overdue_amount", 7000)
                .build();
        ExecutionResult er = spine.resolveAndExecute("bureau.cc_overdue_amount", ctx);
        assertThat(er.valueAvailable()).isTrue();
        assertThat(er.value()).isEqualTo(7000);
        assertThat(er.capability()).isFalse();
        assertThat(er.simulatedValue()).isTrue();
        Map<String, Object> honesty = SharedCanonicalEvaluationSupport.honestyProjection(er);
        assertThat(honesty.get("simulationChangesRealCapability")).isEqualTo(false);
        assertThat(honesty.get("notExecutableInRealContext")).isEqualTo(true);
        assertThat(SimulationOverride.of("bureau.cc_overdue_amount", 7000).overrideKind())
                .isEqualTo(SimulationOverride.KIND_SIMULATED_VALUE);
    }

    @Test
    void asOfDeterminism_identicalRerun() {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        var spec = new CanonicalPolicyRuntime.RuleSpec(
                "r", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)));
        CanonicalRuleResult a = runtime.evaluateRule(spec, ctx, ASOF);
        CanonicalRuleResult b = runtime.evaluateRule(spec, ctx, ASOF);
        assertThat(a.result()).isEqualTo(b.result());
        assertThat(a.evaluationAsOf()).isEqualTo(ASOF);
        assertThatThrownBy(() -> runtime.evaluateRule(spec,
                EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit evaluationAsOf");
    }

    @Test
    void missingDataParity_identicalAcrossModes() {
        EvaluationContext emptyPt = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF).build();
        EvaluationContext emptyUw = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF).build();
        var spec = new CanonicalPolicyRuntime.RuleSpec(
                "m", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)));
        assertThat(runtime.evaluateRule(spec, emptyPt, ASOF).result())
                .isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
        assertThat(runtime.evaluateRule(spec, emptyUw, ASOF).result())
                .isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
    }

    @Test
    void onMissingSemantics_passFailReferDefault() {
        EvaluationContext empty = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF).build();
        Map<String, Object> expr = PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650));
        assertThat(runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("d", null, expr, PolicyDslInterpreterV1.DATA_INSUFFICIENT),
                empty, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
        assertThat(runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("p", null, expr, "PASS"),
                empty, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
        assertThat(runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("f", null, expr, "FAIL"),
                empty, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.FAIL);
        assertThat(runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("r", null, expr, "REFER"),
                empty, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
    }

    @Test
    void compatibilityRegistry_dangerousAliasNotInRuntimeStampList() {
        List<String> runtime = ParameterExecutabilitySupport.runtimeFactAliases("bureau.recent_inquiries_90d");
        assertThat(runtime).contains("bureau.inquiries.count_90d");
        assertThat(runtime).doesNotContain("BUREAU_ENQUIRIES_3M");
        assertThat(CanonicalCompatibilityRegistry.dangerousAliases("bureau.recent_inquiries_90d"))
                .contains("BUREAU_ENQUIRIES_3M");
        assertThat(CanonicalCompatibilityRegistry.exactCanonicalIdForPath("BUREAU_ENQUIRIES_3M")).isNull();
        long dangerous = CanonicalCompatibilityRegistry.allEntries().stream()
                .filter(e -> e.classification() == CanonicalCompatibilityRegistry.AliasClass.DANGEROUS_ALIAS_REJECTED)
                .count();
        assertThat(dangerous).isGreaterThan(0);
    }

    @Test
    void w6ToPolicyRuntimeContextParity() {
        Map<String, Object> facts = Map.of("bureau.score", 710, "bureau.max_dpd_6m", 0);
        EvaluationContext w6 = SharedCanonicalEvaluationSupport.fromMaterializedFacts(
                EvaluationMode.W6_ACQUISITION, ASOF, facts, Map.of());
        EvaluationContext policy = SharedCanonicalEvaluationSupport.normalize(w6, EvaluationMode.UNDERWRITING, ASOF);
        assertThat(policy.facts().get("bureau.score")).isEqualTo(w6.facts().get("bureau.score"));
        assertThat(policy.evaluationAsOf()).isEqualTo(w6.evaluationAsOf());
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "s", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), policy, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);
    }

    @Test
    void scorecardPolicyContextParity_sameFactoryAsOf() {
        // Factory uses ApplicationPolicyQueryFactory — null app → missing asOf flagged
        var ctx = UnderwritingEvaluationContextFactory.build(
                EvaluationMode.UNDERWRITING, null, null, ASOF, Map.of("bureau.score", 680));
        assertThat(ctx.evaluationAsOf()).isEqualTo(ASOF);
        assertThat(ctx.facts().get("bureau.score")).isEqualTo(680);
        assertThat(ApplicationScorecardParameterResolver.ageYears(
                Map.of("dateOfBirth", "1990-01-01"), ASOF)).isNotNull();
        assertThat(ApplicationScorecardParameterResolver.ageYears(
                Map.of("dateOfBirth", "1990-01-01"))).isNull();
    }

    @Test
    void compoundAndOperators_andNonVikasamCorpus() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 700)
                .fact("bureau.max_dpd_6m", 0)
                .build();
        List<Map<String, Object>> cases = List.of(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30)),
                PolicyDsl.between(PolicyDsl.metric("bureau.score"), Map.of("const", 600), Map.of("const", 900)),
                PolicyDsl.and(
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                        PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30))),
                PolicyDsl.or(
                        PolicyDsl.lt(PolicyDsl.metric("bureau.score"), Map.of("const", 100)),
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                PolicyDsl.not(PolicyDsl.lt(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))
        );
        int match = 0;
        for (int i = 0; i < cases.size(); i++) {
            CanonicalRuleResult pt = runtime.evaluateRule(
                    new CanonicalPolicyRuntime.RuleSpec("c" + i, cases.get(i)), ctx, ASOF);
            CanonicalRuleResult live = targetLive.evaluate("p", "1",
                    List.of(new CanonicalPolicyRuntime.RuleSpec("c" + i, cases.get(i))),
                    SharedCanonicalEvaluationSupport.normalize(ctx, EvaluationMode.UNDERWRITING, ASOF),
                    ASOF).ruleResults().get(0);
            if (pt.result() == live.result()) match++;
        }
        assertThat(match).isEqualTo(cases.size());
    }

    @Test
    void vikasamTestLiveParity_table() throws Exception {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<PolicyRuntimeShadowParity.ShadowCase> shadows = new ArrayList<>();
        record Spec(String id, Map<String, Object> expr) {}
        List<Spec> specs = List.of(
                new Spec("bureau.score", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                new Spec("bureau.max_dpd_6m", PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30))),
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
            var rule = new CanonicalPolicyRuntime.RuleSpec(s.id(), s.expr());
            CanonicalRuleResult pt = runtime.evaluateRule(rule, ctx, ASOF);
            CanonicalRuleResult live = targetLive.evaluate("vikasam", "golden", List.of(rule),
                    SharedCanonicalEvaluationSupport.normalize(ctx, EvaluationMode.UNDERWRITING, ASOF), ASOF)
                    .ruleResults().get(0);
            // Graph uses same runtime class — parity by construction
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rule", s.id());
            row.put("policyTest", pt.result().name());
            row.put("targetLive", live.result().name());
            row.put("graphRuntime", PolicyGraphPolicyTestService.ENGINE);
            row.put("match", pt.result() == live.result());
            row.put("parity", pt.result() == live.result()
                    ? PolicyRuntimeShadowParity.ParityClass.EXACT_MATCH.name()
                    : PolicyRuntimeShadowParity.ParityClass.UNKNOWN.name());
            rows.add(row);
            shadows.add(PolicyRuntimeShadowParity.compare("v6-" + s.id(), s.id(),
                    pt.result().name(), live, "wave6"));
        }
        assertThat(rows).allMatch(r -> Boolean.TRUE.equals(r.get("match")));
        assertThat(rows.stream().filter(r -> String.valueOf(r.get("rule")).contains("overdue")))
                .allMatch(r -> "DATA_INSUFFICIENT".equals(r.get("policyTest")));
        Path out = Path.of("target", "architecture-regression", "wave6-vikasam-test-live-parity.json");
        Files.createDirectories(out.getParent());
        Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                .writeValue(out.toFile(), Map.of("vikasamMutated", false, "rows", rows,
                        "shadow", PolicyRuntimeShadowParity.summarize(shadows)));
    }

    @Test
    void frozenShadowCorpus_andParamRefTranslation() {
        var exact = FrozenToCanonicalDslTranslator.translateHardRule(Map.of(
                "parameter", "BUREAU_SCORE", "condition", "LT:650", "decision", "REJECT"));
        assertThat(exact.classification()).isEqualTo(
                FrozenToCanonicalDslTranslator.TranslationClass.TRANSLATABLE_EXACT);

        var paramRef = FrozenToCanonicalDslTranslator.translateHardRule(Map.of(
                "parameter", "BUREAU_SCORE",
                "condition", "PARAM_REF:MAX_DPD_6M",
                "decision", "REJECT"));
        // May be COMPAT or NOT_TRANSLATABLE depending on mapper — never invent IDs
        assertThat(paramRef.classification()).isIn(
                FrozenToCanonicalDslTranslator.TranslationClass.TRANSLATABLE_WITH_EXPLICIT_COMPATIBILITY,
                FrozenToCanonicalDslTranslator.TranslationClass.NOT_TRANSLATABLE);

        var unknown = FrozenToCanonicalDslTranslator.translateHardRule(Map.of(
                "parameter", "UNKNOWN_LEGACY_XYZ", "condition", "GTE:1", "decision", "REJECT"));
        assertThat(unknown.classification()).isEqualTo(
                FrozenToCanonicalDslTranslator.TranslationClass.NOT_TRANSLATABLE);

        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.score", 700).build();
        CanonicalRuleResult cpr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "t", exact.dslExpression()), ctx, ASOF);
        var shadow = PolicyRuntimeShadowParity.compare("f1", "t", "APPROVE", cpr, "frozen");
        assertThat(shadow.changesDecision()).isFalse();
        assertThat(shadow.parity()).isEqualTo(PolicyRuntimeShadowParity.ParityClass.EXACT_MATCH);
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
    void legacyInventoryUpdated() throws Exception {
        String json = Files.readString(Path.of(
                "src/test/resources/architecture-regression/legacy/parallel-paths.json"));
        assertThat(json).contains("CanonicalPolicyRuntime");
        assertThat(json).contains("TargetLiveCanonicalPolicyEvaluation");
        assertThat(json).contains("WAVE_6");
        assertThat(json).contains("PolicyGraphPolicyTestService");
    }

    @Test
    void zeroFalseEmptyNotCollapsedFromMissing() {
        EvaluationContext zero = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.max_dpd_6m", 0).build();
        assertThat(runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "z", PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 0))),
                zero, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);

        EvaluationContext missing = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF).build();
        assertThat(runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "m", PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 0))),
                missing, ASOF).result()).isEqualTo(CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT);
        assertThat(ExecutionStatus.NOT_EXECUTABLE.name()).isNotBlank();
    }
}
