package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DP-1 readiness — execution from spine; productionReady never from catalogue boolean.
 */
class GacatParameterReadinessProjectionTest {

    private final CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();

    @BeforeAll
    static void installSpine() {
        ExecutionCapabilityAuthority.install(
                ExecutionSpineProducerBootstrap.standalone((id, t) -> java.util.Optional.empty()));
    }

    private CanonicalParameterDefinition require(String id) {
        return registry.findById(id).orElseThrow(() -> new AssertionError("missing " + id));
    }

    @Test
    void bureauScore_spineCapable_notCatalogueCertified() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.score"));
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("runtimeReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("legacyCatalogueProductionReadyClaim")).isEqualTo(true);
        assertThat(p.get("overallReadiness")).isNotEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
        assertThat(p.get("sourceType")).isEqualTo(GacatParameterReadinessProjection.SOURCE_PROVIDER);
    }

    @Test
    void writeoffNonCc_spineCapable() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.accounts.writeoff_non_cc"));
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("runtimeReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
    }

    @Test
    void maxDpd6m_policyTestCapable_notProduction() {
        Map<String, Object> max6 = GacatParameterReadinessProjection.project(require("bureau.max_dpd_6m"));
        assertThat(max6.get("productionReady")).isEqualTo(false);
        assertThat(max6.get("policyTestReady")).isEqualTo(true);
        assertThat(max6.get("overallReadiness")).isNotEqualTo(GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY);
    }

    @Test
    void thinFile_notSpineCapable() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.thin_file_indicator"));
        assertThat(p.get("policyTestReady")).isEqualTo(false);
        assertThat(p.get("productionReady")).isEqualTo(false);
    }

    @Test
    void catalogueProductionClaim_isNotExecutionTruth() {
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
        assertThat(p.get("productionReady")).isEqualTo(false);
        assertThat(p.get("legacyCatalogueProductionReadyClaim")).isEqualTo(true);
        assertThat(p.get("policyTestReady")).isEqualTo(false);
    }

    @Test
    void applicationProposedEdi_spineRaw() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("application.proposed_edi"));
        assertThat(p.get("sourceType")).isEqualTo(GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT);
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) p.get("provider");
        assertThat(provider.get("status")).isEqualTo(GacatParameterReadinessProjection.PROVIDER_NOT_APPLICABLE);
    }

    @Test
    void kycWorkflowParameter_providerNotRequired() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("kyc.pan.verified"));
        assertThat(p.get("sourceType")).isIn(
                GacatParameterReadinessProjection.SOURCE_WORKFLOW,
                GacatParameterReadinessProjection.SOURCE_PROVIDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) p.get("provider");
        assertThat(provider.get("status")).isIn(
                GacatParameterReadinessProjection.PROVIDER_NOT_APPLICABLE,
                GacatParameterReadinessProjection.PROVIDER_INFERRED,
                GacatParameterReadinessProjection.PROVIDER_UNKNOWN);
    }

    @Test
    void pathOnly_notCertified() {
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
        assertThat(p.get("policyTestReady")).isEqualTo(false);
    }

    @Test
    void statusNtc_spineCapable_notCertified() {
        Map<String, Object> p = GacatParameterReadinessProjection.project(require("bureau.status_ntc"));
        assertThat(p.get("policyTestReady")).isEqualTo(true);
        assertThat(p.get("productionReady")).isEqualTo(false);
    }

    @Test
    void catalogueFlagsUnchanged_byProjection() {
        for (String id : List.of(
                "bureau.score",
                "bureau.status_ntc",
                "bureau.max_dpd_6m",
                "bureau.accounts.writeoff_non_cc")) {
            CanonicalParameterDefinition def = require(id);
            boolean before = def.capability().productionReady();
            GacatParameterReadinessProjection.project(def);
            assertThat(def.capability().productionReady()).as(id).isEqualTo(before);
            assertThat(GacatParameterReadinessProjection.project(def).get("productionReady"))
                    .as(id + " projection").isEqualTo(false);
        }
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
            assertThat(p.get("canonicalParameterId")).isEqualTo(id);
            assertThat(p.get("productionReady")).isEqualTo(false);
        }
    }
}
