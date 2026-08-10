package com.los.core.creditintelligence.cutover;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.BindingCertificationStatus;
import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiCutoverReview;
import com.los.core.creditintelligence.cutover.domain.CohortStatus;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.domain.G0ReadinessOutcome;
import com.los.core.creditintelligence.cutover.domain.QuarantineResult;
import com.los.core.creditintelligence.cutover.domain.ReviewDisposition;
import com.los.core.creditintelligence.cutover.domain.ValidationDataOrigin;
import com.los.core.creditintelligence.cutover.fixture.G0CandidateCohortFactory;
import com.los.core.creditintelligence.cutover.service.BindingCertificationService;
import com.los.core.creditintelligence.cutover.service.CanonicalSourceReadinessGate;
import com.los.core.creditintelligence.cutover.service.CutoverCamCompatibilityAdapter;
import com.los.core.creditintelligence.cutover.service.CutoverCohortService;
import com.los.core.creditintelligence.cutover.service.CutoverComparisonClassifier;
import com.los.core.creditintelligence.cutover.service.CutoverControlService;
import com.los.core.creditintelligence.cutover.service.CutoverDualRunService;
import com.los.core.creditintelligence.cutover.service.CutoverObservability;
import com.los.core.creditintelligence.cutover.service.CutoverReviewService;
import com.los.core.creditintelligence.cutover.service.CutoverValidationDataClassifier;
import com.los.core.creditintelligence.cutover.service.DecisionCertificationService;
import com.los.core.creditintelligence.cutover.service.DemoFallbackQuarantine;
import com.los.core.creditintelligence.cutover.service.G0CutoverReadinessScorer;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultCatalogService;
import com.los.core.creditintelligence.cutover.service.LegacyDefaultQuarantineService;
import com.los.core.creditintelligence.cutover.service.PolicyCertificationService;
import com.los.core.creditintelligence.cutover.service.SilentDefaultImpactAnalyzer;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import com.los.core.creditintelligence.validation.service.LegacyDefaultInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0 Production Cutover Readiness — §42 validation suite.
 */
class CutoverG0Test {

    private CutoverStore store;
    private CreditIntelligenceProperties props;
    private LegacyDefaultCatalogService catalog;
    private LegacyDefaultQuarantineService quarantine;
    private BindingCertificationService bindings;
    private CutoverCohortService cohorts;
    private CutoverControlService control;
    private CutoverDualRunService dualRun;
    private CutoverReviewService reviews;
    private G0CutoverReadinessScorer scorer;
    private DemoFallbackQuarantine demoGuard;
    private CutoverValidationDataClassifier dataClassifier;
    private PolicyCertificationService policyCert;
    private DecisionCertificationService decisionCert;
    private SilentDefaultImpactAnalyzer impact;
    private CutoverObservability observability;
    private CanonicalSourceReadinessGate sourceGate;
    private CutoverCamCompatibilityAdapter camAdapter;

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

        observability = new CutoverObservability();
        catalog = new LegacyDefaultCatalogService(store, new LegacyDefaultInventory());
        catalog.seedFromInventory();
        G0CandidateCohortFactory.seed(store);

        quarantine = new LegacyDefaultQuarantineService(store, catalog, props, observability);
        bindings = new BindingCertificationService(store);
        cohorts = new CutoverCohortService(store);
        control = new CutoverControlService(store, cohorts, props, observability);
        dualRun = new CutoverDualRunService(store, new CutoverComparisonClassifier(), props);
        reviews = new CutoverReviewService(store);
        scorer = new G0CutoverReadinessScorer(store);
        demoGuard = new DemoFallbackQuarantine();
        dataClassifier = new CutoverValidationDataClassifier();
        policyCert = new PolicyCertificationService(store);
        decisionCert = new DecisionCertificationService(store);
        impact = new SilentDefaultImpactAnalyzer(store);
        sourceGate = new CanonicalSourceReadinessGate();
        camAdapter = new CutoverCamCompatibilityAdapter();
    }

    @Test
    void quarantine_canonicalAvailable() {
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID,
                "DIGILEAP",
                "ANNUAL_GST_TURNOVER",
                Map.of("gst.turnover.trailing_12m", new BigDecimal("84000000")));
        assertThat(r.isAllowLegacy()).isFalse();
        assertThat(r.getDisposition()).isEqualTo("CANONICAL_REPLACEMENT");
        assertThat(r.getCanonicalValue()).isEqualTo(new BigDecimal("84000000"));
    }

    @Test
    void quarantine_canonicalMissing() {
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID,
                "DIGILEAP",
                "ANNUAL_GST_TURNOVER",
                Map.of());
        assertThat(r.isAllowLegacy()).isFalse();
        assertThat(r.getDisposition()).isIn("DATA_INSUFFICIENT", "REFER");
    }

    @Test
    void quarantine_cohortDisabled_allowsLegacy() {
        props.getCutover().setQuarantineEnabled(false);
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID,
                "DIGILEAP",
                "ANNUAL_GST_TURNOVER",
                Map.of());
        assertThat(r.isAllowLegacy()).isTrue();
        assertThat(r.getDisposition()).isEqualTo("LEGACY_UNCHANGED");
    }

    @Test
    void quarantine_approvedPolicyDefault() {
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID,
                "DIGILEAP",
                "PRICING_FLOOR_BPS",
                Map.of());
        assertThat(r.getDisposition()).isEqualTo("APPROVED_POLICY_DEFAULT");
        assertThat(r.isAllowLegacy()).isTrue();
    }

    @Test
    void quarantine_unsafeDefault_blockedWithoutCanonical() {
        QuarantineResult r = quarantine.intercept(
                G0CandidateCohortFactory.TENANT_ID,
                "DIGILEAP",
                "LIVE_UNSECURED_LOAN_COUNT",
                Map.of());
        assertThat(r.isAllowLegacy()).isFalse();
        assertThat(r.getDisposition()).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void bindingCertification_correctUnit() {
        CiPolicyBinding b = bindings.certify(
                G0CandidateCohortFactory.TENANT_ID,
                "ANNUAL_GST_TURNOVER",
                "tester",
                Map.of(
                        "unitsMatch", true,
                        "periodSemanticsMatch", true,
                        "testsPass", true,
                        "replayPass", true,
                        "shadowReviewed", true,
                        "evidenceRefs", List.of("fixture:gst")));
        assertThat(b.getCertificationStatus()).isEqualTo(BindingCertificationStatus.CERTIFIED.name());
        assertThat(b.isReady()).isTrue();
    }

    @Test
    void bindingCertification_wrongUnit_blocked() {
        CiPolicyBinding b = bindings.certify(
                G0CandidateCohortFactory.TENANT_ID,
                "AVERAGE_BANK_BALANCE",
                "tester",
                Map.of("unitsMatch", false, "testsPass", true));
        assertThat(b.getCertificationStatus()).isEqualTo(BindingCertificationStatus.BLOCKED.name());
    }

    @Test
    void bindingCertification_missingPeriod_blocked() {
        CiPolicyBinding b = bindings.certify(
                G0CandidateCohortFactory.TENANT_ID,
                "EMI_OBLIGATION",
                "tester",
                Map.of("periodSemanticsMatch", false));
        assertThat(b.getCertificationStatus()).isEqualTo(BindingCertificationStatus.BLOCKED.name());
    }

    @Test
    void bindingCertification_missingCanonicalPath_blocked() {
        bindings.ensureBindings(G0CandidateCohortFactory.TENANT_ID);
        CiPolicyBinding raw = store.findBinding(G0CandidateCohortFactory.TENANT_ID, "TOL").orElseThrow();
        raw.setCanonicalPath(null);
        store.saveBinding(raw);
        CiPolicyBinding b = bindings.certify(
                G0CandidateCohortFactory.TENANT_ID,
                "TOL",
                "tester",
                Map.of("testsPass", true));
        assertThat(b.getCertificationStatus()).isEqualTo(BindingCertificationStatus.BLOCKED.name());
    }

    @Test
    void dualRun_exactMatch() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of("outcome", "PASS", "amount", 500000, "tenure", 12, "pricing", 0.14),
                Map.of("outcome", "PASS", "amount", 500000, "tenure", 12, "pricing", 0.14),
                false,
                Map.of("dataOrigin", "REPRESENTATIVE_FIXTURE"));
        assertThat(c.getComparisonClass()).isEqualTo(ComparisonClass.EXACT_MATCH.name());
    }

    @Test
    void dualRun_canonicalStricter() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID,
                UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 500000),
                Map.of("outcome", "FAIL", "amount", 500000),
                false, Map.of());
        assertThat(c.getComparisonClass()).isEqualTo(ComparisonClass.CANONICAL_STRICTER.name());
    }

    @Test
    void dualRun_canonicalDi() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID,
                UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 500000),
                Map.of("outcome", "DATA_INSUFFICIENT"),
                false, Map.of());
        assertThat(c.getComparisonClass()).isEqualTo(ComparisonClass.CANONICAL_DATA_INSUFFICIENT.name());
    }

    @Test
    void dualRun_amountDifference() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID,
                UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 1_000_000),
                Map.of("outcome", "PASS", "amount", 700_000),
                false, Map.of());
        assertThat(c.getComparisonClass()).isEqualTo(ComparisonClass.MATERIAL_AMOUNT_DIFFERENCE.name());
    }

    @Test
    void dualRun_defaultDependent() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID,
                UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 500000),
                Map.of("outcome", "REFER"),
                true,
                Map.of("legacyDefaultsUsed", List.of("ANNUAL_GST_TURNOVER"),
                        "legacyDefaultKey", "ANNUAL_GST_TURNOVER",
                        "dataOrigin", "REPRESENTATIVE_FIXTURE"));
        assertThat(c.getComparisonClass()).isEqualTo(ComparisonClass.LEGACY_DEFAULT_DEPENDENT.name());
    }

    @Test
    void cohort_tenantProductIsolation() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThat(cohorts.listByTenant(other)).isEmpty();
        assertThat(cohorts.listByTenant(G0CandidateCohortFactory.TENANT_ID)).hasSize(1);
        assertThat(cohorts.dimensionMatrix(G0CandidateCohortFactory.COHORT_ID))
                .containsKeys("POLICY", "SCORECARD", "LIMIT", "AUTHORITY");
        assertThat(((Map<?, ?>) cohorts.dimensionMatrix(G0CandidateCohortFactory.COHORT_ID).get("AUTHORITY"))
                .get("authoritySource")).isEqualTo("LEGACY");
    }

    @Test
    void cohort_refuseActive() {
        assertThatThrownBy(() -> cohorts.updateStatus(
                G0CandidateCohortFactory.COHORT_ID, CohortStatus.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void rollback_dualRunToLegacy() {
        control.setMode(G0CandidateCohortFactory.COHORT_ID, AuthorityMode.DUAL_RUN, "ops", "start dual");
        assertThat(control.current(G0CandidateCohortFactory.COHORT_ID).getAuthorityMode())
                .isEqualTo(AuthorityMode.DUAL_RUN.name());
        control.setMode(G0CandidateCohortFactory.COHORT_ID, AuthorityMode.LEGACY, "ops", "rollback");
        assertThat(control.current(G0CandidateCohortFactory.COHORT_ID).getAuthorityMode())
                .isEqualTo(AuthorityMode.LEGACY.name());
        assertThat(cohorts.get(G0CandidateCohortFactory.COHORT_ID).getStatus())
                .isEqualTo(CohortStatus.ROLLED_BACK.name());
    }

    @Test
    void rejectCanonicalActivation() {
        assertThatThrownBy(() -> control.setMode(
                G0CandidateCohortFactory.COHORT_ID, AuthorityMode.CANONICAL, "ops", "nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CANONICAL");
    }

    @Test
    void humanReview_immutableAudit() {
        CiCutoverComparison c = dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID, UUID.randomUUID(), null,
                Map.of("outcome", "PASS"), Map.of("outcome", "REFER"),
                false, Map.of());
        CiCutoverReview r1 = reviews.review(c.getId(), ReviewDisposition.NEEDS_INVESTIGATION,
                "first look", "cm1");
        CiCutoverReview r2 = reviews.review(c.getId(), ReviewDisposition.EXPECTED_CANONICAL,
                "accepted", "cm1");
        List<CiCutoverReview> trail = reviews.auditTrail(c.getId());
        assertThat(trail).hasSize(2);
        assertThat(trail).extracting(CiCutoverReview::getId).containsExactlyInAnyOrder(r1.getId(), r2.getId());
        assertThat(trail).extracting(CiCutoverReview::getDisposition)
                .containsExactly(
                        ReviewDisposition.NEEDS_INVESTIGATION.name(),
                        ReviewDisposition.EXPECTED_CANONICAL.name());
        assertThat(store.findComparison(c.getId()).orElseThrow().getReviewDisposition())
                .isEqualTo(ReviewDisposition.EXPECTED_CANONICAL.name());
    }

    @Test
    void tenantSeparation_quarantine() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        QuarantineResult r = quarantine.intercept(other, "DIGILEAP", "ANNUAL_GST_TURNOVER", Map.of());
        assertThat(r.isAllowLegacy()).isTrue();
    }

    @Test
    void demoUrlGuard() {
        assertThatThrownBy(() -> demoGuard.assertNoDemoRedirect(
                "https://demo.ai-los.local/borrower/TEST001"))
                .isInstanceOf(ResponseStatusException.class);
        demoGuard.assertNoDemoRedirect("https://ai-los.example/assistive/app-1");
        assertThat(demoGuard.isDemoUrl(DemoFallbackQuarantine.DEMO_AI_LOS_URL)).isTrue();
    }

    @Test
    void readiness_notReady_whenSilentDefaultsRemain() {
        G0CutoverReadinessScorer.ScoreResult score = scorer.score(
                G0CandidateCohortFactory.TENANT_ID,
                G0CandidateCohortFactory.COHORT_ID,
                new G0CutoverReadinessScorer.GateInput(
                        false, false, false, false, false, false,
                        true, true, true, true, true, true,
                        true, (int) catalog.countUnsafeSilent(), true));
        assertThat(score.overall()).isEqualTo(G0ReadinessOutcome.NOT_READY);
        assertThat(score.limitedPilotReady()).isFalse();
    }

    @Test
    void readiness_limitedPilotReady_whenGatesMetWithQuarantine() {
        Map<String, Object> richMetrics = new LinkedHashMap<>();
        richMetrics.put("gst.turnover.trailing_12m", 1);
        richMetrics.put("bank.abb.average", 1);
        richMetrics.put("bank.turnover.trailing_12m", 1);
        richMetrics.put("bureau.live_unsecured_count", 1);
        richMetrics.put("bureau.emi.monthly", 1);
        richMetrics.put("itr.income.total", 1);
        richMetrics.put("income.monthly", 1);
        richMetrics.put("obligation.ratio", 1);
        richMetrics.put("dti.ratio", 1);
        richMetrics.put("itr.pat", 1);
        richMetrics.put("financials.tol", 1);
        richMetrics.put("financials.tnw", 1);
        richMetrics.put("bureau.enquiries_3m", 1);
        richMetrics.put("financials.interest_coverage", 1);
        richMetrics.put("financials.dte", 1);
        for (var d : catalog.listAll()) {
            if ("UNSAFE_SILENT_DEFAULT".equals(d.getClassification())
                    || "DEMO_ONLY".equals(d.getClassification())) {
                quarantine.intercept(
                        G0CandidateCohortFactory.TENANT_ID, "DIGILEAP", d.getLegacyKey(),
                        richMetrics);
            }
        }
        for (CiPolicyBinding b : bindings.ensureBindings(G0CandidateCohortFactory.TENANT_ID)) {
            if (b.isCritical()) {
                bindings.certify(G0CandidateCohortFactory.TENANT_ID, b.getLegacyParameter(), "tester",
                        Map.of("unitsMatch", true, "periodSemanticsMatch", true, "testsPass", true,
                                "replayPass", true, "shadowReviewed", true));
            }
        }
        UUID pkg = UUID.randomUUID();
        UUID strat = UUID.randomUUID();
        policyCert.certify(G0CandidateCohortFactory.TENANT_ID, pkg, "tester",
                Map.of("studioApproval", true, "makerChecker", true, "hardRuleTestsPass", true,
                        "historicalReplayComplete", true, "dualRunReviewed", true,
                        "criticalBindingsCertified", true, "noBlockingAmbiguity", true),
                List.of("ev1"));
        decisionCert.certify(G0CandidateCohortFactory.TENANT_ID, strat, "tester",
                Map.of("hybridLegacyOk", true), List.of("ev2"));
        dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID, UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 100), Map.of("outcome", "PASS", "amount", 100),
                false, Map.of("dataOrigin", "REPRESENTATIVE_FIXTURE"));
        control.setMode(G0CandidateCohortFactory.COHORT_ID, AuthorityMode.DUAL_RUN, "ops", "pilot");
        control.setMode(G0CandidateCohortFactory.COHORT_ID, AuthorityMode.LEGACY, "ops", "rollback proof");

        G0CutoverReadinessScorer.ScoreResult score = scorer.score(
                G0CandidateCohortFactory.TENANT_ID,
                G0CandidateCohortFactory.COHORT_ID,
                new G0CutoverReadinessScorer.GateInput(
                        Boolean.TRUE.equals(bindings.coverage(G0CandidateCohortFactory.TENANT_ID)
                                .get("allCriticalCertified")),
                        catalog.countUnsafeSilent() == 0,
                        false,
                        true,
                        policyCert.isCertified(G0CandidateCohortFactory.TENANT_ID, pkg),
                        decisionCert.isCertified(G0CandidateCohortFactory.TENANT_ID, strat),
                        true, true, true, true, true, true,
                        true,
                        (int) catalog.countUnsafeSilent(),
                        true));

        assertThat(score.overall()).isEqualTo(G0ReadinessOutcome.NOT_READY);
        assertThat(score.cohortOutcome()).isEqualTo(G0ReadinessOutcome.LIMITED_PILOT_READY);
        assertThat(score.limitedPilotReady()).isTrue();
    }

    @Test
    void validationDataClassifier_c6IsRepresentativeFixture() {
        assertThat(dataClassifier.classifyC6Bundle("CASE_A_STRONG"))
                .isEqualTo(ValidationDataOrigin.REPRESENTATIVE_FIXTURE);
        Map<String, Object> scan = dataClassifier.scanProviderFixtures();
        assertThat(scan.get("honestyNote")).asString().contains("ORIGIN.md");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) scan.get("fixtures");
        assertThat(fixtures).isNotEmpty();
        assertThat(fixtures).noneMatch(f -> Boolean.TRUE.equals(f.get("liveProduction")));
    }

    @Test
    void dualRunStatistics_notFabricated() {
        Map<String, Object> empty = dualRun.statistics(G0CandidateCohortFactory.COHORT_ID);
        assertThat(empty.get("applicationsDualRun")).isEqualTo(0);
        assertThat(empty.get("exactMatchPct")).isNull();
        assertThat(empty.get("dataOriginNote")).asString().contains("Not fabricated");

        dualRun.compareSnapshots(
                G0CandidateCohortFactory.COHORT_ID, UUID.randomUUID(), null,
                Map.of("outcome", "PASS", "amount", 100), Map.of("outcome", "PASS", "amount", 100),
                false, Map.of("dataOrigin", "REPRESENTATIVE_FIXTURE"));
        Map<String, Object> stats = dualRun.statistics(G0CandidateCohortFactory.COHORT_ID);
        assertThat(stats.get("applicationsDualRun")).isEqualTo(1);
        assertThat(stats.get("exactMatchPct")).isEqualTo(new BigDecimal("100.00"));

        Map<String, Object> impactReport = impact.analyze(G0CandidateCohortFactory.COHORT_ID, List.of());
        assertThat(impactReport.get("fabricated")).isEqualTo(false);
    }

    @Test
    void sourceGate_and_camAdapter() {
        var gate = sourceGate.evaluate(Map.of(
                "bureau", Map.of("present", true, "fresh", true, "subjectMatch", true,
                        "parserSuccessful", true, "canonicalMetricsAvailable", true),
                "bank", Map.of("present", true, "fresh", true, "subjectMatch", true,
                        "parserSuccessful", true, "canonicalMetricsAvailable", true),
                "gst", Map.of("present", true, "fresh", true, "subjectMatch", true,
                        "parserSuccessful", true, "canonicalMetricsAvailable", true),
                "itr", Map.of("present", true, "fresh", true, "subjectMatch", true,
                        "parserSuccessful", true, "canonicalMetricsAvailable", true)));
        assertThat(gate.readiness().name()).isEqualTo("READY");

        Map<String, Object> cam = camAdapter.toCamReadModel(Map.of(
                "amount", 1000, "tenure", 12, "pricing", 0.12,
                "conditions", List.of("CP1"), "reasonCodes", List.of("R1"),
                "authority", "L1", "outcome", "APPROVE"));
        assertThat(cam.get("writesCam")).isEqualTo(false);
        assertThat(cam.get("authoritative")).isEqualTo(false);
    }

    @Test
    void inventorySeeded() {
        assertThat(catalog.listAll().size()).isGreaterThanOrEqualTo(18);
        assertThat(catalog.countUnsafeSilent()).isGreaterThan(0);
    }
}
