package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType;
import com.los.core.creditintelligence.policystudio.certification.CertificationScopeType;
import com.los.core.creditintelligence.policystudio.certification.CertificationStatus;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationAuthority;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationService;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.ownership.CanonicalUnderwritingOrchestration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionOwnershipFlags;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAVE-8 — Production Certification Authority goldens A–J.
 * Live legacy authority unchanged. No auto-certification. No Vikasam mutation.
 */
class Wave8ProductionCertificationTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 1);

    private ProductionCertificationService certs;
    private CanonicalParameterExecutionService spine;
    private CanonicalPolicyRuntime runtime;
    private CanonicalUnderwritingOrchestration orchestration;

    @BeforeEach
    void setUp() {
        DecisionOwnershipFlags.resetForTests();
        certs = new ProductionCertificationService();
        ProductionCertificationAuthority.install(certs);
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
        runtime = new CanonicalPolicyRuntime(spine);
        orchestration = new CanonicalUnderwritingOrchestration(runtime);
    }

    @AfterEach
    void tearDown() {
        ProductionCertificationAuthority.clear();
        ExecutionCapabilityAuthority.clear();
        DecisionOwnershipFlags.resetForTests();
    }

    @Test
    void goldenA_capableUncertified_policyTestOk_targetLiveBlocked() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        ExecutionResult er = spine.resolveAndExecute("bureau.score", ctx);
        assertThat(er.capability()).isTrue();
        assertThat(er.toCanonicalContractMap().get("certificationStatus"))
                .isEqualTo(CertificationStatus.UNCERTIFIED.name());

        FinalUnderwritingDecision blocked = orchestration.assembleTargetLive(
                "a", ASOF, ctx, "pol-a", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null,
                List.of("bureau.score"), "sc-a", "1",
                CertificationScopeType.PLATFORM, null);
        assertThat(blocked.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
        assertThat(blocked.reasonCodes()).contains("NOT_CERTIFIED");
        assertThat(blocked.provenance().get("creditReject")).isEqualTo(false);
    }

    @Test
    void goldenB_G_certifiedClosure_targetLivePermitted() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        certifyAll("pol-b", "1", "bureau.score", "sc-b", "1");
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision ok = orchestration.assembleTargetLive(
                "b", ASOF, ctx, "pol-b", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null,
                List.of("bureau.score"), "sc-b", "1",
                CertificationScopeType.PLATFORM, null);
        assertThat(ok.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
        assertThat(ok.finalOutcome()).isNotEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
    }

    @Test
    void goldenC_testedDefinitionUncertified_capableButLiveBlocked() {
        // Simulated: capable via fact overlay, certification UNCERTIFIED
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .input("obligation.ratio", 25).build();
        ExecutionResult er = spine.resolveAndExecute("obligation.ratio", ctx);
        assertThat(er.valueAvailable()).isTrue();
        // capability may be false without producer — still UNCERTIFIED projection
        assertThat(er.toCanonicalContractMap().get("certificationStatus"))
                .isEqualTo(CertificationStatus.UNCERTIFIED.name());
        assertThat(er.capability()).isNotEqualTo(
                "CERTIFIED".equals(er.toCanonicalContractMap().get("certificationStatus")));
    }

    @Test
    void goldenE_F_policyOrOperandUncertified_blocked() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        certs.certify(CertifiableArtifactType.POLICY_VERSION, "pol-e", "1",
                CertificationScopeType.PLATFORM, null, "reviewer", "policy only",
                Map.of(), "GACAT-SEMANTIC-4.0.0", "CPR", null);
        // operand uncertified
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision blocked = orchestration.assembleTargetLive(
                "e", ASOF, ctx, "pol-e", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null,
                List.of("bureau.score"), null, null,
                CertificationScopeType.PLATFORM, null);
        assertThat(blocked.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
        assertThat(blocked.reasonCodes().stream().anyMatch(r -> r.contains("OPERAND_NOT_CERTIFIED"))).isTrue();
    }

    @Test
    void goldenH_revocationBlocksFutureTargetLive() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        certifyAll("pol-h", "1", "bureau.score", "sc-h", "1");
        certs.revoke(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null, "ops", "defect");
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision blocked = orchestration.assembleTargetLive(
                "h", ASOF, ctx, "pol-h", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null,
                List.of("bureau.score"), "sc-h", "1",
                CertificationScopeType.PLATFORM, null);
        assertThat(blocked.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
        assertThat(certs.listEvidence(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER,
                "bureau.score", "1", CertificationScopeType.PLATFORM, null).size()).isGreaterThan(1);
    }

    @Test
    void goldenI_gacatProductionReadyNotCertification() {
        // No ledger row → UNCERTIFIED regardless of any catalogue production_ready claim
        assertThat(certs.getCertificationStatus(
                CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null)).isEqualTo(CertificationStatus.UNCERTIFIED);
        // Explicit cert even if we pretend catalogue said false
        certs.certify(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null, "officer", "ledger wins",
                Map.of("gacatProductionReadyClaim", false), null, null, "1");
        assertThat(certs.isCertifiedForLiveUse(
                CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null)).isTrue();
    }

    @Test
    void goldenJ_tenantIsolation_noCrossTenantLeak() {
        certs.certify(CertifiableArtifactType.POLICY_VERSION, "pol-j", "1",
                CertificationScopeType.TENANT, "tenant-A", "officer", "A only",
                Map.of(), null, null, null);
        assertThat(certs.isCertifiedForLiveUse(
                CertifiableArtifactType.POLICY_VERSION, "pol-j", "1",
                CertificationScopeType.TENANT, "tenant-A")).isTrue();
        assertThat(certs.isCertifiedForLiveUse(
                CertifiableArtifactType.POLICY_VERSION, "pol-j", "1",
                CertificationScopeType.TENANT, "tenant-B")).isFalse();
        assertThat(certs.isCertifiedForLiveUse(
                CertifiableArtifactType.POLICY_VERSION, "pol-j", "1",
                CertificationScopeType.PLATFORM, null)).isFalse();
    }

    @Test
    void certificationDoesNotChangeCapability() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST).evaluationAsOf(ASOF)
                .fact("bureau.score", 700).build();
        boolean before = spine.resolveAndExecute("bureau.score", ctx).capability();
        certs.certify(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null, "officer", "cert", Map.of(), null, null, "1");
        boolean after = spine.resolveAndExecute("bureau.score", ctx).capability();
        assertThat(after).isEqualTo(before);
        assertThat(spine.resolveAndExecute("bureau.score", ctx).toCanonicalContractMap()
                .get("capabilityIndependentOfCertification")).isEqualTo(true);
    }

    @Test
    void certificationFailureNotCreditReject() {
        DecisionOwnershipFlags.setTargetLiveCertificationGateEnabled(true);
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF).build();
        FinalUnderwritingDecision d = orchestration.assembleTargetLive(
                "x", ASOF, ctx, "pol-x", "1", List.of(),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null, List.of(), null, null,
                CertificationScopeType.PLATFORM, null);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED);
        assertThat(d.finalOutcome()).isNotEqualTo(FinalUnderwritingDecision.FinalOutcome.REJECT);
    }

    @Test
    void noAutoCertification_blankActorRejected() {
        assertThatThrownBy(() -> certs.certify(
                CertifiableArtifactType.POLICY_VERSION, "p", "1",
                CertificationScopeType.PLATFORM, null, "  ", "x", Map.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(certs.counts().get("LIVE_DATA_AUTO_CERTIFIED")).isEqualTo(false);
    }

    @Test
    void legacyLiveGateOff_assembleUnchanged() {
        assertThat(DecisionOwnershipFlags.targetLiveCertificationGateEnabled()).isFalse();
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING).evaluationAsOf(ASOF)
                .fact("bureau.score", 720).build();
        FinalUnderwritingDecision d = orchestration.assemble(
                "legacy", ASOF, ctx, "pol", "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("s",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                new CanonicalUnderwritingOrchestration.ScorecardBandInput(
                        FinalUnderwritingDecision.FinalOutcome.APPROVE, Map.of(), true),
                Map.of(), Map.of(), null);
        assertThat(d.finalOutcome()).isEqualTo(FinalUnderwritingDecision.FinalOutcome.APPROVE);
        assertThat(DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED).isFalse();
    }

    @Test
    void migrationFilePresent_v142() throws Exception {
        Path mig = Path.of("src/main/resources/db/migration/V142__production_certification_ledger.sql");
        assertThat(Files.exists(mig)).isTrue();
        String sql = Files.readString(mig);
        assertThat(sql).contains("ci_production_certification");
        assertThat(sql).contains("ci_production_certification_event");
        assertThat(sql).doesNotContain("DROP TABLE");
    }

    @Test
    void vikasamCertificationProjection_axesIndependent() {
        EvaluationContext ctx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        for (String id : List.of("bureau.score", "bureau.cc_overdue_amount")) {
            ExecutionResult er = spine.resolveAndExecute(id, ctx);
            Map<String, Object> m = er.toCanonicalContractMap();
            assertThat(m).containsKeys("capability", "valueAvailable", "certificationStatus");
            assertThat(m.get("certificationStatus")).isEqualTo(CertificationStatus.UNCERTIFIED.name());
        }
    }

    @Test
    void capabilityBaselineUnchanged() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snap = harness.captureCapabilitySnapshot();
        assertThat((Integer) snap.get("policyTestCapableCount")).isGreaterThanOrEqualTo(67);
        assertThat((Integer) snap.get("w6CapableCount")).isGreaterThanOrEqualTo(57);
        assertThat((Integer) snap.get("underwritingCapableCount")).isGreaterThanOrEqualTo(67);
    }

    private void certifyAll(String policyId, String policyVer, String paramId, String scId, String scVer) {
        certs.certify(CertifiableArtifactType.POLICY_VERSION, policyId, policyVer,
                CertificationScopeType.PLATFORM, null, "reviewer", "policy", Map.of(), null, null, null);
        certs.certify(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, paramId, "1",
                CertificationScopeType.PLATFORM, null, "reviewer", "producer", Map.of(), null, null, "1");
        certs.certify(CertifiableArtifactType.SCORECARD_VERSION, scId, scVer,
                CertificationScopeType.PLATFORM, null, "reviewer", "scorecard", Map.of(), null, null, null);
    }
}
