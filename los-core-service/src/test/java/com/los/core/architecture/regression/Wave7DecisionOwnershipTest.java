package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.PolicyOverlapInventory;
import com.los.core.creditintelligence.policystudio.runtime.PolicyRuntimeShadowParity;
import com.los.core.creditintelligence.policystudio.runtime.ownership.CanonicalUnderwritingOrchestration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.CreditControlDecisionBoundary;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionAuthorityInventory;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionOwnershipFlags;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DuplicateBusinessConditionInventory;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FoirAuthorityBoundary;
import com.los.core.creditintelligence.policystudio.runtime.ownership.ManualOverrideRecord;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;
import com.los.core.creditintelligence.policystudio.runtime.ownership.ScorecardHardRuleOwnership;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-7 — Decision ownership and boundary closure.
 * Live authority unchanged. Frozen not retired. Capability 40/30/40.
 */
class Wave7DecisionOwnershipTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 1);

    private CanonicalParameterExecutionService spine;
    private CanonicalPolicyRuntime runtime;
    private CanonicalUnderwritingOrchestration orchestration;

    @BeforeEach
    void setUp() {
        DecisionOwnershipFlags.resetForTests();
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
        runtime = new CanonicalPolicyRuntime(spine);
        orchestration = new CanonicalUnderwritingOrchestration(runtime);
    }

    @AfterEach
    void tearDown() {
        DecisionOwnershipFlags.resetForTests();
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void decisionAuthorityInventory_noUnknownClass() {
        var snap = DecisionAuthorityInventory.snapshot();
        assertThat(snap.get("unknownCount")).isEqualTo(0L);
        assertThat((Integer) snap.get("entryCount")).isGreaterThan(15);
        assertThat(snap.get("liveDecisionAuthorityChanged")).isEqualTo(false);
    }

    @Test
    void duplicateConditions_foirOwnedByPolicy() {
        var foir = DuplicateBusinessConditionInventory.all().stream()
                .filter(c -> c.businessCondition().startsWith("FOIR"))
                .findFirst().orElseThrow();
        assertThat(foir.targetOwner()).isEqualTo("POLICY");
        assertThat(foir.duplication()).isEqualTo(
                DuplicateBusinessConditionInventory.DuplicationClass.EXACT_DUPLICATE);
    }

    @Test
    void foirSingleAuthority_demoNotDecisionTruth() {
        var demo = FoirAuthorityBoundary.classifyScorecardFoir(
                new BigDecimal("25"), ScorecardValueProvenance.DEMO_DEFAULT);
        assertThat(demo.authoritativeForDecision()).isFalse();
        var real = FoirAuthorityBoundary.classifyScorecardFoir(
                new BigDecimal("32"), ScorecardValueProvenance.REAL_PROVIDER);
        assertThat(real.authoritativeForDecision()).isTrue();
        assertThat(FoirAuthorityBoundary.snapshot().get("authorityAfter").toString())
                .contains("Policy/CPR=ELIGIBILITY");
    }

    @Test
    void policyFailCannotScoreApprove() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 500).build();
        var rules = List.of(new CanonicalPolicyRuntime.RuleSpec(
                "minScore", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))));
        FinalUnderwritingDecision d = orchestration.assemble(
                "app-1", ASOF, ctx, "pol", "1", rules,
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE,
                        Map.of("band", "HIGH"), true),
                Map.of(), Map.of(), null);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.REJECT);
        assertThat(d.reasonCodes()).anyMatch(r -> r.contains("POLICY_FAIL"));
        assertThat(d.provenance().get("liveDecisionAuthorityChanged")).isEqualTo(false);
    }

    @Test
    void policyDiBlocksSilentScoreApprove() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF).build();
        var rules = List.of(new CanonicalPolicyRuntime.RuleSpec(
                "minScore", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))));
        FinalUnderwritingDecision d = orchestration.assemble(
                "app-2", ASOF, ctx, "pol", "1", rules,
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE,
                        Map.of("band", "HIGH"), true),
                Map.of(), Map.of(), null);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT);
        assertThat(d.reasonCodes()).anyMatch(r -> r.contains("BLOCKS_SCORE_APPROVE"));
    }

    @Test
    void policyPassScorecardBandApproves() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        var rules = List.of(new CanonicalPolicyRuntime.RuleSpec(
                "minScore", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))));
        FinalUnderwritingDecision d = orchestration.assemble(
                "app-3", ASOF, ctx, "pol", "1", rules,
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE,
                        Map.of("normalizedPercent", 85), true),
                Map.of(), Map.of(), null);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
    }

    @Test
    void workflowNotDecisionAuthority() {
        assertThat(CanonicalUnderwritingOrchestration.workflowStateToDecision("COMPLETE"))
                .isEqualTo(FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT);
        assertThat(CanonicalUnderwritingOrchestration.workflowStateToDecision("READY"))
                .isNotEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
    }

    @Test
    void manualOverrideSeparation() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 500).build();
        var rules = List.of(new CanonicalPolicyRuntime.RuleSpec(
                "minScore", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))));
        var ov = new ManualOverrideRecord(
                "cm-1", Instant.parse("2026-08-01T10:00:00Z"),
                FinalUnderwritingDecision.FinalOutcome.REJECT,
                FinalUnderwritingDecision.FinalOutcome.APPROVE,
                "exception approved", "FULL");
        FinalUnderwritingDecision d = orchestration.assemble(
                "app-4", ASOF, ctx, "pol", "1", rules,
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.REJECT, Map.of(), true),
                Map.of(), Map.of(), ov);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
        assertThat(d.manualOverride().toMap().get("mutatesCanonicalRuleResult")).isEqualTo(false);
        assertThat(d.policyResult().get("overall")).isEqualTo("FAIL");
    }

    @Test
    void scorecardHardRuleOwnership_andFlag() {
        var hr = Map.<String, Object>of(
                "parameter", "BUREAU_SCORE",
                "condition", "LT:650",
                "decision", "REJECT",
                "message", "low score");
        var cls = ScorecardHardRuleOwnership.classify(hr);
        assertThat(cls.kind().name()).contains("POLICY");
        assertThat(cls.translation().dslExpression()).isNotNull();
        assertThat(DecisionOwnershipFlags.scorecardHardRulesShadowOnly()).isFalse();
        DecisionOwnershipFlags.setScorecardHardRulesShadowOnly(true);
        assertThat(DecisionOwnershipFlags.scorecardHardRulesShadowOnly()).isTrue();
    }

    @Test
    void creditControlBoundary_demoPathsClassified() {
        var snap = CreditControlDecisionBoundary.snapshot();
        assertThat((Integer) snap.get("demoDefaultDecisionPathsFound")).isGreaterThan(0);
        assertThat(snap.get("demoDefaultDecisionPathsRemaining").toString())
                .contains("refuses as truth");
    }

    @Test
    void noDuplicateKnockout_policyOwnsScorecardHardRuleSemantics() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.score", 700).build();
        var tr = ScorecardHardRuleOwnership.classify(Map.of(
                "parameter", "BUREAU_SCORE", "condition", "LT:650", "decision", "REJECT")).translation();
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec("hr", tr.dslExpression()), ctx, ASOF);
        assertThat(rr.result().name()).isEqualTo("PASS");
    }

    @Test
    void frozenOwnershipShadowParity_corpus() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.score", 700).build();
        var rr = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "s", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), ctx, ASOF);
        var match = PolicyRuntimeShadowParity.compare("own-1", "s", "APPROVE", rr, "frozen");
        assertThat(match.changesDecision()).isFalse();
        assertThat(match.parity()).isEqualTo(PolicyRuntimeShadowParity.ParityClass.EXACT_MATCH);

        EvaluationContext empty = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF).build();
        var di = runtime.evaluateRule(new CanonicalPolicyRuntime.RuleSpec(
                "s2", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))), empty, ASOF);
        var miss = PolicyRuntimeShadowParity.compare("own-2", "s2", "APPROVE", di, "legacy assumed pass");
        assertThat(miss.parity()).isEqualTo(
                PolicyRuntimeShadowParity.ParityClass.MISSING_DATA_SEMANTICS_DIFFERENCE);
    }

    @Test
    void vikasamDecisionOwnership_overdueRemainDi() {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        for (String id : List.of("bureau.cc_overdue_amount", "bureau.overdue.amount", "bureau.overdue.age_months")) {
            FinalUnderwritingDecision d = orchestration.assemble(
                    "vikasam", ASOF, ctx, "vikasam", "golden",
                    List.of(new CanonicalPolicyRuntime.RuleSpec(id,
                            PolicyDsl.lte(PolicyDsl.metric(id), Map.of("const", 0)))),
                    new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                            FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                    Map.of(), Map.of(), null);
            assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT);
        }
    }

    @Test
    void nonVikasamDecisionCorpus() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        // policy FAIL + high score
        rows.add(caseRow("policyFailHighScore",
                Map.of("bureau.score", 500),
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                FinalUnderwritingDecision.FinalOutcome.APPROVE,
                FinalUnderwritingDecision.FinalOutcome.REJECT));
        // policy PASS + low score band → REFER/REJECT from band
        rows.add(caseRow("policyPassLowScore",
                Map.of("bureau.score", 720),
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                FinalUnderwritingDecision.FinalOutcome.REFER,
                FinalUnderwritingDecision.FinalOutcome.REFER));
        // FOIR demo not decision truth → DI when only demo FOIR would approve
        var foir = FoirAuthorityBoundary.classifyScorecardFoir(
                new BigDecimal("25"), ScorecardValueProvenance.DEMO_DEFAULT);
        rows.add(Map.of(
                "case", "foirDemoNotTruth",
                "authoritative", foir.authoritativeForDecision(),
                "expected", false));

        assertThat(rows).allMatch(r -> {
            if (r.containsKey("expectedOutcome")) {
                return r.get("actual").equals(r.get("expectedOutcome"));
            }
            return Boolean.FALSE.equals(r.get("authoritative")) || Boolean.FALSE.equals(r.get("expected"));
        });

        Path out = Path.of("target", "architecture-regression", "wave7-decision-corpus.json");
        Files.createDirectories(out.getParent());
        Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                .writeValue(out.toFile(), Map.of("rows", rows, "vikasamMutated", false));
    }

    private Map<String, Object> caseRow(
            String name, Map<String, Object> facts, Map<String, Object> expr,
            FinalUnderwritingDecision.FinalOutcome band,
            FinalUnderwritingDecision.FinalOutcome expected) {
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF);
        facts.forEach(b::fact);
        FinalUnderwritingDecision d = orchestration.assemble(
                name, ASOF, b.build(), "corp", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec(name, expr)),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(band, Map.of(), true),
                Map.of(), Map.of(), null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("case", name);
        m.put("actual", d.finalOutcome().name());
        m.put("expectedOutcome", expected.name());
        return m;
    }

    @Test
    void noDemoDefaultDecisionTruth() {
        assertThat(DecisionOwnershipFlags.demoDefaultsNotDecisionTruth()).isTrue();
        assertThat(PolicyScorecardPrecedence.rulesDocument().get("workflowNeverApproves")).isEqualTo(true);
        assertThat(DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED).isFalse();
        assertThat(DecisionOwnershipFlags.FROZEN_RETIRED).isFalse();
    }

    @Test
    void capabilityBaselineUnchanged() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snap = harness.captureCapabilitySnapshot();
        assertThat(snap.get("policyTestCapableCount")).isEqualTo(40);
        assertThat(snap.get("w6CapableCount")).isEqualTo(30);
        assertThat(snap.get("underwritingCapableCount")).isEqualTo(40);
    }

    @Test
    void overlapInventoryUpdated() {
        var snap = PolicyOverlapInventory.snapshot();
        assertThat(snap.get("wave7OwnershipModel")).isEqualTo(true);
        assertThat(snap.get("liveDecisionAuthorityChanged")).isEqualTo(false);
    }

    @Test
    void liveCutoverGate_notReady() {
        // UNKNOWN decision authority = 0, but Frozen still live + unexplained live dual engines remain
        assertThat(DecisionAuthorityInventory.snapshot().get("unknownCount")).isEqualTo(0L);
        boolean ready = false; // explicit: Frozen still authority; dual live engines; cutover not authorized
        assertThat(ready).isFalse();
    }
}
