package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.service.readiness.DataParametersAdminService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GACAT-SOURCE-CATALOGUE-RECOVERY-1 — registry expansion, lineage, exclusivity, authority.
 */
class GacatSourceCatalogueRecovery1Test {

    private final CanonicalParameterRegistry registry = new CanonicalParameterRegistry();
    private final DataParametersAdminService admin = new DataParametersAdminService();

    @Test
    void bureauRetailDerivedHonesty_emittedIdsImplementedNotCertified() {
        List<CanonicalParameterDefinition> derived = GacatCatalogueSeed.all().stream()
                .filter(d -> CanonicalParameterDefinition.DERIVED.equals(d.type()))
                .filter(d -> "Bureau Retail".equals(d.evaluatedFrom()))
                .toList();
        assertThat(derived).hasSize(GacatCatalogueSeed.BUREAU_RETAIL_DERIVED_TARGET_COUNT);
        for (String id : BuiltInBureauMetricProducer.EMITTED_IDS) {
            CanonicalParameterDefinition d = registry.findById(id).orElseThrow();
            assertThat(d.capability().implemented()).as(id).isTrue();
            assertThat(d.capability().derivationDefined()).as(id).isTrue();
            assertThat(d.capability().productionReady()).as(id).isFalse();
            assertThat(d.existingImplementationBinding()).as(id).contains("BureauMetricService");
            assertThat(d.existingImplementationBinding()).as(id).doesNotContain("PolicyBureauMetricService");
            for (String prim : d.requiredPrimitives()) {
                assertThat(prim).as(id + " primitive").contains(".");
                CanonicalParameterDefinition raw = registry.findById(prim).orElseThrow();
                assertThat(raw.type()).as(prim).isEqualTo(CanonicalParameterDefinition.RAW);
            }
        }
        CanonicalParameterDefinition cc = registry.findById("bureau.cc_overdue_amount").orElseThrow();
        assertThat(cc.calculationSummary()).containsIgnoringCase("MAX");
        assertThat(cc.calculationSummary()).doesNotContainIgnoringCase("Sum of credit-card");
        CanonicalParameterDefinition util = registry.findById("bureau.cc_utilisation").orElseThrow();
        assertThat(util.calculationSummary()).containsIgnoringCase("SUM");
        assertThat(util.capability().implemented()).isTrue();
    }

    @Test
    void rawFieldRegistryIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (CanonicalParameterDefinition p : registry.all()) {
            assertThat(ids.add(p.id())).as("duplicate id %s", p.id()).isTrue();
        }
        assertThat(registry.all().size()).isGreaterThan(28);
    }

    @Test
    void bureauRetailAndCommercialAreSeparated() {
        Map<String, Object> retail = registry.browseBySource("Bureau Retail");
        Map<String, Object> commercial = registry.browseBySource("Bureau Commercial");
        assertThat((int) retail.get("rawCount")).isGreaterThan(20);
        assertThat((int) commercial.get("rawCount")).isGreaterThan(10);
        assertThat(registry.findById("bureau.tradeline.payment_history")).isPresent();
        assertThat(registry.findById("bureau.commercial.facility.sanction_amount")).isPresent();
        assertThat(registry.findById("bureau.commercial.guarantee_exposure")).isPresent();
        assertThat(registry.findById("bureau.commercial.guarantee_exposure").orElseThrow()
                .capability().derivationDefined()).isFalse();
    }

    @Test
    void maxDpd6mExactLineageAndAlgorithm() {
        CanonicalParameterDefinition d = registry.findById("bureau.max_dpd_6m").orElseThrow();
        assertThat(d.type()).isEqualTo(CanonicalParameterDefinition.DERIVED);
        assertThat(d.calculationSummary()).containsIgnoringCase("trailing 6");
        assertThat(d.calculationSummary()).containsIgnoringCase("MAX");
        assertThat(d.requiredPrimitives()).contains("bureau.tradeline.dpd_month");
        assertThat(d.requiredPrimitives()).doesNotContain("bureau.tradeline.payment_history");
        assertThat(d.capability().implemented()).isTrue();
        assertThat(d.capability().productionReady()).isFalse(); // not production-certified
        assertThat(d.existingImplementationBinding()).isEqualTo("BureauMetricService.evaluateMaxDpd");
        assertThat(d.calculationSummary()).containsIgnoringCase("YearMonth");
        assertThat(d.capability().aggregation()).isEqualTo("MAX");
        assertThat(d.capability().missingDataTreatment()).containsIgnoringCase("insufficient");

        var bureau = new PolicyBureauMetricService();
        var tls = List.of(new PolicyBureauMetricService.TradelineInput(
                "PL", "Active", false, null, null,
                List.of(
                        new PolicyBureauMetricService.PaymentMonth(YearMonth.of(2024, 5), 45),
                        new PolicyBureauMetricService.PaymentMonth(YearMonth.of(2023, 1), 90)
                ),
                null, false));
        var r = bureau.maxDpd6m(tls, LocalDate.of(2024, 6, 15));
        assertThat(r.get("v")).isEqualTo(45);
        assertThat(bureau.maxDpd6m(List.of(), LocalDate.of(2024, 6, 15)).get("outcome"))
                .isIn("DATA_INSUFFICIENT", PolicyBureauMetricService.OUTCOME_DI);
    }

    @Test
    void noDuplicateSynonymFactsAsSeparateIds() {
        // Aliases must resolve to same canonical id — not invent parallel facts
        assertThat(registry.resolve("cibil score").orElseThrow().id()).isEqualTo("bureau.score");
        assertThat(registry.resolve("maximum dpd 6 months").orElseThrow().id()).isEqualTo("bureau.max_dpd_6m");
        assertThat(registry.resolve("adb").orElseThrow().id()).isEqualTo("banking.avg_daily_balance_3m");
    }

    @Test
    void statusHonesty_definedNotImplementedVsProductionReady() {
        CanonicalParameterDefinition defined = registry.findById("bureau.dpd_30_plus_count_6m").orElseThrow();
        assertThat(defined.capability().derivationDefined()).isTrue();
        assertThat(defined.capability().implemented()).isTrue();
        assertThat(defined.capability().productionReady()).isFalse();
        assertThat(defined.existingImplementationBinding()).contains("BureauMetricService");

        CanonicalParameterDefinition live = registry.findById("bureau.live_unsecured_loan_count").orElseThrow();
        assertThat(live.capability().implemented()).isTrue();
        assertThat(live.capability().productionReady()).isFalse();

        CanonicalParameterDefinition thin = registry.findById("bureau.thin_file_indicator").orElseThrow();
        assertThat(thin.capability().implemented()).isFalse();
        assertThat(thin.capability().missingDataTreatment()).contains("BUSINESS_DEFINITION_REQUIRED");

        CanonicalParameterDefinition restructured = registry.findById("bureau.restructured_account_count").orElseThrow();
        assertThat(restructured.capability().implemented()).isFalse();
        assertThat(restructured.capability().missingDataTreatment()).contains("SOURCE_NOT_PROVEN");
    }

    @Test
    void searchAcrossNameAliasIdSource() {
        Map<String, Object> dpd = registry.search("dpd");
        assertThat((int) dpd.get("count")).isGreaterThan(0);
        Map<String, Object> gst = registry.search("gst turnover");
        assertThat((int) gst.get("count")).isGreaterThan(0);
        Map<String, Object> foir = registry.search("foir");
        assertThat((int) foir.get("count")).isGreaterThan(0);
    }

    @Test
    void adminAndResolverShareSameCanonicalId() {
        Map<String, Object> detail = admin.parameterDetail("bureau.max_dpd_6m");
        assertThat(detail.get("found")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) detail.get("parameter");
        assertThat(param.get("id")).isEqualTo("bureau.max_dpd_6m");
        assertThat(registry.resolve("max dpd").orElseThrow().id()).isEqualTo("bureau.max_dpd_6m");
        assertThat(detail.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void overviewExposesSourceCountsAndAuthorityFalse() {
        Map<String, Object> overview = admin.overview();
        assertThat(overview.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(overview.get("inventoryVersion")).isEqualTo("GACAT-SOURCE-CATALOGUE-RECOVERY-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) overview.get("totals");
        assertThat((int) totals.get("registryCount")).isGreaterThan(28);
        assertThat((int) totals.get("beforeExpansionBaseline")).isEqualTo(28);
    }

    @Test
    void catalogueViewAuthorityRemainsFalse() {
        assertThat(registry.catalogueView().get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(registry.catalogueView().get("readModelOnly")).isEqualTo(true);
    }
}
