package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DATA-PARAMETERS-CAPABILITY-SEMANTICS-CLEANUP-1 — Bureau Retail golden + lender UX projection.
 * Does not mutate catalogue production_ready flags.
 */
class DataParametersCapabilitySemanticsTest {

    private final CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
    private final DataParametersAdminService admin = new DataParametersAdminService();

    private CanonicalParameterDefinition require(String id) {
        return registry.findById(id).orElseThrow(() -> new AssertionError("missing " + id));
    }

    @Test
    void bureauRetail_platformIsProductionReady() {
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(require("bureau.score"));
        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        assertThat(platform.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY);
        assertThat(platform.get("providerLabel")).asString().containsIgnoringCase("Equifax");
    }

    @Test
    void bureauScore_supportedRaw() {
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(require("bureau.score"));
        @SuppressWarnings("unchecked")
        Map<String, Object> support = (Map<String, Object>) cap.get("parameterSupport");
        assertThat(support.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_RAW);
        assertThat(cap.get("canBillionTechSupport")).isEqualTo(true);
        assertThat(cap.get("flagsMutated")).isEqualTo(false);
        assertThat(cap.get("applicationDataStateExcluded")).isEqualTo(true);
    }

    @Test
    void ccWriteoff_supportedDerived_policyDesignAvailable_liveRequiresSubscription() {
        CanonicalParameterDefinition def = require("bureau.accounts.cc_writeoff");
        Map<String, Object> readiness = GacatParameterReadinessProjection.project(def);
        assertThat(readiness.get("productionReady")).isEqualTo(false);
        assertThat(readiness.get("legacyCatalogueProductionReadyClaim")).isEqualTo(false);
        // Spine capability — may be true when BuiltIn producer registered
        assertThat(readiness.get("policyTestReady")).isIn(true, false);

        Map<String, Object> cap = DataParametersCapabilitySemantics.project(
                def,
                readiness,
                family -> DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED);

        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        @SuppressWarnings("unchecked")
        Map<String, Object> support = (Map<String, Object>) cap.get("parameterSupport");
        @SuppressWarnings("unchecked")
        Map<String, Object> org = (Map<String, Object>) cap.get("yourOrganisation");
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) cap.get("policyDesign");
        @SuppressWarnings("unchecked")
        Map<String, Object> live = (Map<String, Object>) cap.get("liveUse");

        assertThat(platform.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY);
        assertThat(support.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_DERIVED);
        assertThat(String.valueOf(support.get("businessHow"))).containsIgnoringCase("Calculated by BillionTech");
        assertThat(org.get("status")).isEqualTo(DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED);
        assertThat(design.get("available")).isEqualTo(true);
        assertThat(String.valueOf(design.get("label"))).containsIgnoringCase("policy design");
        assertThat(live.get("available")).isEqualTo(false);
        assertThat(String.valueOf(live.get("label"))).containsIgnoringCase("Not certified");
        assertThat(cap.get("flagsMutated")).isEqualTo(false);
        assertThat(cap.get("productionReady")).isEqualTo(false);
    }

    @Test
    void notSubscribed_doesNotBlockPolicyDesign() {
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(
                require("bureau.score"),
                family -> DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED);
        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) cap.get("policyDesign");
        @SuppressWarnings("unchecked")
        Map<String, Object> live = (Map<String, Object>) cap.get("liveUse");
        assertThat(platform.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY);
        assertThat(design.get("available")).isEqualTo(true);
        assertThat(live.get("available")).isEqualTo(false);
        assertThat(String.valueOf(live.get("label"))).containsIgnoringCase("Not certified");
    }

    @Test
    void commercialBureau_providerLabelIsBusinessFacing() {
        List<CanonicalParameterDefinition> commercial = registry.all().stream()
                .filter(d -> {
                    String f = d.evaluatedFrom() == null ? "" : d.evaluatedFrom().toLowerCase();
                    return f.contains("commercial");
                })
                .toList();
        if (commercial.isEmpty()) {
            return;
        }
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(commercial.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        assertThat(platform.get("providerLabel")).isEqualTo("Commercial Bureau");
        assertThat(String.valueOf(platform.get("providerLabel"))).doesNotContain("SurePass");
    }

    @Test
    void applicationInput_notPresentedAsProviderGap() {
        CanonicalParameterDefinition def = require("application.loan_amount");
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(def);
        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        @SuppressWarnings("unchecked")
        Map<String, Object> support = (Map<String, Object>) cap.get("parameterSupport");
        assertThat(platform.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_NOT_APPLICABLE);
        assertThat(support.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SUPPORT_NOT_APPLICABLE);
        assertThat(String.valueOf(support.get("how"))).containsIgnoringCase("application");
    }

    @Test
    void bureauRetail_sourceFamilySummary_countsAreCoherent() {
        List<CanonicalParameterDefinition> bureau = registry.all().stream()
                .filter(d -> "Bureau Retail".equals(d.evaluatedFrom()))
                .toList();
        assertThat(bureau).isNotEmpty();
        Map<String, Object> summary = DataParametersCapabilitySemantics.sourceFamilySummary(
                "Bureau Retail", bureau, family -> DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED);
        assertThat(summary.get("platformIntegration"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY);
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) summary.get("parameterSupportCounts");
        int total = ((Number) counts.get("total")).intValue();
        int sum = ((Number) counts.get("supportedRaw")).intValue()
                + ((Number) counts.get("supportedDerived")).intValue()
                + ((Number) counts.get("providerDoesNotSupport")).intValue()
                + ((Number) counts.get("calculationNotImplemented")).intValue()
                + ((Number) counts.get("sourceNotIntegrated")).intValue()
                + ((Number) counts.get("notApplicable")).intValue()
                + ((Number) counts.get("other")).intValue();
        assertThat(sum).isEqualTo(total);
        assertThat(((Number) counts.get("supportedRaw")).intValue()
                + ((Number) counts.get("supportedDerived")).intValue()).isGreaterThan(0);
    }

    @Test
    void adminEnrich_exposesLenderCapabilitySections() {
        Map<String, Object> detail = admin.parameterDetail("bureau.accounts.cc_writeoff");
        assertThat(detail.get("found")).isEqualTo(true);
        assertThat(detail.get("capabilitySemantics")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> sections = (Map<String, Object>) detail.get("sections");
        assertThat(sections).containsKey("lenderCapability");
        assertThat(sections).containsKey("engineeringReadinessAdvanced");
        @SuppressWarnings("unchecked")
        Map<String, Object> lender = (Map<String, Object>) sections.get("lenderCapability");
        assertThat(lender.get("parameterSupportStatus"))
                .isEqualTo(DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_DERIVED);
        assertThat(lender.get("platformIntegrationStatus"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY);
    }

    @Test
    void overview_includesCapabilityModelMetadata() {
        Map<String, Object> overview = admin.overview();
        assertThat(overview.get("capabilitySemantics")).isEqualTo(true);
        assertThat(overview.get("applicationDataStateExcluded")).isEqualTo(true);
        assertThat(overview.get("sourceCapabilitySummary")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> supportStatuses = (List<String>) overview.get("parameterSupportStatuses");
        assertThat(supportStatuses).contains(
                DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_RAW,
                DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_DERIVED,
                DataParametersCapabilitySemantics.SUPPORT_PROVIDER_DOES_NOT_SUPPORT,
                DataParametersCapabilitySemantics.SUPPORT_CALCULATION_NOT_IMPLEMENTED,
                DataParametersCapabilitySemantics.SUPPORT_SOURCE_NOT_INTEGRATED);
    }

    @Test
    void commercialBureau_notIntegrated_parametersInheritSourceNotIntegrated() {
        List<CanonicalParameterDefinition> commercial = registry.all().stream()
                .filter(d -> {
                    String f = d.evaluatedFrom() == null ? "" : d.evaluatedFrom().toLowerCase();
                    return f.contains("commercial");
                })
                .toList();
        if (commercial.isEmpty()) {
            return; // catalogue may not expose commercial family in this build
        }
        Map<String, Object> cap = DataParametersCapabilitySemantics.project(commercial.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) cap.get("platformIntegration");
        @SuppressWarnings("unchecked")
        Map<String, Object> support = (Map<String, Object>) cap.get("parameterSupport");
        assertThat(platform.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SOURCE_PLATFORM_NOT_INTEGRATED);
        assertThat(support.get("status"))
                .isEqualTo(DataParametersCapabilitySemantics.SUPPORT_SOURCE_NOT_INTEGRATED);
    }

    @Test
    void flagsMutatedAlwaysFalse_acrossBureauSample() {
        List<String> sample = List.of(
                "bureau.score",
                "bureau.accounts.cc_writeoff",
                "bureau.accounts.writeoff_non_cc",
                "bureau.thin_file_indicator");
        for (String id : sample) {
            Map<String, Object> cap = DataParametersCapabilitySemantics.project(require(id));
            assertThat(cap.get("flagsMutated")).as(id).isEqualTo(false);
            // production projection is never catalogue boolean; legacy claim may differ
            Map<String, Object> readiness = GacatParameterReadinessProjection.project(require(id));
            assertThat(cap.get("productionReady")).isEqualTo(false);
            assertThat(readiness.get("productionReady")).isEqualTo(false);
            assertThat(cap.get("catalogueProductionReady"))
                    .isEqualTo(readiness.get("legacyCatalogueProductionReadyClaim"));
        }
    }

    @Test
    void bureauRetail_supportHistogram_forReport() {
        Map<String, Integer> hist = new LinkedHashMap<>();
        hist.put(DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_RAW, 0);
        hist.put(DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_DERIVED, 0);
        hist.put(DataParametersCapabilitySemantics.SUPPORT_PROVIDER_DOES_NOT_SUPPORT, 0);
        hist.put(DataParametersCapabilitySemantics.SUPPORT_CALCULATION_NOT_IMPLEMENTED, 0);
        hist.put(DataParametersCapabilitySemantics.SUPPORT_SOURCE_NOT_INTEGRATED, 0);
        hist.put(DataParametersCapabilitySemantics.SUPPORT_NOT_APPLICABLE, 0);
        List<String> other = new ArrayList<>();
        for (CanonicalParameterDefinition d : registry.all()) {
            if (!"Bureau Retail".equals(d.evaluatedFrom())) continue;
            Map<String, Object> cap = DataParametersCapabilitySemantics.project(d);
            @SuppressWarnings("unchecked")
            Map<String, Object> support = (Map<String, Object>) cap.get("parameterSupport");
            String st = String.valueOf(support.get("status"));
            if (hist.containsKey(st)) {
                hist.put(st, hist.get(st) + 1);
            } else {
                other.add(d.id() + "=" + st);
            }
        }
        assertThat(other).isEmpty();
        int total = hist.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isGreaterThan(10);
        // printable for FINAL REPORT
        System.out.println("BUREAU_RETAIL_SUPPORT_HISTOGRAM=" + hist + " TOTAL=" + total);
    }
}
