package com.los.core.creditintelligence.cutover;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiLimitedPilotCertification;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.domain.CutoverDrillResult;
import com.los.core.creditintelligence.cutover.domain.DefaultDefinitionStatus;
import com.los.core.creditintelligence.cutover.domain.LimitedPilotCertificationStatus;
import com.los.core.creditintelligence.cutover.domain.QuarantineResult;
import com.los.core.creditintelligence.cutover.domain.ReviewDisposition;
import com.los.core.creditintelligence.cutover.fixture.G0CandidateCohortFactory;
import com.los.core.creditintelligence.cutover.pilot.CutoverDrillService;
import com.los.core.creditintelligence.cutover.pilot.DefaultLeakageAssertor;
import com.los.core.creditintelligence.cutover.pilot.DualRunEnablementService;
import com.los.core.creditintelligence.cutover.pilot.LimitedPilotCertificationService;
import com.los.core.creditintelligence.cutover.pilot.PilotAmbiguityGate;
import com.los.core.creditintelligence.cutover.pilot.PilotCandidateRanker;
import com.los.core.creditintelligence.cutover.pilot.PilotCriticalBindingCertificationService;
import com.los.core.creditintelligence.cutover.pilot.PilotDataDiscoveryService;
import com.los.core.creditintelligence.cutover.pilot.PilotDataGapService;
import com.los.core.creditintelligence.cutover.pilot.PilotDualRunOrchestrator;
import com.los.core.creditintelligence.cutover.pilot.PilotDualRunStatsService;
import com.los.core.creditintelligence.cutover.pilot.PilotMismatchThresholds;
import com.los.core.creditintelligence.cutover.pilot.PilotObservabilityDashboard;
import com.los.core.creditintelligence.cutover.pilot.PilotReplayCertifier;
import com.los.core.creditintelligence.cutover.pilot.ConditionsReadinessResolver;
import com.los.core.creditintelligence.cutover.service.BindingCertificationService;
import com.los.core.creditintelligence.cutover.service.CutoverCohortService;
import com.los.core.creditintelligence.cutover.service.CutoverComparisonClassifier;
import com.los.core.creditintelligence.cutover.service.CutoverControlService;
import com.los.core.creditintelligence.cutover.service.CutoverDualRunService;
import com.los.core.creditintelligence.cutover.service.CutoverObservability;
import com.los.core.creditintelligence.cutover.service.CutoverValidationDataClassifier;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultCatalogService;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultQuarantineService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import com.los.core.creditintelligence.validation.service.LegacyDefaultInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0.1 Limited Pilot Certification — §40 validation suite.
 */
class CutoverG01PilotCertificationTest {

    private CutoverStore store;
    private CreditIntelligenceProperties props;
    private LegacyDefaultCatalogService catalog;
    private LegacyDefaultQuarantineService quarantine;
    private BindingCertificationService bindings;
    private CutoverCohortService cohorts;
    private CutoverControlService control;
    private CutoverDualRunService dualRun;
    private CutoverObservability observability;
    private PilotDataDiscoveryService discovery;
    private PilotCandidateRanker ranker;
    private PilotCriticalBindingCertificationService criticalBindings;
    private PilotAmbiguityGate ambiguityGate;
    private PilotDualRunOrchestrator dualRunOrchestrator;
    private PilotDualRunStatsService dualRunStats;
    private PilotMismatchThresholds mismatchThresholds;
    private PilotReplayCertifier replayCertifier;
    private DefaultLeakageAssertor leakageAssertor;
    private CutoverDrillService drillService;
    private DualRunEnablementService enablement;
    private ConditionsReadinessResolver conditionsResolver;
    private LimitedPilotCertificationService certification;
    private PilotDataGapService dataGaps;

    @BeforeEach
    void setUp() {
        store = new CutoverStore();
        props = new CreditIntelligenceProperties();
        props.getCutover().setEnabled(true);
        props.getCutover().setDualRunEnabled(true);
        props.getCutover().setQuarantineEnabled(true);
        props.getCutover().setTenantIds(List.of(G0CandidateCohortFactory.TENANT_ID.toString()));
        props.getCutover().setProductCodes(List.of("DIGILEAP", "SCF_STARTER"));
        props.getCutover().setAllowCanonicalAuthority(false);
        props.getCutover().setMinRealOrStoredCases(20);
        props.getCutover().setEvidenceWeightingEnabled(true);

        observability = new CutoverObservability();
        catalog = new LegacyDefaultCatalogService(store, new LegacyDefaultInventory());
        catalog.seedFromInventory();
        G0CandidateCohortFactory.seed(store);

        quarantine = new LegacyDefaultQuarantineService(store, catalog, props, observability);
        bindings = new BindingCertificationService(store);
        cohorts = new CutoverCohortService(store);
        control = new CutoverControlService(store, cohorts, props, observability);
        dualRun = new CutoverDualRunService(store, new CutoverComparisonClassifier(), props);
        discovery = new PilotDataDiscoveryService(new CutoverValidationDataClassifier());
        ranker = new PilotCandidateRanker(store);
        criticalBindings = new PilotCriticalBindingCertificationService(store, bindings);
        ambiguityGate = new PilotAmbiguityGate();
        dualRunOrchestrator = new PilotDualRunOrchestrator(store, dualRun);
        dualRunStats = new PilotDualRunStatsService(store);
        mismatchThresholds = new PilotMismatchThresholds();
        replayCertifier = new PilotReplayCertifier();
        leakageAssertor = new DefaultLeakageAssertor(store);
        drillService = new CutoverDrillService(store, control);
        enablement = new DualRunEnablementService(store, control);
        conditionsResolver = new ConditionsReadinessResolver(store);
        dataGaps = new PilotDataGapService(store);
        certification = new LimitedPilotCertificationService(
                store, props, discovery, criticalBindings, ambiguityGate, dualRunStats,
                mismatchThresholds, replayCertifier, leakageAssertor, drillService,
                enablement, conditionsResolver, catalog);
    }

    @Test
    void fixtureOnly_cannotGetLimitedPilotReady_whenMin20() {
        preparePassingCodeGates();
        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(null));

        assertThat(cert.getRealStoredCaseCount()).isEqualTo(0);
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.NOT_READY.name());
        assertThat(cert.getGateResults().get("limitedPilotReady")).isEqualTo(false);
        assertThat(cert.getBlockers()).anyMatch(b -> b.contains("realStoredCaseCount"));
    }

    @Test
    void readyWithExceptions_whenSampleSizeExceptionApproved() {
        preparePassingCodeGates();
        enablement.grantSampleSizeException(
                G0CandidateCohortFactory.COHORT_ID,
                "fixture-only sample below 20",
                "risk-approver",
                "shadow dual-run only; no authority cutover",
                Instant.now().plusSeconds(86400));

        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(null));

        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.READY_WITH_EXCEPTIONS.name());
        assertThat(cert.getGateResults().get("limitedPilotReady")).isEqualTo(false);
        assertThat(cert.getRealStoredCaseCount()).isEqualTo(0);
    }

    @Test
    void fullPass_limitedPilotReady_onlyWhenRealStoredMeetsMin() {
        preparePassingCodeGates();
        props.getCutover().setMinRealOrStoredCases(20);
        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(20));

        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.LIMITED_PILOT_READY.name());
        assertThat(cert.getRealStoredCaseCount()).isEqualTo(20);
        assertThat(cert.getGateResults().get("limitedPilotReady")).isEqualTo(true);
        assertThat(cert.getGateResults().get("aiAffectsScore")).isEqualTo(false);
        assertThat(cert.getGateResults().get("g1MayBegin")).isEqualTo(false);
    }

    @Test
    void bindingMissing_blocks() {
        // do not certify bindings
        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester",
                new LimitedPilotCertificationService.CertificationInput(
                        true, true, true, null, null, true, true, true, true, true, true,
                        true, List.of(), Map.of(), replayOk(), List.of(), Map.of(),
                        20, true, true));
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.NOT_READY.name());
        assertThat(cert.getBlockers()).isNotEmpty();
        assertThat(Boolean.TRUE.equals(cert.getGateResults().get("criticalBindingsCertified"))).isFalse();
    }

    @Test
    void unsafeDefaultLeak_fails() {
        preparePassingCodeGates();
        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester",
                new LimitedPilotCertificationService.CertificationInput(
                        true, true, true, null, null, true, true, true, true, true, true,
                        true, List.of(), Map.of(), replayOk(),
                        List.of("ANNUAL_GST_TURNOVER"),
                        Map.of("ANNUAL_GST_TURNOVER", "UNSAFE_SILENT_DEFAULT"),
                        20, true, true));
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.NOT_READY.name());
        assertThat(cert.getBlockers()).anyMatch(b -> b.contains(DefaultLeakageAssertor.FAILURE_CODE));
    }

    @Test
    void replayFailure_blocks() {
        preparePassingCodeGates();
        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester",
                new LimitedPilotCertificationService.CertificationInput(
                        true, true, true, null, null, true, true, true, true, true, true,
                        true, List.of(), Map.of(),
                        List.of(new PilotReplayCertifier.ReplayCase(
                                "CASE_A", "h1", "h2", "p1", "p1", "d1", "d1")),
                        List.of(), Map.of(), 20, true, true));
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.NOT_READY.name());
        assertThat(cert.getBlockers()).anyMatch(b -> b.toLowerCase().contains("replay"));
    }

    @Test
    void unresolvedPermissiveMismatch_blocks() {
        preparePassingCodeGates();
        CiCutoverComparison cmp = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID, UUID.randomUUID(), null,
                Map.of("outcome", "FAIL", "amount", 100),
                Map.of("outcome", "PASS", "amount", 100),
                false, Map.of());
        // Force class if classifier didn't mark more-permissive
        cmp.setComparisonClass(ComparisonClass.CANONICAL_MORE_PERMISSIVE.name());
        cmp.setReviewStatus("PENDING");
        store.saveComparison(cmp);

        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(20));
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.NOT_READY.name());
        assertThat(cert.getBlockers()).anyMatch(b -> b.contains("more-permissive"));
    }

    @Test
    void saferProductRanking_digileapHighestAmongFixtures() {
        List<PilotCandidateRanker.CandidateScore> ranked = ranker.rank();
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).productCode()).isEqualTo("DIGILEAP");
        assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(ranked.size() - 1).score());
        assertThat(ranked.get(0).rationale().get("note").toString()).contains("not auto-selected");
    }

    @Test
    void ranking_penalizesInsufficientSourceCoverage() {
        List<PilotCandidateRanker.CandidateScore> ranked = ranker.rank(Map.of(
                "DIGILEAP", Map.of("sourceCompleteness", 0.1),
                "SCF_STARTER", Map.of("sourceCompleteness", 0.95, "bindingCoverage", 0.9,
                        "defaultDependency", 0.2, "sampleSize", 0.5, "complexity", 0.3,
                        "mismatchRate", 0.1, "diRate", 0.1, "rollbackSimplicity", 0.9)
        ));
        assertThat(ranked.get(0).productCode()).isEqualTo("SCF_STARTER");
    }

    @Test
    void dualRun_isolation_canonicalFailureDoesNotBlockLegacy() {
        var result = dualRunOrchestrator.runCases(
                G0CandidateCohortFactory.COHORT_ID,
                List.of(new PilotDualRunOrchestrator.DualRunCase(
                        UUID.randomUUID(), "FAULT_CASE", "REPRESENTATIVE_FIXTURE",
                        Map.of("outcome", "PASS", "amount", 100),
                        Map.of("outcome", "PASS", "amount", 100),
                        false, true)));
        assertThat(result.canonicalFailures()).isEqualTo(1);
        assertThat(result.persisted()).isEqualTo(1);
        assertThat(result.notes()).anyMatch(n -> n.contains("legacy unaffected"));
        assertThat(store.listOperationalEvents(G0CandidateCohortFactory.COHORT_ID))
                .anyMatch(e -> "CANONICAL_EVALUATION_FAILED".equals(e.getEventType()));
        // legacy control remains LEGACY
        assertThat(control.current(G0CandidateCohortFactory.COHORT_ID).getAuthorityMode())
                .isEqualTo(AuthorityMode.LEGACY.name());
    }

    @Test
    void quarantine_zeroLeakage() {
        Map<String, Object> metrics = Map.of("gst.turnover.trailing_12m", 1);
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID, "DIGILEAP", "ANNUAL_GST_TURNOVER", metrics);
        assertThat(r.isAllowLegacy()).isFalse();
        var leak = leakageAssertor.assertNoUnsafeLeak(
                G0CandidateCohortFactory.COHORT_ID, List.of(), Map.of());
        assertThat(leak.leaked()).isFalse();
    }

    @Test
    void rollback_and_killSwitch_drills() {
        var rollback = drillService.rollbackDrill(G0CandidateCohortFactory.COHORT_ID, "ops");
        assertThat(rollback.getResult()).isEqualTo(CutoverDrillResult.PASS.name());
        assertThat(control.current(G0CandidateCohortFactory.COHORT_ID).getAuthorityMode())
                .isEqualTo(AuthorityMode.LEGACY.name());

        var kill = drillService.killSwitchDrill(G0CandidateCohortFactory.COHORT_ID, "ops");
        assertThat(kill.getResult()).isEqualTo(CutoverDrillResult.PASS.name());
        assertThat(kill.getEvidence().get("noRedeployment")).isEqualTo(true);
    }

    @Test
    void tenantIsolation_and_canonicalRejected() {
        assertThat(props.getCutover().isAllowCanonicalAuthority()).isFalse();
        assertThatThrownBy(() -> control.setMode(
                G0CandidateCohortFactory.COHORT_ID, AuthorityMode.CANONICAL, "x", "no"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CANONICAL");

        UUID otherTenant = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThat(cohorts.listByTenant(otherTenant)).isEmpty();
        assertThat(cohorts.listByTenant(G0CandidateCohortFactory.TENANT_ID)).isNotEmpty();
    }

    @Test
    void aiOffline_irrelevant_to_certification() {
        preparePassingCodeGates();
        CiLimitedPilotCertification offline = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester",
                new LimitedPilotCertificationService.CertificationInput(
                        true, true, true, null, null, true, true, true, true, true, true,
                        true, List.of(), Map.of(), replayOk(), List.of(), Map.of(), 20, true, true));
        CiLimitedPilotCertification online = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester",
                new LimitedPilotCertificationService.CertificationInput(
                        true, true, true, null, null, true, true, true, true, true, true,
                        false, List.of(), Map.of(), replayOk(), List.of(), Map.of(), 20, true, true));
        assertThat(offline.getStatus()).isEqualTo(online.getStatus());
        assertThat(offline.getGateResults().get("aiAffectsScore")).isEqualTo(false);
        assertThat(online.getGateResults().get("aiAffectsScore")).isEqualTo(false);
    }

    @Test
    void enableDualRun_requiresCertification() {
        assertThatThrownBy(() -> enablement.enableDualRun(
                G0CandidateCohortFactory.COHORT_ID, "ops", "go"))
                .isInstanceOf(ResponseStatusException.class);

        preparePassingCodeGates();
        enablement.grantSampleSizeException(
                G0CandidateCohortFactory.COHORT_ID, "risk", "approver", "mitigation",
                Instant.now().plusSeconds(3600));
        certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(null));
        Map<String, Object> enabled = enablement.enableDualRun(
                G0CandidateCohortFactory.COHORT_ID, "ops", "pilot dual-run");
        assertThat(enabled.get("productionAuthority")).isEqualTo("LEGACY");
        assertThat(control.current(G0CandidateCohortFactory.COHORT_ID).getAuthorityMode())
                .isEqualTo(AuthorityMode.DUAL_RUN.name());
    }

    @Test
    void discovery_honestRealStoredZero() {
        Map<String, Object> report = discovery.discover();
        assertThat(report.get("realStoredCaseCount")).isEqualTo(0);
        assertThat(report.get("piiExcluded")).isEqualTo(true);
        assertThat(report.get("honestyNote").toString()).contains("do NOT count");
    }

    @Test
    void dualRun_c6Fixtures_labeledRepresentative() {
        var result = dualRunOrchestrator.runCases(
                G0CandidateCohortFactory.COHORT_ID,
                PilotDualRunOrchestrator.c6RepresentativeFixtures());
        assertThat(result.persisted()).isEqualTo(5);
        Map<String, Object> stats = dualRunStats.statistics(G0CandidateCohortFactory.COHORT_ID);
        assertThat(stats.get("fixtureApplications")).isEqualTo(5);
        assertThat(stats.get("realStoredApplications")).isEqualTo(0);
    }

    @Test
    void explainedPermissive_doesNotBlock() {
        preparePassingCodeGates();
        CiCutoverComparison cmp = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID, UUID.randomUUID(), null,
                Map.of("outcome", "FAIL", "amount", 100),
                Map.of("outcome", "PASS", "amount", 100),
                false, Map.of());
        cmp.setComparisonClass(ComparisonClass.CANONICAL_MORE_PERMISSIVE.name());
        cmp.setReviewDisposition(ReviewDisposition.EXPECTED_CANONICAL.name());
        cmp.setReviewStatus("REVIEWED");
        store.saveComparison(cmp);

        CiLimitedPilotCertification cert = certification.runCertification(
                G0CandidateCohortFactory.COHORT_ID, "tester", passingInput(20));
        assertThat(cert.getStatus()).isEqualTo(LimitedPilotCertificationStatus.LIMITED_PILOT_READY.name());
    }

    @Test
    void conditions_resolvedExplicitlyLegacy() {
        var r = conditionsResolver.resolve(G0CandidateCohortFactory.COHORT_ID, false);
        assertThat(r.ambiguous()).isFalse();
        assertThat(r.authoritySource()).isEqualTo("LEGACY");
        assertThat(r.note()).contains("G0.1");
    }

    @Test
    void observability_excludesPiiAndAi() {
        PilotObservabilityDashboard dash = new PilotObservabilityDashboard(store, dualRunStats);
        Map<String, Object> m = dash.metrics(G0CandidateCohortFactory.COHORT_ID);
        assertThat(m.get("piiExcluded")).isEqualTo(true);
        assertThat(m.get("aiExcludedFromCertification")).isEqualTo(true);
    }

    private void preparePassingCodeGates() {
        Map<String, Object> rich = new LinkedHashMap<>();
        rich.put("gst.turnover.trailing_12m", 1);
        rich.put("bank.abb.average", 1);
        rich.put("bank.turnover.trailing_12m", 1);
        rich.put("bureau.live_unsecured_count", 1);
        rich.put("bureau.emi.monthly", 1);
        rich.put("itr.income.total", 1);
        rich.put("income.monthly", 1);
        rich.put("obligation.ratio", 1);
        rich.put("dti.ratio", 1);
        rich.put("itr.pat", 1);
        rich.put("financials.tol", 1);
        rich.put("financials.tnw", 1);
        rich.put("bureau.enquiries_3m", 1);
        rich.put("financials.interest_coverage", 1);
        rich.put("financials.dte", 1);
        for (var d : catalog.listAll()) {
            if ("UNSAFE_SILENT_DEFAULT".equals(d.getClassification())
                    || "DEMO_ONLY".equals(d.getClassification())) {
                quarantine.intercept(
                        G0CandidateCohortFactory.TENANT_ID, "DIGILEAP", d.getLegacyKey(), rich);
                d.setStatus(DefaultDefinitionStatus.QUARANTINED.name());
                store.saveDefault(d);
            }
        }
        for (CiPolicyBinding b : bindings.ensureBindings(G0CandidateCohortFactory.TENANT_ID)) {
            if (b.isCritical()) {
                bindings.certify(G0CandidateCohortFactory.TENANT_ID, b.getLegacyParameter(), "tester",
                        Map.of("unitsMatch", true, "periodSemanticsMatch", true, "testsPass", true,
                                "replayPass", true, "shadowReviewed", true));
            }
        }
        drillService.rollbackDrill(G0CandidateCohortFactory.COHORT_ID, "ops");
        drillService.killSwitchDrill(G0CandidateCohortFactory.COHORT_ID, "ops");
    }

    private LimitedPilotCertificationService.CertificationInput passingInput(Integer realStoredOverride) {
        return new LimitedPilotCertificationService.CertificationInput(
                true, true, true, null, null, true, true, true, true, true, true,
                true, List.of(), Map.of(), replayOk(), List.of(), Map.of(),
                realStoredOverride, true, false);
    }

    private static List<PilotReplayCertifier.ReplayCase> replayOk() {
        return List.of(new PilotReplayCertifier.ReplayCase(
                "CASE_A", "h1", "h1", "p1", "p1", "d1", "d1"));
    }
}
