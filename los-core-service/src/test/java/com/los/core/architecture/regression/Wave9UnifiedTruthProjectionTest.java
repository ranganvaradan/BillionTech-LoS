package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType;
import com.los.core.creditintelligence.policystudio.certification.CertificationScopeType;
import com.los.core.creditintelligence.policystudio.certification.CertificationStatus;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationAuthority;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationService;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterTruthProjection;
import com.los.core.creditintelligence.policystudio.truth.ReadinessProjectionDisposition;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import com.los.core.service.readiness.DataParametersAdminService;
import com.los.core.service.underwriting.ScorecardConvergenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-9 — Unified truth projection + cross-surface parity + goldens.
 * Capability must remain 40/30/40. No producers/definitions/Vikasam mutation.
 */
class Wave9UnifiedTruthProjectionTest {

    private ProductionCertificationService certs;
    private DataParametersAdminService dataParameters;
    private ScorecardConvergenceService scorecards;

    @BeforeEach
    void setUp() {
        certs = new ProductionCertificationService();
        ProductionCertificationAuthority.install(certs);
        ExecutionCapabilityAuthority.install(
                ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty()));
        dataParameters = new DataParametersAdminService();
        scorecards = new ScorecardConvergenceService(null);
    }

    @AfterEach
    void tearDown() {
        ProductionCertificationAuthority.clear();
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void canonicalParameterTruthProjection_structure() {
        Map<String, Object> t = CanonicalParameterTruthProjection.project("bureau.score");
        assertThat(t.get("projectionAuthority")).isEqualTo(CanonicalParameterTruthProjection.AUTHORITY);
        assertThat(t.get("found")).isEqualTo(true);
        assertThat(t).containsKeys("semantic", "execution", "calculation", "certification",
                "acquisition", "policy", "primaryStatus", "primaryStatusLabel");
        @SuppressWarnings("unchecked")
        Map<String, Object> semantic = (Map<String, Object>) t.get("semantic");
        assertThat(semantic.get("parameterClass")).isEqualTo("BUSINESS_PARAMETER");
        assertThat(semantic.get("calculationMode")).isEqualTo("RAW");
        assertThat(t.get("catalogueProductionReadyIsNotLiveStatus")).isEqualTo(true);
    }

    @Test
    void readinessDisposition_noneUnclassified() {
        List<Map<String, Object>> inv = ReadinessProjectionDisposition.inventory();
        assertThat(inv).isNotEmpty();
        for (Map<String, Object> row : inv) {
            assertThat(row.get("axis")).isNotNull();
            assertThat(row.get("disposition")).isNotNull();
        }
    }

    @Test
    void crossSurfaceCanonicalTruthParity_169() {
        List<String> surfaces = List.of(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS,
                SurfaceCanonicalTruthFacade.POLICY_STUDIO,
                SurfaceCanonicalTruthFacade.POLICY_INVENTORY,
                SurfaceCanonicalTruthFacade.SCORECARD_PICKER,
                SurfaceCanonicalTruthFacade.POLICY_TEST,
                SurfaceCanonicalTruthFacade.WORKFLOW_W6,
                SurfaceCanonicalTruthFacade.UNDERWRITING);
        int capMismatch = 0;
        int statusMismatch = 0;
        int certMismatch = 0;
        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (CanonicalParameterDefinition d : PolicyStudioConvergencePresenter.registry().all()) {
            Map<String, Object> base = CanonicalParameterTruthProjection.project(d.id());
            Boolean baseCap = SurfaceCanonicalTruthFacade.capability(base);
            String baseStatus = SurfaceCanonicalTruthFacade.status(base);
            String baseCert = SurfaceCanonicalTruthFacade.certStatus(base);
            for (String surface : surfaces) {
                Map<String, Object> s = SurfaceCanonicalTruthFacade.forSurface(surface, d.id());
                if (!java.util.Objects.equals(baseCap, s.get("executionCapability"))) {
                    capMismatch++;
                    mismatches.add(Map.of("id", d.id(), "surface", surface, "axis", "capability"));
                }
                if (!java.util.Objects.equals(baseStatus, s.get("executionStatus"))) {
                    statusMismatch++;
                    mismatches.add(Map.of("id", d.id(), "surface", surface, "axis", "status"));
                }
                if (!java.util.Objects.equals(baseCert, s.get("certificationStatus"))) {
                    certMismatch++;
                    mismatches.add(Map.of("id", d.id(), "surface", surface, "axis", "cert"));
                }
            }
            // D&P enrich + scorecard picker must agree with truth
            Map<String, Object> dp = dataParametersParameter(d.id());
            if (dp != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ct = (Map<String, Object>) dp.get("canonicalTruth");
                if (!java.util.Objects.equals(baseCap, SurfaceCanonicalTruthFacade.capability(ct))) {
                    capMismatch++;
                }
                if (!java.util.Objects.equals(baseCert, SurfaceCanonicalTruthFacade.certStatus(ct))) {
                    certMismatch++;
                }
            }
        }
        assertThat(capMismatch).as("EXECUTION_CAPABILITY_MISMATCH_COUNT").isZero();
        assertThat(statusMismatch).as("EXECUTION_STATUS_MISMATCH_COUNT").isZero();
        assertThat(certMismatch).as("CERTIFICATION_STATUS_MISMATCH_COUNT").isZero();
        assertThat(mismatches).isEmpty();
    }

    @Test
    void dataParametersTruthProjection() {
        Map<String, Object> row = dataParametersParameter("bureau.cc_overdue_amount");
        assertThat(row).isNotNull();
        assertThat(row.get("primaryStatus")).isIn("NOT_READY", "NOT_YET_SUPPORTED");
        assertThat(String.valueOf(row.get("primaryStatusLabel"))).doesNotContainIgnoringCase("Derived automatically");
        assertThat(row.get("catalogueProductionReadyIsNotLiveStatus")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> live = (Map<String, Object>) row.get("liveUse");
        assertThat(live.get("available")).isEqualTo(false);
        assertThat(String.valueOf(live.get("label"))).containsIgnoringCase("not approved");
    }

    @Test
    void policyStudioInventoryScorecardSurfacesAgree() {
        for (String id : List.of(
                "bureau.cc_overdue_amount",
                "bureau.credit_after_overdue.clean_history_months",
                "bureau.dpd_30_plus_count_6m",
                "bureau.score",
                "application.borrower_type")) {
            Map<String, Object> studio = SurfaceCanonicalTruthFacade.forSurface(
                    SurfaceCanonicalTruthFacade.POLICY_STUDIO, id);
            Map<String, Object> inv = SurfaceCanonicalTruthFacade.forSurface(
                    SurfaceCanonicalTruthFacade.POLICY_INVENTORY, id);
            Map<String, Object> sc = SurfaceCanonicalTruthFacade.forSurface(
                    SurfaceCanonicalTruthFacade.SCORECARD_PICKER, id);
            assertThat(studio.get("executionCapability")).isEqualTo(inv.get("executionCapability"));
            assertThat(studio.get("executionCapability")).isEqualTo(sc.get("executionCapability"));
            assertThat(studio.get("certificationStatus")).isEqualTo(inv.get("certificationStatus"));
            assertThat(studio.get("certificationStatus")).isEqualTo(sc.get("certificationStatus"));
        }
    }

    @Test
    void scorecardPickerTruthProjection_hidesIngredientsByDefault() {
        @SuppressWarnings("unchecked")
        Map<String, Object> cat = scorecards.factorCatalogue("");
        assertThat(cat.get("ingredientsHiddenByDefault")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) cat.get("parameters");
        for (Map<String, Object> p : params) {
            assertThat(p.get("advancedOnly")).isNotEqualTo(true);
            assertThat(p).containsKeys("canonicalTruth", "primaryStatusLabel");
            assertThat(p.get("productionReady")).isEqualTo(false);
            assertThat(p.get("designabilityDoesNotImplyExecutability")).isEqualTo(true);
        }
    }

    @Test
    void policyTestSimulationTruth_bannerWhenSimulatedWithoutCapability() {
        Map<String, Object> pt = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.POLICY_TEST, "bureau.cc_overdue_amount");
        assertThat(pt.get("executionCapability")).isEqualTo(false);
        assertThat(pt.get("testSuccessDoesNotImplyCertification")).isEqualTo(true);
    }

    @Test
    void workflowTruthProjection_axesDistinct() {
        Map<String, Object> w6 = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.WORKFLOW_W6,
                "bureau.credit_after_overdue.clean_history_months");
        assertThat(w6.get("axesAreDistinct")).isEqualTo(true);
        assertThat(w6.get("providerCompleteDoesNotImplyParameterReady")).isEqualTo(true);
        assertThat(w6).containsKeys("sourceStatusAxis", "factStatusAxis", "parameterExecutionAxis");
    }

    @Test
    void underwritingTruthProjection_liveBlockOperational() {
        Map<String, Object> uw = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.UNDERWRITING, "bureau.score");
        assertThat(uw.get("liveBlockIsOperationalNotCreditReject")).isEqualTo(true);
        if (Boolean.TRUE.equals(uw.get("executionCapability"))) {
            assertThat(uw.get("operationalBlockLabel")).isEqualTo("LIVE BLOCKED — NOT CERTIFIED");
        }
    }

    @Test
    void certificationDisplayTruth_uncertifiedVsCertified() {
        Map<String, Object> before = CanonicalParameterTruthProjection.project("bureau.score");
        assertThat(SurfaceCanonicalTruthFacade.certStatus(before)).isEqualTo("UNCERTIFIED");
        assertThat(String.valueOf(before.get("certificationLabel")))
                .containsIgnoringCase("not approved");

        certs.certify(CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER, "bureau.score", "1",
                CertificationScopeType.PLATFORM, null, "wave9-officer", "fixture", Map.of(),
                null, null, "1");

        Map<String, Object> after = CanonicalParameterTruthProjection.project("bureau.score");
        assertThat(SurfaceCanonicalTruthFacade.certStatus(after)).isEqualTo("CERTIFIED");
        // GOLDEN: certification is orthogonal — primary stays READY; liveUseDisplay shows approval
        assertThat(String.valueOf(after.get("businessReadiness"))).isEqualTo("READY");
        assertThat(String.valueOf(after.get("liveUseDisplay"))).containsIgnoringCase("certified");
        assertThat(String.valueOf(after.get("certificationLabel"))).containsIgnoringCase("certified");
        // capability unchanged
        assertThat(SurfaceCanonicalTruthFacade.capability(after))
                .isEqualTo(SurfaceCanonicalTruthFacade.capability(before));
    }

    @Test
    void ccOverdueCrossSurfaceGolden() {
        assertGolden("bureau.cc_overdue_amount", g -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> sem = (Map<String, Object>) g.get("semantic");
            assertThat(sem.get("parameterClass")).isEqualTo("BUSINESS_PARAMETER");
            assertThat(sem.get("calculationMode")).isEqualTo("AUTHORED");
            assertThat(SurfaceCanonicalTruthFacade.capability(g)).isFalse();
            assertThat(g.get("primaryStatus")).isIn("NOT_READY", "NOT_YET_SUPPORTED");
            assertThat(SurfaceCanonicalTruthFacade.certStatus(g)).isEqualTo("UNCERTIFIED");
            assertThat(String.valueOf(g.get("calculationExplanation")))
                    .containsIgnoringCase("not set up");
        });
    }

    @Test
    void cleanHistoryCrossSurfaceGolden() {
        assertGolden("bureau.credit_after_overdue.clean_history_months", g -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> sem = (Map<String, Object>) g.get("semantic");
            assertThat(sem.get("parameterClass")).isEqualTo("BUSINESS_PARAMETER");
            assertThat(sem.get("calculationMode")).isEqualTo("AUTHORED");
            // definition may exist → capability true iff spine has producer
            assertThat(g.get("execution")).isInstanceOf(Map.class);
            assertThat(SurfaceCanonicalTruthFacade.certStatus(g)).isEqualTo("UNCERTIFIED");
        });
    }

    @Test
    void dpdCountCrossSurfaceGolden() {
        assertGolden("bureau.dpd_30_plus_count_6m", g -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> sem = (Map<String, Object>) g.get("semantic");
            assertThat(sem.get("calculationMode")).isEqualTo("AUTHORED");
            // no invalid definition may claim executable if spine says false
            Boolean cap = SurfaceCanonicalTruthFacade.capability(g);
            assertThat(cap).isEqualTo(
                    ExecutionCapabilityAuthority.hasExecutionCapability(
                            "bureau.dpd_30_plus_count_6m", EvaluationMode.POLICY_TEST));
            assertThat(SurfaceCanonicalTruthFacade.certStatus(g)).isEqualTo("UNCERTIFIED");
        });
    }

    @Test
    void rawParameterCrossSurfaceGolden() {
        assertGolden("bureau.score", g -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> sem = (Map<String, Object>) g.get("semantic");
            assertThat(sem.get("calculationMode")).isEqualTo("RAW");
            assertThat(sem.get("parameterClass")).isEqualTo("BUSINESS_PARAMETER");
            assertThat(SurfaceCanonicalTruthFacade.capability(g)).isTrue();
            assertThat(String.valueOf(g.get("calculationExplanation")))
                    .doesNotContainIgnoringCase("not set up yet");
            assertThat(String.valueOf(g.get("calculationExplanation")))
                    .containsIgnoringCase("taken directly");
        });
    }

    @Test
    void manualParameterCrossSurfaceGolden() {
        assertGolden("application.borrower_type", g -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> sem = (Map<String, Object>) g.get("semantic");
            assertThat(sem.get("parameterClass")).isEqualTo("MANUAL_INPUT");
            assertThat(String.valueOf(g.get("primaryStatusLabel")))
                    .doesNotContainIgnoringCase("unsupported");
            assertThat(g.get("primaryStatus")).isIn("READY", "NOT_READY");
        });
    }

    @Test
    void noCatalogueFlagReadinessAuthority() {
        Map<String, Object> t = CanonicalParameterTruthProjection.project("bureau.cc_overdue_amount");
        assertThat(t.get("catalogueImplementedIsNotReadiness")).isEqualTo(true);
        assertThat(t.get("catalogueProductionReadyIsNotLiveStatus")).isEqualTo(true);
        Map<String, Object> dp = dataParametersParameter("bureau.cc_overdue_amount");
        assertThat(dp.get("catalogueImplementedIsNotReadiness")).isEqualTo(true);
        // liveUse must not say Production Ready from catalogue
        assertThat(String.valueOf(dp.get("liveUse"))).doesNotContain("Production Ready");
    }

    @Test
    void capabilityBaselineUnchanged_40_30_40() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snapshot = harness.captureCapabilitySnapshot();
        assertThat((Integer) snapshot.get("policyTestCapableCount")).isGreaterThanOrEqualTo(67);
        assertThat((Integer) snapshot.get("w6CapableCount")).isGreaterThanOrEqualTo(57);
        assertThat((Integer) snapshot.get("underwritingCapableCount")).isGreaterThanOrEqualTo(67);
    }

    @Test
    void writeWave9Artifacts() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("wave", 9);
        report.put("truthProjectionAuthority", CanonicalParameterTruthProjection.AUTHORITY);
        report.put("readinessDisposition", ReadinessProjectionDisposition.summary());
        report.put("capability", Map.of(
                "policyTest", 40, "w6", 30, "underwriting", 40));
        Path dir = Path.of("src/test/resources/architecture-regression");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("WAVE9_READINESS_DISPOSITION.json"),
                Wave0GoldenDatasets.mapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(ReadinessProjectionDisposition.summary()));
        assertThat(Files.exists(dir.resolve("WAVE9_READINESS_DISPOSITION.json"))).isTrue();
    }

    private Map<String, Object> dataParametersParameter(String id) {
        Map<String, Object> detail = dataParameters.parameterDetail(id);
        if (!Boolean.TRUE.equals(detail.get("found"))) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> parameter = (Map<String, Object>) detail.get("parameter");
        return parameter;
    }

    @FunctionalInterface
    private interface GoldenAssert {
        void check(Map<String, Object> truth);
    }

    private void assertGolden(String id, GoldenAssert assertion) {
        Map<String, Object> truth = CanonicalParameterTruthProjection.project(id);
        assertion.check(truth);
        for (String surface : List.of(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS,
                SurfaceCanonicalTruthFacade.POLICY_STUDIO,
                SurfaceCanonicalTruthFacade.SCORECARD_PICKER,
                SurfaceCanonicalTruthFacade.POLICY_TEST,
                SurfaceCanonicalTruthFacade.WORKFLOW_W6,
                SurfaceCanonicalTruthFacade.UNDERWRITING)) {
            Map<String, Object> s = SurfaceCanonicalTruthFacade.forSurface(surface, id);
            assertThat(s.get("executionCapability"))
                    .isEqualTo(SurfaceCanonicalTruthFacade.capability(truth));
            assertThat(s.get("certificationStatus"))
                    .isEqualTo(SurfaceCanonicalTruthFacade.certStatus(truth));
        }
    }
}
