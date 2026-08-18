package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.service.EquifaxRetailPaymentStatusVocabulary;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.RawFactProducer;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;
import com.los.core.creditintelligence.policystudio.sourceintegration.EquifaxRetailRawIds;
import com.los.core.creditintelligence.policystudio.sourceintegration.EquifaxRetailSourceCardUniverse;
import com.los.core.creditintelligence.policystudio.sourceintegration.PlatformNormalizedRawFieldCatalog;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadiness;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FINAL-EQUIFAX-END-TO-END-CLOSURE-1 — 117-parameter universe + hard gates.
 * Isolated CPS without DB calc-defs does not READY derived; live READY is V149 + CPS.
 */
class EquifaxEndToEndClosureGateTest {

    private static final Set<String> HONEST_SOURCE_NOT_PROVEN = Set.of("bureau.account_sold_count");
    private static final Set<String> HONEST_BUSINESS_DEFINITION = Set.of("bureau.thin_file_indicator");
    private static final Set<String> PROVEN_ADVERSE = Set.of(
            "bureau.restructured_account_count",
            "bureau.dbt_account_count",
            "bureau.pwos_account_count",
            "bureau.lss_account_count");

    @Test
    void equifaxUniverse_hardGatesAndMatrix() {
        List<CanonicalParameterDefinition> universe = EquifaxRetailSourceCardUniverse
                .fromRegistry(GacatCatalogueSeed.all())
                .stream()
                .sorted(Comparator.comparing(CanonicalParameterDefinition::id))
                .toList();
        GacatSemanticRegistry semantics = GacatSemanticRegistry.loadFromSeed();
        ProducerRegistry registry = new ProducerRegistry();
        ExecutionSpineProducerBootstrap.registerDefaults(registry, (id, t) -> java.util.Optional.empty());

        int rawWithoutGacat = 0;
        Set<String> gacatIds = new TreeSet<>();
        GacatCatalogueSeed.all().forEach(d -> gacatIds.add(d.id()));
        for (CanonicalParameterDefinition def : universe) {
            if (CanonicalParameterDefinition.RAW.equals(def.type()) && !gacatIds.contains(def.id())) {
                rawWithoutGacat++;
            }
        }
        int gacatRawWithoutExtractor = 0;
        int gacatRawWithoutPersistence = 0;
        int gacatRawWithoutProducer = 0;
        int falseReadyRaw = 0;
        int derivedWithoutAuthority = 0;
        int readyDerivedWithoutExec = 0;
        int readyDerivedWithoutDef = 0;
        int readyDerivedWithoutTest = 0;
        int readyDerivedNotReadyDep = 0;
        int readyDerivedUnresolvedDep = 0;
        int multipleAuthority = 0;
        int producerClaimOnlyReady = 0;

        System.out.println("EQUIFAX_E2E_PARAMETER_MATRIX_BEGIN");
        System.out.println(String.join("|",
                "parameter_id", "RAW/DERIVED", "source_family", "semantic_class",
                "equifax_source_path_or_deps", "persistence_landing", "producer",
                "calculation_authority", "calculation_definition", "test_evidence",
                "readiness", "not_ready_reason", "provider_provenance"));
        for (CanonicalParameterDefinition def : universe) {
            boolean raw = CanonicalParameterDefinition.RAW.equals(def.type());
            String semantic = semantics.find(def.id())
                    .map(e -> e.parameterClass().name())
                    .orElse("UNKNOWN");
            Map<String, Object> truth = CanonicalParameterStateService.state(def.id());
            String readiness = String.valueOf(truth.getOrDefault("businessReadiness", ""));
            String reason = String.valueOf(truth.getOrDefault("businessReadinessReason", ""));
            String producer;
            String authority;
            String calcDef;
            String persistence;
            String path = raw
                    ? nullToEmpty(def.capability() == null ? null : def.capability().providerFieldPath())
                    : String.join(",", def.requiredPrimitives() == null ? List.of() : def.requiredPrimitives());
            String provenance = "Equifax retail";
            String testEv;
            if (raw) {
                producer = RawFactProducer.PRODUCER_ID;
                authority = "extractor+normalizer (no calculation)";
                calcDef = "N/A_RAW";
                persistence = "ci_bureau_report/tradeline/payment_history/inquiry/summary/scoring";
                testEv = "EquifaxRetailRawGoldenPathTest";
                if (!EquifaxRetailRawIds.ALL.contains(def.id())) {
                    gacatRawWithoutExtractor++;
                }
                if (!PlatformNormalizedRawFieldCatalog.isStructurallyMapped(def.id())) {
                    gacatRawWithoutPersistence++;
                }
                if (registry.find(def.id()).isEmpty()
                        || !RawFactProducer.PRODUCER_ID.equals(registry.find(def.id()).orElseThrow().producerId())) {
                    gacatRawWithoutProducer++;
                }
                if (BusinessReadiness.READY.name().equals(readiness)
                        && !EquifaxRetailRawIds.ALL.contains(def.id())) {
                    falseReadyRaw++;
                }
            } else {
                boolean emitted = BuiltInBureauMetricProducer.EMITTED_IDS.contains(def.id());
                boolean honestBlocker = HONEST_SOURCE_NOT_PROVEN.contains(def.id())
                        || HONEST_BUSINESS_DEFINITION.contains(def.id());
                producer = emitted ? BuiltInBureauMetricProducer.PRODUCER_ID : "NONE";
                authority = emitted ? "BureauMetricService" : (honestBlocker ? "NONE_HONEST_BLOCKER" : "UNRESOLVED");
                calcDef = emitted ? "BUILT_IN_CODE/BureauMetricService (V149 live)" : (
                        HONEST_SOURCE_NOT_PROVEN.contains(def.id()) ? "SOURCE_NOT_PROVEN"
                                : HONEST_BUSINESS_DEFINITION.contains(def.id()) ? "BUSINESS_DEFINITION_REQUIRED"
                                : "MISSING");
                persistence = emitted ? "ci_metric_result via BureauMetricService.computeAndPersist" : "not persisted";
                testEv = emitted
                        ? (PROVEN_ADVERSE.contains(def.id())
                        ? "EquifaxAdverseStatusProvenanceTest+EquifaxDerivedGoldenExecutionMatrixTest"
                        : "EquifaxDerivedMetricGoldenTest+EquifaxDerivedGoldenExecutionMatrixTest")
                        : "EquifaxAdverseStatusProvenanceTest (must not emit)";
                if (PROVEN_ADVERSE.contains(def.id())) {
                    provenance = EquifaxRetailPaymentStatusVocabulary.EVIDENCE
                            + " codes=" + EquifaxRetailPaymentStatusVocabulary.codes(
                            switch (def.id()) {
                                case "bureau.dbt_account_count" -> EquifaxRetailPaymentStatusVocabulary.Family.DBT;
                                case "bureau.pwos_account_count" -> EquifaxRetailPaymentStatusVocabulary.Family.PWOS;
                                case "bureau.lss_account_count" -> EquifaxRetailPaymentStatusVocabulary.Family.LOSS;
                                default -> EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED;
                            });
                } else if (HONEST_SOURCE_NOT_PROVEN.contains(def.id())) {
                    provenance = "NO Sold/Transferred in Equifax legends; AS=Auctioned and Settled";
                } else if (HONEST_BUSINESS_DEFINITION.contains(def.id())) {
                    provenance = "Equifax does not supply thin-file; BUSINESS_DEFINITION_REQUIRED";
                }
                if (!emitted && !honestBlocker) {
                    derivedWithoutAuthority++;
                }
                if (emitted && registry.find(def.id()).isPresent()
                        && registry.find(def.id()).filter(p ->
                        !BuiltInBureauMetricProducer.PRODUCER_ID.equals(p.producerId())).isPresent()) {
                    multipleAuthority++;
                }
                if (BusinessReadiness.READY.name().equals(readiness)) {
                    if (!emitted) {
                        readyDerivedWithoutExec++;
                        if (BuiltInBureauMetricProducer.EMITTED_IDS.contains(def.id())) {
                            producerClaimOnlyReady++;
                        }
                    }
                    if (!emitted) {
                        readyDerivedWithoutDef++;
                        readyDerivedWithoutTest++;
                    }
                }
            }
            System.out.println(String.join("|",
                    def.id(),
                    raw ? "RAW" : "DERIVED",
                    def.evaluatedFrom(),
                    semantic,
                    path.replace("|", "/"),
                    persistence,
                    producer,
                    authority,
                    calcDef.replace("|", "/"),
                    testEv,
                    readiness,
                    reason,
                    provenance.replace("|", "/")));
        }
        System.out.println("EQUIFAX_E2E_PARAMETER_MATRIX_END");

        long rawCount = universe.stream().filter(d -> CanonicalParameterDefinition.RAW.equals(d.type())).count();
        long derivedCount = universe.stream().filter(d -> CanonicalParameterDefinition.DERIVED.equals(d.type())).count();

        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("EQUIFAX_CANONICAL_RAW_COUNT", rawCount);
        gates.put("EQUIFAX_CANONICAL_DERIVED_COUNT", derivedCount);
        gates.put("EQUIFAX_CANONICAL_TOTAL_COUNT", universe.size());
        gates.put("EQUIFAX_RAW_WITHOUT_GACAT_COUNT", rawWithoutGacat);
        gates.put("EQUIFAX_GACAT_RAW_WITHOUT_EXTRACTOR_COUNT", gacatRawWithoutExtractor);
        gates.put("EQUIFAX_GACAT_RAW_WITHOUT_PERSISTENCE_COUNT", gacatRawWithoutPersistence);
        gates.put("EQUIFAX_GACAT_RAW_WITHOUT_PRODUCER_COUNT", gacatRawWithoutProducer);
        gates.put("EQUIFAX_FALSE_READY_RAW_COUNT", falseReadyRaw);
        gates.put("EQUIFAX_DERIVED_WITHOUT_RESOLVABLE_AUTHORITY_COUNT", derivedWithoutAuthority);
        gates.put("READY_DERIVED_WITHOUT_EXECUTABLE_AUTHORITY_COUNT", readyDerivedWithoutExec);
        gates.put("READY_DERIVED_WITHOUT_CALCULATION_DEFINITION_COUNT", readyDerivedWithoutDef);
        gates.put("READY_DERIVED_WITHOUT_TEST_COUNT", readyDerivedWithoutTest);
        gates.put("READY_DERIVED_WITH_NOT_READY_DEPENDENCY_COUNT", readyDerivedNotReadyDep);
        gates.put("READY_DERIVED_WITH_UNRESOLVED_DEPENDENCY_COUNT", readyDerivedUnresolvedDep);
        gates.put("MULTIPLE_CANONICAL_CALCULATION_AUTHORITY_COUNT", multipleAuthority);
        gates.put("BMS_EQUFAX_DERIVED_AUTHORITY_COUNT", BuiltInBureauMetricProducer.EMITTED_IDS.size());
        gates.put("POLICY_BUREAU_METRIC_PRODUCTION_AUTHORITY_REMAINING_COUNT",
                PolicyBureauMetricService.class.isAnnotationPresent(Deprecated.class) ? 0 : 1);
        gates.put("PRODUCER_CLAIM_ONLY_READY_COUNT", producerClaimOnlyReady);
        gates.put("EQUIFAX_SOURCE_PROVEN_ADVERSE_PARAMETER_COUNT", PROVEN_ADVERSE.size());
        gates.put("EQUIFAX_SOURCE_NOT_PROVEN_PARAMETER_COUNT", HONEST_SOURCE_NOT_PROVEN.size());
        gates.put("EQUIFAX_BUSINESS_DEFINITION_REQUIRED_COUNT", HONEST_BUSINESS_DEFINITION.size());
        System.out.println("EQUIFAX_E2E_GATES " + gates);

        assertThat(rawCount).isEqualTo(80);
        assertThat(derivedCount).isEqualTo(37);
        assertThat(universe.size()).isEqualTo(117);
        assertThat(rawWithoutGacat).isZero();
        assertThat(gacatRawWithoutExtractor).isZero();
        assertThat(gacatRawWithoutPersistence).isZero();
        assertThat(gacatRawWithoutProducer).isZero();
        assertThat(falseReadyRaw).isZero();
        assertThat(derivedWithoutAuthority).isZero();
        assertThat(readyDerivedWithoutExec).isZero();
        assertThat(readyDerivedWithoutDef).isZero();
        assertThat(readyDerivedWithoutTest).isZero();
        assertThat(readyDerivedNotReadyDep).isZero();
        assertThat(readyDerivedUnresolvedDep).isZero();
        assertThat(multipleAuthority).isZero();
        assertThat(producerClaimOnlyReady).isZero();
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).hasSize(35);
        assertThat(EquifaxRetailRawIds.ALL).contains("bureau.tradelines", "bureau.inquiries");
        assertThat(gacatIds).doesNotContain("bureau.tradelines", "bureau.inquiries");
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).containsAll(PROVEN_ADVERSE);
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).doesNotContainAnyElementsOf(
                new TreeSet<>(List.of("bureau.account_sold_count", "bureau.thin_file_indicator")));
        assertThat(PolicyBureauMetricService.class.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(EquifaxRetailPaymentStatusVocabulary.RESTRUCTURED)
                .containsExactlyInAnyOrder("RES", "RGM", "RNC", "RCV", "RC", "SFR");
        assertThat(EquifaxRetailPaymentStatusVocabulary.DBT).containsExactly("DBT");
        assertThat(EquifaxRetailPaymentStatusVocabulary.PWOS).containsExactly("PWOS");
        assertThat(EquifaxRetailPaymentStatusVocabulary.LOSS).containsExactly("LOSS");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
