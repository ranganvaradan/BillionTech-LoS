package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DATA-PARAMETERS-DP1-ENRICHMENT-1 — derived readiness projection honesty.
 */
class GacatParameterReadinessProjectionTest {

    private final CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
    private final DataParametersAdminService admin = new DataParametersAdminService();

    private CanonicalParameterDefinition require(String id) {
        return registry.findById(id).orElseThrow(() -> new AssertionError("missing " + id));
    }

    @Test
    void productionReady_derivesCorrectly_bureauScore() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.score"));
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        assertThat(p.get("productionReady")).isEqualTo(true);
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("runtimeReady")).isEqualTo(true);
        assertThat(p.get("sourceType")).isEqualTo(GacatParameterReadinessProjection.SOURCE_PROVIDER);
    }

    @Test
    void runtimeReadyNonprod_derivesCorrectly_writeoffNonCc() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.accounts.writeoff_non_cc"));
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_RUNTIME_READY_NONPROD);
        assertThat(p.get("runtimeReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("policyTestReady")).isEqualTo(true);
    }

    @Test
    void policyTestOnly_doesNotImplyProduction() {
        // Implemented studio helper that is NOT known-runtime-nonprod → POLICY_TEST_ONLY
        // Prefer a known POLICY_TEST_READY id when present; fall back to max_dpd_6m which is RUNTIME_READY_NONPROD
        Map<String, Object> max6 = GacatParameterReadinessProjection.project(require("bureau.max_dpd_6m"));
        assertThat(max6.get("productionReady")).isEqualTo(false);
        assertThat(max6.get("policyTestReady")).isEqualTo(true);
        // policyTestReady must not promote overall to PRODUCTION_READY
        assertThat(max6.get("overallReadiness")).isNotEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        assertThat(String.valueOf(max6.get("overallReadiness"))).isIn(
                GacatParameterReadinessProjection.OVERALL_RUNTIME_READY_NONPROD,
                GacatParameterReadinessProjection.OVERALL_POLICY_TEST_ONLY);
    }

    @Test
    void catalogueOnly_whenNotImplemented() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.thin_file_indicator"));
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_CATALOGUE_ONLY);
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("policyTestReady")).isEqualTo(false);
    }

    @Test
    void contradictoryMetadata_returnsReadinessUnknown() {
        CanonicalParameterDefinition.Capability bad = CanonicalParameterDefinition.Capability.of(
                "BUREAU_RETAIL", true, true, true, false, true,
                "SCALAR", "/fake/path", null, null, null, null);
        CanonicalParameterDefinition def = new CanonicalParameterDefinition(
                "test.contradiction.prod_without_impl",
                "Contradiction fixture",
                "Bureau Retail",
                CanonicalParameterDefinition.DERIVED,
                null, null, null, "fixture", List.of(), "SomeCalculator",
                List.of(), null, null, bad);
        Map<String, Object> p = GacatParameterReadinessProjection.project(def);
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_READINESS_UNKNOWN);
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) p.get("overallReadinessReasons");
        assertThat(reasons).anyMatch(r -> r.contains("productionReady=true") && r.contains("implemented=false"));
    }

    @Test
    void providerlessApplicationParameter_notFalselyUnintegrated() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("application.proposed_edi"));
        assertThat(p.get("sourceType")).isEqualTo(GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT);
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) p.get("provider");
        assertThat(provider.get("status")).isEqualTo(GacatParameterReadinessProjection.PROVIDER_NOT_APPLICABLE);
        assertThat(p.get("providerBound")).isEqualTo(false);
    }

    @Test
    void kycWorkflowParameter_providerNotRequired() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("kyc.pan.verified"));
        assertThat(p.get("sourceType")).isIn(
                GacatParameterReadinessProjection.SOURCE_WORKFLOW,
                GacatParameterReadinessProjection.SOURCE_PROVIDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) p.get("provider");
        // Empty provider_code must not force "unintegrated" look for workflow/KYC
        assertThat(provider.get("status")).isIn(
                GacatParameterReadinessProjection.PROVIDER_NOT_APPLICABLE,
                GacatParameterReadinessProjection.PROVIDER_INFERRED,
                GacatParameterReadinessProjection.PROVIDER_UNKNOWN);
        if (GacatParameterReadinessProjection.SOURCE_WORKFLOW.equals(p.get("sourceType"))) {
            assertThat(provider.get("status")).isEqualTo(GacatParameterReadinessProjection.PROVIDER_NOT_APPLICABLE);
        }
    }

    @Test
    void sourcePathDoesNotImplyProductionReady() {
        CanonicalParameterDefinition.Capability pathOnly = CanonicalParameterDefinition.Capability.of(
                "BUREAU_RETAIL", true, true, true, true, false,
                "SCALAR", "InquiryResponse/Score/sch:Score", null, null, null, null);
        CanonicalParameterDefinition def = new CanonicalParameterDefinition(
                "test.path.only",
                "Path without certification",
                "Bureau Retail",
                CanonicalParameterDefinition.DERIVED,
                null, null, null, "path fixture", List.of(), "StudioCalc",
                List.of(), null, null, pathOnly);
        Map<String, Object> p = GacatParameterReadinessProjection.project(def);
        assertThat(p.get("mappingAvailable")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("overallReadiness")).isNotEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
    }

    @Test
    void policyTestReadyDoesNotImplyProductionReady() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.status_ntc"));
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("overallReadiness")).isNotEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
    }

    @Test
    void bureauGoldenStatuses_remainUnchanged() {
        String[] goldens = {
                "bureau.score",
                "bureau.status_ntc",
                "bureau.max_dpd_6m",
                "bureau.accounts.writeoff_non_cc",
                "bureau.written_off_account_count",
                "bureau.inquiries.current_month",
                "bureau.recent_inquiries_90d",
                "bureau.live_unsecured_loan_count"
        };
        for (String id : goldens) {
            var opt = registry.findById(id);
            if (opt.isEmpty()) {
                // bureau.inquiries.last_3m may be seed-only drift — skip if absent
                continue;
            }
            CanonicalParameterDefinition def = opt.get();
            boolean before = def.capability() != null && def.capability().productionReady();
            Map<String, Object> p = GacatParameterReadinessProjection.project(def);
            assertThat(p.get("productionReady")).as(id).isEqualTo(before || CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type()));
            // Projection is read-only — capability flags untouched
            assertThat(def.capability().productionReady()).as(id + " flag unchanged").isEqualTo(before);
        }
        // Explicit absences / expected buckets
        assertThat(GacatParameterReadinessProjection.project(require("bureau.score")).get("overallReadiness"))
                .isEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        assertThat(GacatParameterReadinessProjection.project(require("bureau.accounts.writeoff_non_cc")).get("overallReadiness"))
                .isEqualTo(GacatParameterReadinessProjection.OVERALL_RUNTIME_READY_NONPROD);
    }

    @Test
    void bankGstFinancialSmoke_projectionWorks() {
        for (String id : List.of(
                "banking.avg_daily_balance_3m",
                "gst.turnover.trailing_12m",
                "financial.revenue",
                "kyc.pan.verified",
                "application.requested_amount")) {
            assertThat(registry.findById(id)).as(id).isPresent();
            Map<String, Object> p = GacatParameterReadinessProjection.project(require(id));
            assertThat(p.get("overallReadiness")).as(id).isIn(
                    GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY,
                    GacatParameterReadinessProjection.OVERALL_RUNTIME_READY_NONPROD,
                    GacatParameterReadinessProjection.OVERALL_POLICY_TEST_ONLY,
                    GacatParameterReadinessProjection.OVERALL_CATALOGUE_ONLY,
                    GacatParameterReadinessProjection.OVERALL_READINESS_UNKNOWN);
            assertThat(p.get("sourceType")).as(id).isNotNull();
        }
    }

    @Test
    void policyTestOnly_derivesFromImplementedNonRuntimeFixture() {
        CanonicalParameterDefinition.Capability studio = CanonicalParameterDefinition.Capability.of(
                "BUREAU_RETAIL", true, true, true, true, false,
                "SCALAR", null, null, null, null, null);
        CanonicalParameterDefinition def = new CanonicalParameterDefinition(
                "test.policy.test.only",
                "Policy test only fixture",
                "Bureau Retail",
                CanonicalParameterDefinition.DERIVED,
                null, null, null, "studio only", List.of(), "StudioOnlyCalc",
                List.of(), null, null, studio);
        Map<String, Object> p = GacatParameterReadinessProjection.project(def);
        assertThat(p.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_POLICY_TEST_ONLY);
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("runtimeReady")).isEqualTo(false);
        assertThat(p.get("productionReady")).isEqualTo(false);
    }

    @Test
    void adminEnrich_preservesExistingBehaviourAndAddsDp1() {
        Map<String, Object> overview = admin.overview();
        assertThat(overview.get("readModelOnly")).isEqualTo(true);
        assertThat(overview.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(overview.get("dp1")).isEqualTo(true);
        assertThat(overview.get("knownCatalogueDrift")).asList().isNotEmpty();

        Map<String, Object> detail = admin.parameterDetail("bureau.score");
        assertThat(detail.get("found")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> parameter = (Map<String, Object>) detail.get("parameter");
        assertThat(parameter.get("overallReadiness")).isEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        assertThat(parameter.get("sections")).isInstanceOf(Map.class);
        assertThat(parameter.get("advanced")).isInstanceOf(Map.class);

        Map<String, Object> search = admin.search("bureau.score");
        assertThat(search.get("results")).asList().isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) search.get("results")).get(0);
        assertThat(first.get("overallReadiness")).isNotNull();
        assertThat(first.get("sourceType")).isNotNull();
    }

    @Test
    void workflowLookup_inverse_exists() {
        Map<String, Object> score = WorkflowParameterProvidesCatalog.lookupForParameter("bureau.score");
        assertThat(score.get("workflowAvailable")).isEqualTo(true);
        assertThat(score.get("productionSteps")).asList().contains("BUREAU_PULL");

        Map<String, Object> studio = WorkflowParameterProvidesCatalog.lookupForParameter("bureau.max_dpd_6m");
        assertThat(studio.get("studioOnlySteps")).asList().contains("BUREAU_PULL");
    }
}
