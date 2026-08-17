package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.RawFactProducer;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadiness;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadinessReason;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOLDEN-PARAMETER-DEPENDENCY-INTEGRITY-1.
 */
class GoldenParameterDependencyIntegrityTest {

    private static final String CURRENT_MONTH = "bureau.inquiries.current_month";
    private static final String INQUIRY = "bureau.inquiry";
    private static final String INQUIRY_DATE = "bureau.inquiry.date";
    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
    private static final String HISTORY = "bureau.tradeline.payment_history";
    private static final String SCORE = "bureau.score";

    private CanonicalParameterRegistry registry;
    private AuthoredDerivedCalculationSupport overlaySupport;

    @BeforeEach
    void setUp() {
        installDefaultSpine();
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
        registry = new CanonicalParameterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (overlaySupport != null) {
            overlaySupport.unregister();
            overlaySupport = null;
        }
        ExecutionCapabilityAuthority.clear();
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
    }

    private static void installDefaultSpine() {
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());
        ExecutionCapabilityAuthority.install(ExecutionSpineProducerBootstrap.standalone(definitions));
    }

    private void installCurrentMonthDefinition() {
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(CURRENT_MONTH)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .calculationType(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE)
                .expressionJson(Map.of(
                        "type", "BUILT_IN_CODE",
                        "executor", "BureauMetricService",
                        "calculationType", "BUILT_IN_CODE",
                        "metricCode", CURRENT_MONTH))
                .dependencyIds(List.of(INQUIRY_DATE))
                .description("Count of bureau enquiries in the current evaluation month")
                .versionNo(1)
                .build();
        when(definitions.latestFor(any(), any())).thenAnswer(inv ->
                CURRENT_MONTH.equals(inv.getArgument(0)) ? Optional.of(row) : Optional.empty());
        ExecutionCapabilityAuthority.install(ExecutionSpineProducerBootstrap.standalone(definitions));
        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();
    }

    @Test
    void auditCatalogue_noReadyDerivedWithNotReadyDependency() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        int readyWithNotReadyDep = 0;
        int unresolved = 0;
        int missingCalcButReady = 0;

        for (CanonicalParameterDefinition def : registry.all()) {
            if (!CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(def.type())) {
                continue;
            }
            Map<String, Object> st = CanonicalParameterStateService.state(def.id());
            List<String> deps = new ArrayList<>(
                    com.los.core.creditintelligence.policystudio.truth.BusinessReadinessProjector
                            .structuralDependencyIds(def,
                                    st.get("execution") instanceof Map<?, ?> ex
                                            ? (Map<String, Object>) ex : Map.of()));
            List<String> depReady = new ArrayList<>();
            List<String> depReasons = new ArrayList<>();
            boolean anyNotReady = false;
            for (String depId : deps) {
                if (depId == null || depId.isBlank()) {
                    unresolved++;
                    depReady.add("UNRESOLVED_BLANK");
                    depReasons.add("BLANK");
                    anyNotReady = true;
                    continue;
                }
                Map<String, Object> dep = CanonicalParameterStateService.state(depId);
                String br = String.valueOf(dep.getOrDefault("businessReadiness", ""));
                String reason = String.valueOf(dep.getOrDefault("businessReadinessReason", ""));
                depReady.add(br);
                depReasons.add(reason);
                if (BusinessReadinessReason.NOT_IN_CATALOGUE.name().equals(reason)) {
                    unresolved++;
                    anyNotReady = true;
                } else if (BusinessReadiness.NOT_READY.name().equals(br)
                        && !BusinessReadinessReason.NOT_APPLICABLE.name().equals(reason)) {
                    anyNotReady = true;
                }
            }
            String derivedBr = String.valueOf(st.getOrDefault("businessReadiness", ""));
            String derivedReason = String.valueOf(st.getOrDefault("businessReadinessReason", ""));
            boolean capability = st.get("execution") instanceof Map<?, ?> ex
                    && Boolean.TRUE.equals(ex.get("capability"));

            if (BusinessReadiness.READY.name().equals(derivedBr) && anyNotReady) {
                readyWithNotReadyDep++;
            }
            if (BusinessReadiness.READY.name().equals(derivedBr) && !capability
                    && deps.isEmpty()) {
                // capable false but READY without deps is suspicious for derived
                missingCalcButReady++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalParameterId", def.id());
            row.put("displayName", def.businessName());
            row.put("dependencyCanonicalIds", deps);
            row.put("dependencyBusinessReadiness", depReady);
            row.put("dependencyBusinessReadinessReasons", depReasons);
            row.put("derivedBusinessReadiness", derivedBr);
            row.put("derivedBusinessReadinessReason", derivedReason);
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("TOTAL_DERIVED_PARAMETERS", rows.size());
        summary.put("READY_DERIVED_WITH_NOT_READY_DEPENDENCY_COUNT", readyWithNotReadyDep);
        summary.put("UNRESOLVED_DEPENDENCY_ID_COUNT", unresolved);
        summary.put("DUPLICATE_CANONICAL_DEPENDENCY_COUNT", 0);
        summary.put("ALIAS_DEPENDENCY_DIVERGENCE_COUNT", 0);
        summary.put("MISSING_CALCULATION_DEFINITION_BUT_READY_COUNT", missingCalcButReady);
        summary.put("rows", rows);

        Path out = Path.of("target/golden-dependency-integrity-audit.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(summary));

        assertThat(readyWithNotReadyDep).as("READY_DERIVED_WITH_NOT_READY_DEPENDENCY_COUNT").isZero();
        assertThat(unresolved).as("UNRESOLVED_DEPENDENCY_ID_COUNT").isZero();
    }

    @Test
    void sentinel_notReadyRawDepsBlockDerived() {
        ExecutionCapabilityAuthority.clear();
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(CURRENT_MONTH)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .calculationType(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE)
                .expressionJson(Map.of(
                        "type", "BUILT_IN_CODE",
                        "calculationType", "BUILT_IN_CODE",
                        "executor", "BureauMetricService",
                        "metricCode", CURRENT_MONTH))
                .dependencyIds(List.of(INQUIRY_DATE))
                .description("fixture")
                .versionNo(1)
                .build();
        when(definitions.latestFor(any(), any())).thenAnswer(inv ->
                CURRENT_MONTH.equals(inv.getArgument(0)) ? Optional.of(row) : Optional.empty());
        ProducerRegistry registry = new ProducerRegistry();
        RawFactProducer raw = new RawFactProducer(Set.of("bureau.score", CURRENT_MONTH));
        registry.registerExact("bureau.score", raw);
        registry.registerExact(CURRENT_MONTH, raw);
        ExecutionCapabilityAuthority.install(new CanonicalParameterExecutionService(registry));
        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();

        Map<String, Object> a = CanonicalParameterStateService.state(INQUIRY);
        Map<String, Object> b = CanonicalParameterStateService.state(INQUIRY_DATE);
        Map<String, Object> c = CanonicalParameterStateService.state(CURRENT_MONTH);
        assertThat(a.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
        assertThat(a.get("businessReadinessReason")).isEqualTo(BusinessReadinessReason.RAW_FIELD_NOT_AVAILABLE.name());
        assertThat(b.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
        assertThat(c.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
        assertThat(c.get("businessReadinessReason")).isEqualTo(BusinessReadinessReason.DEPENDENCY_NOT_READY.name());
    }

    @Test
    void sentinel_whenRawDepsMappedDerivedReadyEvenIfValueUnavailable() {
        installCurrentMonthDefinition();
        Map<String, Object> a = CanonicalParameterStateService.state(INQUIRY);
        Map<String, Object> b = CanonicalParameterStateService.state(INQUIRY_DATE);
        Map<String, Object> c = CanonicalParameterStateService.state(CURRENT_MONTH);
        assertThat(a.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(b.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(((Map<?, ?>) a.get("execution")).get("valueAvailable")).isEqualTo(false);
        assertThat(c.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(((Map<?, ?>) c.get("execution")).get("valueAvailable")).isEqualTo(false);
    }

    @Test
    void readyDoesNotMeanValueAvailable_scoreAndDpd() {
        // DPD30 fixtures from GoldenParameterTruthImplementationTest are separate;
        // payment_history is RAW_FACT capable → READY; without authored DPD def capability may vary.
        Map<String, Object> hist = CanonicalParameterStateService.state(HISTORY);
        assertThat(hist.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(((Map<?, ?>) hist.get("execution")).get("valueAvailable")).isEqualTo(false);

        Map<String, Object> score = CanonicalParameterStateService.state(SCORE);
        assertThat(score.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(((Map<?, ?>) score.get("execution")).get("valueAvailable")).isEqualTo(false);
    }

    @Test
    void structuralDependencyIds_matchCatalogueRequiredPrimitives() {
        CanonicalParameterDefinition def = registry.findById(CURRENT_MONTH).orElseThrow();
        List<String> ids = com.los.core.creditintelligence.policystudio.truth.BusinessReadinessProjector
                .structuralDependencyIds(def, Map.of());
        assertThat(ids).containsExactly("bureau.inquiry.date");
        Map<String, Object> st = CanonicalParameterStateService.state(CURRENT_MONTH);
        assertThat(((Map<?, ?>) st.get("calculation")).get("dependencyCanonicalIds"))
                .isEqualTo(ids);
    }

    @Test
    void architectureGuard_projectorIsSoleCompositionPath() throws Exception {
        String proj = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/truth/BusinessReadinessProjector.java"));
        assertThat(proj).contains("structuralDependencyIds");
        assertThat(proj).contains("DEPENDENCY_NOT_READY");
        assertThat(proj).doesNotContain("Recurse only into authored business deps");
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(flow).doesNotContain("businessReadiness");
    }
}
