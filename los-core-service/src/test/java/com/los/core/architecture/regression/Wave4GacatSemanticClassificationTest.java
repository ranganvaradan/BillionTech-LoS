package com.los.core.architecture.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedSemanticValidation;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatCollectionElementContracts;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticEntry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-4 — GACAT semantic classification goldens. No ID churn. No CPES capability change.
 */
class Wave4GacatSemanticClassificationTest {

    private static final List<String> VIKASAM_13 = List.of(
            "bureau.score",
            "bureau.recent_inquiries_90d",
            "bureau.settled_account_count",
            "bureau.written_off_account_count",
            "bureau.accounts.cc_writeoff",
            "bureau.accounts.writeoff_non_cc",
            "bureau.tradeline.suit_filed",
            "bureau.credit_after_overdue.clean_history_months",
            "bureau.dpd_30_plus_count_6m",
            "bureau.cc_overdue_amount",
            "bureau.overdue.amount",
            "bureau.overdue.age_months",
            "bureau.max_dpd_6m"
    );

    private GacatSemanticRegistry registry;
    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        registry = GacatSemanticRegistry.loadFromSeed();
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> java.util.Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void all169Classified_noSilentUnknownClass() throws Exception {
        assertThat(GacatCatalogueSeed.all()).hasSize(169);
        assertThat(registry.size()).isEqualTo(169);
        assertThat(registry.unknownSemanticCount()).isEqualTo(0);

        ObjectMapper mapper = Wave0GoldenDatasets.mapper().copy().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> artifact = registry.snapshotArtifact();
        Path out = Path.of("target", "architecture-regression", "gacat-169-semantic-classification.json");
        Files.createDirectories(out.getParent());
        mapper.writeValue(out.toFile(), artifact);

        Path committed = Path.of("src", "test", "resources", "architecture-regression", "baselines",
                "gacat-169-semantic-classification.json");
        Files.createDirectories(committed.getParent());
        mapper.writeValue(committed.toFile(), artifact);

        assertThat(artifact.get("gacatTotal")).isEqualTo(169);
        assertThat(artifact.get("semanticVersion")).isEqualTo(GacatSemanticTaxonomy.SEMANTIC_VERSION);
        assertThat(artifact.get("canonicalIdsChanged")).isEqualTo(0);
    }

    @Test
    void capabilityCountsUnchanged_wave4MetadataOnly() {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snap = harness.captureCapabilitySnapshot();
        assertThat(snap.get("policyTestCapableCount")).isEqualTo(67);
        assertThat(snap.get("w6CapableCount")).isEqualTo(57);
        assertThat(snap.get("underwritingCapableCount")).isEqualTo(67);
    }

    @Test
    void legacyImplementedFlagAlone_doesNotChangeCpesCapability() {
        CanonicalParameterDefinition base = GacatCatalogueSeed.all().stream()
                .filter(d -> "bureau.cc_overdue_amount".equals(d.id()))
                .findFirst()
                .orElseThrow();
        EvaluationContext ctx = EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build();

        boolean before = spine.hasExecutionCapability(base.id(), ctx);
        assertThat(before).isFalse();

        CanonicalParameterDefinition.Capability flipped = CanonicalParameterDefinition.Capability.of(
                base.capability().schema(),
                base.capability().sourceAvailable(),
                base.capability().normalized(),
                base.capability().derivationDefined(),
                true,  // implemented flipped on
                true,  // productionReady flipped on
                base.capability().cardinality(),
                base.capability().providerFieldPath(),
                base.capability().missingDataTreatment(),
                base.capability().filtersEligibility(),
                base.capability().transformation(),
                base.capability().aggregation());
        CanonicalParameterDefinition mutated = new CanonicalParameterDefinition(
                base.id(), base.businessName(), base.evaluatedFrom(), base.type(), base.unit(), base.period(),
                base.availability(), base.calculationSummary(), base.requiredPrimitives(),
                base.existingImplementationBinding(), base.aliases(),
                base.liveRuleParameter(), base.liveScorecardParameter(), flipped);

        // Catalogue flags changed — spine capability must ignore them
        assertThat(mutated.capability().implemented()).isTrue();
        assertThat(mutated.capability().productionReady()).isTrue();
        assertThat(spine.hasExecutionCapability(mutated.id(), ctx)).isFalse();
        assertThat(spine.resolveAndExecute(mutated.id(), ctx).status())
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE);
    }

    @Test
    void collectionContracts_presentForWave3BureauCollections() {
        List<Map<String, Object>> contracts = GacatCollectionElementContracts.allContracts();
        assertThat(contracts).extracting(c -> c.get("collectionId"))
                .contains(
                        "bureau.tradelines",
                        "bureau.tradeline.payment_history",
                        "bureau.inquiries");
        Map<String, String> phSchema = GacatCollectionElementContracts.rowSchemaFor(
                "bureau.tradeline.payment_history");
        assertThat(phSchema).containsKeys("month", "dpd");
    }

    @Test
    void safeDerived_rejectsFilterOnScalar_allowsSumOnMoneyCollection() {
        Map<String, Object> bad = Map.of(
                "op", "FILTER",
                "from", Map.of("op", "REF", "id", "bureau.score"),
                "where", Map.of("op", "CONST", "value", true));
        assertThat(SafeDerivedSemanticValidation.validate(bad, registry))
                .anyMatch(e -> e.contains("SCALAR") && e.contains("bureau.score"));

        Map<String, Object> good = Map.of(
                "op", "SUM",
                "of", Map.of(
                        "op", "PROJECT",
                        "field", "overdue_amount",
                        "from", Map.of(
                                "op", "FILTER",
                                "from", Map.of("op", "REF", "id", "bureau.tradelines"),
                                "where", Map.of(
                                        "op", "EQ",
                                        "left", Map.of("op", "FIELD", "field", "account_type"),
                                        "right", Map.of("op", "CONST", "value", "CREDIT_CARD")))));
        assertThat(SafeDerivedSemanticValidation.validate(good, registry)).isEmpty();

        Map<String, Object> boolSum = Map.of(
                "op", "SUM",
                "of", Map.of(
                        "op", "PROJECT",
                        "field", "suit_filed",
                        "from", Map.of("op", "REF", "id", "bureau.tradelines")));
        assertThat(SafeDerivedSemanticValidation.validate(boolSum, registry))
                .anyMatch(e -> e.contains("BOOLEAN"));
    }

    @Test
    void vikasam13_semanticClassification() {
        Map<String, GacatSemanticEntry> byId = registry.all().stream()
                .collect(java.util.stream.Collectors.toMap(GacatSemanticEntry::canonicalId, e -> e));
        assertThat(byId.get("bureau.score").parameterClass())
                .isEqualTo(GacatSemanticTaxonomy.ParameterClass.BUSINESS_PARAMETER);
        assertThat(byId.get("bureau.score").calculationMode())
                .isEqualTo(GacatSemanticTaxonomy.CalculationMode.RAW);
        assertThat(byId.get("bureau.score").cardinality())
                .isEqualTo(GacatSemanticTaxonomy.Cardinality.SCALAR);

        assertThat(byId.get("bureau.max_dpd_6m").calculationMode())
                .isEqualTo(GacatSemanticTaxonomy.CalculationMode.BUILT_IN);
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").calculationMode())
                .isEqualTo(GacatSemanticTaxonomy.CalculationMode.AUTHORED);
        assertThat(byId.get("bureau.dpd_30_plus_count_6m").calculationMode())
                .isEqualTo(GacatSemanticTaxonomy.CalculationMode.AUTHORED);
        assertThat(byId.get("bureau.cc_overdue_amount").calculationMode())
                .isEqualTo(GacatSemanticTaxonomy.CalculationMode.AUTHORED);
        assertThat(byId.get("bureau.cc_overdue_amount").parameterClass())
                .isEqualTo(GacatSemanticTaxonomy.ParameterClass.BUSINESS_PARAMETER);

        // payment_history is ingredient HISTORY (not in Vikasam-13 list as operand, but catalogue ID)
        assertThat(byId.get("bureau.tradeline.payment_history").parameterClass())
                .isEqualTo(GacatSemanticTaxonomy.ParameterClass.INGREDIENT);
        assertThat(byId.get("bureau.tradeline.payment_history").cardinality())
                .isEqualTo(GacatSemanticTaxonomy.Cardinality.HISTORY);

        for (String id : VIKASAM_13) {
            assertThat(byId).containsKey(id);
        }
    }

    @Test
    void noCanonicalIdChurn_seedIdsStable() {
        Set<String> seedIds = GacatCatalogueSeed.all().stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> classified = registry.all().stream()
                .map(GacatSemanticEntry::canonicalId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(classified).isEqualTo(seedIds);
    }

    @Test
    void businessView_exposesParameterClassWithoutChangingExecutionLabels() {
        CanonicalParameterDefinition score = GacatCatalogueSeed.all().stream()
                .filter(d -> "bureau.score".equals(d.id()))
                .findFirst().orElseThrow();
        Map<String, Object> view = score.toBusinessView();
        assertThat(view.get("parameterClass")).isEqualTo("BUSINESS_PARAMETER");
        assertThat(view.get("semantic")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> sem = (Map<String, Object>) view.get("semantic");
        assertThat(sem.get("calculationModeDoesNotImplyCapability")).isEqualTo(true);
        assertThat(sem.get("legacyFlagsAuthority")).isEqualTo("LEGACY_EXECUTION_METADATA");
        assertThat(view).containsKey("executionState");
    }
}
