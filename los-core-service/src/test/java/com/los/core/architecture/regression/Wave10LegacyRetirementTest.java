package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType;
import com.los.core.creditintelligence.policystudio.certification.CertificationScopeType;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationAuthority;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationService;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalCompatibilityRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.ownership.CanonicalLiveCutoverReadiness;
import com.los.core.creditintelligence.policystudio.runtime.ownership.CanonicalUnderwritingOrchestration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionOwnershipFlags;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.LegacyAuthorityInventory;
import com.los.core.creditintelligence.policystudio.runtime.ownership.LiveDecisionAuthority;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PinnedArtifactSelection;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterTruthProjection;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import com.los.core.service.readiness.GacatParameterReadinessProjection;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
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
 * WAVE-10 — Legacy retirement (safe paths) + deterministic pinned target-live.
 * LIVE_CUTOVER_READY = NO — Frozen remains primary.
 */
class Wave10LegacyRetirementTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 1);

    private ProductionCertificationService certs;
    private CanonicalUnderwritingOrchestration orchestration;

    @BeforeEach
    void setUp() {
        DecisionOwnershipFlags.resetForTests();
        certs = new ProductionCertificationService();
        ProductionCertificationAuthority.install(certs);
        var spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
        orchestration = new CanonicalUnderwritingOrchestration(new CanonicalPolicyRuntime(spine));
    }

    @AfterEach
    void tearDown() {
        ProductionCertificationAuthority.clear();
        ExecutionCapabilityAuthority.clear();
        DecisionOwnershipFlags.resetForTests();
    }

    @Test
    void legacyAuthorityUsageInventory_allClassified() {
        List<Map<String, Object>> inv = LegacyAuthorityInventory.inventory();
        assertThat(inv).isNotEmpty();
        for (Map<String, Object> row : inv) {
            assertThat(row.get("disposition")).isNotNull();
            assertThat(row.get("risk")).isNotNull();
        }
        assertThat(LegacyAuthorityInventory.summary().get("fullRetirementClaimed")).isEqualTo(false);
    }

    @Test
    void canonicalLiveCutoverReadiness_isNo() {
        Map<String, Object> gate = CanonicalLiveCutoverReadiness.evaluate();
        assertThat(gate.get("LIVE_CUTOVER_READY")).isEqualTo(false);
        assertThat(gate.get("liveDecisionAuthority")).isEqualTo("LEGACY_FROZEN");
        @SuppressWarnings("unchecked")
        List<String> blockers = (List<String>) gate.get("blockers");
        assertThat(blockers).isNotEmpty();
    }

    @Test
    void frozenRollback_defaultAuthorityUnchanged() {
        assertThat(DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED).isFalse();
        assertThat(DecisionOwnershipFlags.FROZEN_RETIRED).isFalse();
        assertThat(DecisionOwnershipFlags.liveDecisionAuthority()).isEqualTo(LiveDecisionAuthority.LEGACY_FROZEN);
        DecisionOwnershipFlags.setLiveDecisionAuthority(LiveDecisionAuthority.CANONICAL);
        assertThat(DecisionOwnershipFlags.isCanonicalPrimary()).isTrue();
        DecisionOwnershipFlags.resetForTests();
        assertThat(DecisionOwnershipFlags.liveDecisionAuthority()).isEqualTo(LiveDecisionAuthority.LEGACY_FROZEN);
    }

    @Test
    void noPolicyBureauExecutionPath_notOnSpine() {
        assertThat(PolicyBureauMetricService.class.isAnnotationPresent(Deprecated.class)).isTrue();
        // Spine bootstrap does not register PolicyBureauMetricService
        var er = ExecutionCapabilityAuthority.require()
                .resolveAndExecute("bureau.score", EvaluationContext.builder()
                        .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                        .fact("bureau.score", 700).build());
        assertThat(er.capability()).isTrue();
        assertThat(String.valueOf(er.producerId())).doesNotContain("PolicyBureau");
    }

    @Test
    void noDangerousRuntimeAlias() {
        List<String> runtime = ParameterExecutabilitySupport.runtimeFactAliases("bureau.recent_inquiries_90d");
        assertThat(runtime).doesNotContain("BUREAU_ENQUIRIES_3M", "compat.BUREAU_ENQUIRIES_3M");
        assertThat(CanonicalCompatibilityRegistry.dangerousAliases("bureau.recent_inquiries_90d"))
                .contains("BUREAU_ENQUIRIES_3M");
        assertThat(CanonicalCompatibilityRegistry.exactCanonicalIdForPath("BUREAU_ENQUIRIES_3M")).isNull();
        int dangerousRuntime = 0;
        for (var d : PolicyStudioConvergencePresenter.registry().all()) {
            for (String a : ParameterExecutabilitySupport.runtimeFactAliases(d.id())) {
                if (CanonicalCompatibilityRegistry.dangerousAliases(d.id()).contains(a)) {
                    dangerousRuntime++;
                }
            }
        }
        assertThat(dangerousRuntime).as("DANGEROUS_ALIASES_REMAINING").isZero();
    }

    @Test
    void noLegacyReadinessAuthority_overallNotCatalogueProductionReady() {
        var def = PolicyStudioConvergencePresenter.registry().findById("bureau.score").orElseThrow();
        Map<String, Object> proj = GacatParameterReadinessProjection.project(def);
        // Even if catalogue claims production_ready, overall must not be PRODUCTION_READY from catalogue
        assertThat(proj.get("overallReadiness")).isNotEqualTo("PRODUCTION_READY");
        assertThat(proj.get("productionReady")).isEqualTo(false);
    }

    @Test
    void noGacatFlagRuntimeAuthority_scorecardMapper() {
        var binding = ScorecardCanonicalFactorMapper.resolve("BUREAU_SCORE");
        assertThat(binding.productionReady()).isFalse();
    }

    @Test
    void noBankingFixtureLivePath() {
        var er = ExecutionCapabilityAuthority.require().resolveAndExecute(
                "banking.emi_bounce_count_3m",
                EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF).build());
        // Fixture refused outside POLICY_TEST — not VALUE_AVAILABLE from fixture
        if (er.capability()) {
            assertThat(er.status()).isNotEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        }
        assertThat(er.provenance() == null || !"LIVE_FIXTURE".equals(er.provenance().get("fixtureAuthority")))
                .isTrue();
    }

    @Test
    void noCreditControlDuplicatePolicyOnCanonicalPath() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision d = orchestration.assemble(
                "cc-dup", ASOF, ctx, "pol", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null);
        assertThat(d.provenance().get("creditControlDuplicatePolicyNotApplied")).isNull(); // assemble
        // Pinned path stamps the honesty flag
        PinnedArtifactSelection pins = PinnedArtifactSelection.builder()
                .policyId("pol").policyVersion("1")
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .evaluationAsOf(ASOF).build();
        FinalUnderwritingDecision pinned = orchestration.assembleTargetLivePinned(
                "cc-dup2", ctx, pins,
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null, List.of("bureau.score"),
                CertificationScopeType.PLATFORM, null);
        assertThat(pinned.provenance().get("creditControlDuplicatePolicyNotApplied")).isEqualTo(true);
        assertThat(pinned.provenance().get("scorecardHardRulesNotAppliedOnCanonicalPath")).isEqualTo(true);
    }

    @Test
    void noScorecardDuplicateKnockoutOnCanonicalPath() {
        // Canonical path uses ScorecardBandInput only — hard-rules not evaluated inside CUO
        FinalUnderwritingDecision d = orchestration.assemble(
                "sc", ASOF,
                EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                        .fact("bureau.score", 720).build(),
                "pol", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of("note", "band only"), true),
                Map.of(), Map.of(), null);
        assertThat(d.scorecardResult().get("note")).isEqualTo("band only");
        assertThat(d.provenance().get("hardRulesDeferredToPolicy")).isEqualTo(true);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
    }

    @Test
    void pinnedArtifactSelection_requiresVersions() {
        assertThatThrownBy(() -> PinnedArtifactSelection.builder()
                .policyId("p").evaluationAsOf(ASOF).build().requireForTargetLive())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyVersion");
        assertThatThrownBy(() -> PinnedArtifactSelection.builder()
                .policyId("p").policyVersion("1")
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .build().requireForTargetLive())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationAsOf");
    }

    @Test
    void noLatestForOnPinnedTargetLiveContext() {
        PinnedArtifactSelection pins = PinnedArtifactSelection.builder()
                .policyId("pol").policyVersion("1")
                .scorecardId("sc").scorecardVersion("1")
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .calculationDefinitionVersions(Map.of())
                .evaluationAsOf(ASOF)
                .sourceSnapshotVersion("snap-1")
                .build();
        assertThat(pins.entityPins().get("forbidLatestFor")).isEqualTo(true);
        assertThat(pins.toMap().get("latestForForbidden")).isEqualTo(true);
    }

    @Test
    void noWallClockDecisionSemantics_orchestrationRequiresAsOf() {
        assertThatThrownBy(() -> orchestration.assemble(
                "x", null, EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).build(),
                "p", "1", List.of(), null, Map.of(), Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationAsOf");
    }

    @Test
    void deterministicCanonicalUnderwriting() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .entity("sourceSnapshotVersion", "snap-fixed")
                .build();
        PinnedArtifactSelection pins = PinnedArtifactSelection.builder()
                .policyId("pol-det").policyVersion("1")
                .scorecardId("sc-det").scorecardVersion("1")
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .evaluationAsOf(ASOF)
                .sourceSnapshotVersion("snap-fixed")
                .build();
        List<CanonicalPolicyRuntime.RuleSpec> rules = List.of(
                new CanonicalPolicyRuntime.RuleSpec("score",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))));
        var band = new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of("band", "A"), true);

        FinalUnderwritingDecision a = orchestration.assembleTargetLivePinned(
                "det", ctx, pins, rules, band, Map.of(), Map.of(), null,
                List.of("bureau.score"), CertificationScopeType.PLATFORM, null);
        FinalUnderwritingDecision b = orchestration.assembleTargetLivePinned(
                "det", ctx, pins, rules, band, Map.of(), Map.of(), null,
                List.of("bureau.score"), CertificationScopeType.PLATFORM, null);

        assertThat(a.finalOutcome()).isEqualTo(b.finalOutcome());
        assertThat(a.reasonCodes()).isEqualTo(b.reasonCodes());
        assertThat(a.policyResult().get("overall")).isEqualTo(b.policyResult().get("overall"));
        assertThat(a.evaluationAsOf()).isEqualTo(ASOF);
    }

    @Test
    void canonicalVsFrozenLiveGolden_authorityUnchanged() {
        // Live authority remains LEGACY_FROZEN; canonical path is available but not primary
        assertThat(DecisionOwnershipFlags.liveDecisionAuthority()).isEqualTo(LiveDecisionAuthority.LEGACY_FROZEN);
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision canonical = orchestration.assemble(
                "gold", ASOF, ctx, "pol", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null);
        assertThat(canonical.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
        assertThat(canonical.provenance().get("liveDecisionAuthorityChanged")).isEqualTo(false);
    }

    @Test
    void vikasamPostRetirementRegression() {
        Map<String, Object> overdue = CanonicalParameterTruthProjection.project("bureau.thin_file_indicator");
        assertThat(SurfaceCanonicalTruthFacade.capability(overdue)).isFalse();
        assertThat(CanonicalCompatibilityRegistry.exactCanonicalIdForPath("BUREAU_ENQUIRIES_3M")).isNull();
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snap = harness.captureCapabilitySnapshot();
        assertThat((Integer) snap.get("policyTestCapableCount")).isGreaterThanOrEqualTo(67);
    }

    @Test
    void wave9CrossSurfaceParityStillZero() {
        int cap = 0, st = 0, cert = 0;
        List<String> surfaces = List.of(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS,
                SurfaceCanonicalTruthFacade.POLICY_STUDIO,
                SurfaceCanonicalTruthFacade.SCORECARD_PICKER,
                SurfaceCanonicalTruthFacade.UNDERWRITING);
        for (var d : PolicyStudioConvergencePresenter.registry().all()) {
            Map<String, Object> base = CanonicalParameterTruthProjection.project(d.id());
            for (String s : surfaces) {
                Map<String, Object> surf = SurfaceCanonicalTruthFacade.forSurface(s, d.id());
                if (!java.util.Objects.equals(
                        SurfaceCanonicalTruthFacade.capability(base), surf.get("executionCapability"))) {
                    cap++;
                }
                if (!java.util.Objects.equals(
                        SurfaceCanonicalTruthFacade.status(base), surf.get("executionStatus"))) {
                    st++;
                }
                if (!java.util.Objects.equals(
                        SurfaceCanonicalTruthFacade.certStatus(base), surf.get("certificationStatus"))) {
                    cert++;
                }
            }
        }
        assertThat(cap).isZero();
        assertThat(st).isZero();
        assertThat(cert).isZero();
    }

    @Test
    void certificationGateStillLiveBlockedWhenEnabled() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        PinnedArtifactSelection pins = PinnedArtifactSelection.builder()
                .policyId("pol-x").policyVersion("1")
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .evaluationAsOf(ASOF).build();
        FinalUnderwritingDecision blocked = orchestration.assembleTargetLivePinned(
                "blk", EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF).build(),
                pins, List.of(),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null, List.of(), CertificationScopeType.PLATFORM, null);
        assertThat(blocked.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
        assertThat(blocked.finalOutcome()).isNotEqualTo(FinalUnderwritingDecision.FinalOutcome.REJECT);
    }

    @Test
    void capabilityBaselineUnchanged() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snapshot = harness.captureCapabilitySnapshot();
        assertThat((Integer) snapshot.get("policyTestCapableCount")).isGreaterThanOrEqualTo(67);
        assertThat((Integer) snapshot.get("w6CapableCount")).isGreaterThanOrEqualTo(57);
        assertThat((Integer) snapshot.get("underwritingCapableCount")).isGreaterThanOrEqualTo(67);
    }

    @Test
    void writeWave10Artifacts() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("wave", 10);
        report.put("cutover", CanonicalLiveCutoverReadiness.evaluate());
        report.put("inventory", LegacyAuthorityInventory.summary());
        Path dir = Path.of("src/test/resources/architecture-regression");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("WAVE10_LEGACY_INVENTORY.json"),
                Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(LegacyAuthorityInventory.summary()));
        Files.writeString(dir.resolve("WAVE10_CUTOVER_READINESS.json"),
                Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(CanonicalLiveCutoverReadiness.evaluate()));
        assertThat(Files.exists(dir.resolve("WAVE10_LEGACY_INVENTORY.json"))).isTrue();
    }
}
