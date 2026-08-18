package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBankingMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ManualInputProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.ParameterProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquifaxDerivedClosureInvariantTest {

    private static final List<String> CATALOGUE_NOT_EMITTED = List.of(
            "bureau.thin_file_indicator",
            "bureau.account_sold_count");

    private AuthoredDerivedCalculationSupport overlaySupport;

    @AfterEach
    void tearDown() {
        if (overlaySupport != null) {
            overlaySupport.unregister();
            overlaySupport = null;
        }
    }

    @Test
    void emittedIdsSize_andCatalogueHonesty() {
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).hasSize(35);
        assertThat(GacatCatalogueSeed.BUREAU_RETAIL_DERIVED_TARGET_COUNT).isEqualTo(37);

        Set<String> catalogueIds = GacatCatalogueSeed.all().stream()
                .map(CanonicalParameterDefinition::id)
                .collect(Collectors.toSet());
        for (String id : BuiltInBureauMetricProducer.EMITTED_IDS) {
            assertThat(catalogueIds).as("EMITTED_ID in GacatCatalogueSeed.all(): %s", id).contains(id);
        }

        long bureauRetailDerived = GacatCatalogueSeed.all().stream()
                .filter(d -> CanonicalParameterDefinition.DERIVED.equals(d.type()))
                .filter(d -> "Bureau Retail".equals(d.evaluatedFrom()))
                .count();
        assertThat(bureauRetailDerived).isEqualTo(GacatCatalogueSeed.BUREAU_RETAIL_DERIVED_TARGET_COUNT);

        for (String id : CATALOGUE_NOT_EMITTED) {
            assertThat(catalogueIds).as("catalogue contains %s", id).contains(id);
            assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).as("%s must not be emitted", id).doesNotContain(id);
        }
    }

    @Test
    void policyBureauMetricService_stillDeprecated() {
        assertThat(PolicyBureauMetricService.class.isAnnotationPresent(Deprecated.class)).isTrue();
    }

    @Test
    void noSecondProducer_bootstrapExactIdsAreBuiltInBureau() {
        ProducerRegistry registry = new ProducerRegistry();
        ExecutionSpineProducerBootstrap.registerDefaults(registry, (id, t) -> Optional.empty());
        BuiltInBureauMetricProducer bms = new BuiltInBureauMetricProducer();
        BuiltInBankingMetricProducer banking = new BuiltInBankingMetricProducer();
        AuthoredDerivedProducer authored = new AuthoredDerivedProducer((id, t) -> Optional.empty());
        ManualInputProducer manual = new ManualInputProducer();

        for (String id : BuiltInBureauMetricProducer.EMITTED_IDS) {
            ParameterProducer found = registry.find(id).orElseThrow();
            assertThat(found.producerId()).as(id).isEqualTo(BuiltInBureauMetricProducer.PRODUCER_ID);
            assertThat(bms.claims(id)).as(id).isTrue();
            assertThat(banking.claims(id)).as(id).isFalse();
            assertThat(authored.claims(id)).as(id).isFalse();
            assertThat(manual.claims(id)).as(id).isFalse();
        }
    }

    @Test
    void latestDefinitionPresent_treatsBuiltInCodeAsPresent() {
        String id = "bureau.cc_overdue_amount";
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "BUILT_IN_CODE");
        expr.put("executor", "BureauMetricService");
        expr.put("calculationType", DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE);
        expr.put("metricCode", id);
        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(id)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .calculationType(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE)
                .expressionJson(expr)
                .description("MAX of credit-card overdue amounts (not SUM).")
                .versionNo(1)
                .metadata(Map.of("executionAuthority", "BureauMetricService",
                        "producer", "BuiltInBureauMetricProducer"))
                .build();

        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.of(row));
        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();

        Optional<Map<String, Object>> meta = AuthoredDerivedCalculationSupport.latestDefinitionPresent(id);
        assertThat(meta).isPresent();
        assertThat(meta.get().get("calculationType"))
                .isEqualTo(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE);
        assertThat(meta.get().get("executor")).isEqualTo("BureauMetricService");
        assertThat(AuthoredDerivedCalculationSupport.isBuiltInCodeDefinition(expr)).isTrue();
        assertThat(AuthoredDerivedProducer.isSpineExecutableDefinition(id, expr)).isFalse();
        assertThat(AuthoredDerivedCalculationSupport.latestExecutableHow(id).orElseThrow())
                .contains("MAX of credit-card overdue amounts");
    }
}
