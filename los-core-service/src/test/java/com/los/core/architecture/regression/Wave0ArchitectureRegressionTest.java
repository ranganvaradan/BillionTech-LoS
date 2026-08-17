package com.los.core.architecture.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorDataState;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorInput;
import com.los.core.service.underwriting.ScorecardSafetyScoring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLATFORM-CONVERGENCE-WAVE-0 — durable regression baseline harness.
 * Does not change business behaviour; records CURRENT reality including gaps.
 */
class Wave0ArchitectureRegressionTest {

    private Wave0SpineBaselineHarness spineHarness;
    private Wave0PolicyBaselineHarness policyHarness;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        spineHarness = new Wave0SpineBaselineHarness();
        policyHarness = new Wave0PolicyBaselineHarness();
        mapper = Wave0GoldenDatasets.mapper().copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Test
    void freezeManifest_presentAndMatchesHeadAssumption() throws Exception {
        var freeze = Wave0GoldenDatasets.loadResource("WAVE0_FREEZE.json");
        assertThat(freeze.path("SOURCE_START_SHA").asText())
                .isEqualTo("3da1ad4316eeda581975da41e6a23b2a258d4029");
        assertThat(freeze.path("SAME_ARTIFACT_INTERNAL_CLIENT").asBoolean()).isTrue();
        assertThat(freeze.path("DB_MIGRATION_WAVE0").asText()).isEqualTo("NONE");
        assertThat(freeze.path("GACAT_MUTATED").asBoolean()).isFalse();
        assertThat(freeze.path("VIKASAM_MUTATED").asBoolean()).isFalse();
        assertThat(freeze.path("BUSINESS_BEHAVIOUR_INTENTIONALLY_CHANGED").asBoolean()).isFalse();
    }

    @Test
    void goldenDatasets_coverRequiredFamilies() {
        assertThat(Wave0GoldenDatasets.loadResource("datasets/bureau-retail-golden.json")
                .path("paymentHistory").size()).isGreaterThanOrEqualTo(12);
        assertThat(Wave0GoldenDatasets.loadResource("datasets/banking-golden.json")
                .path("transactions").isArray()).isTrue();
        assertThat(Wave0GoldenDatasets.loadResource("datasets/gst-golden.json")
                .path("periods").isArray()).isTrue();
        assertThat(Wave0GoldenDatasets.loadResource("datasets/itr-tax-golden.json")
                .path("facts").has("itr.income.total")).isTrue();
        assertThat(Wave0GoldenDatasets.loadResource("datasets/kyc-golden.json")
                .path("cases").size()).isEqualTo(3);
        assertThat(Wave0GoldenDatasets.loadResource("datasets/application-golden.json")
                .path("facts").has("application.requested_amount")).isTrue();
    }

    @Test
    void gacatCatalogue_matchesSeedInventory() {
        assertThat(CanonicalParameterRegistry.fromSeedForTestsOnly().all())
                .hasSize(com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed.all().size());
    }

    @Test
    void executionSpine_policyTest_capturesMustPreserveAndGaps() throws Exception {
        List<Map<String, Object>> rows =
                spineHarness.captureExecutionBaseline(EvaluationMode.POLICY_TEST);
        writeTarget("execution-spine-policy-test.json", rows);

        Map<String, Map<String, Object>> byId = indexByCanonicalId(rows);

        // MUST_PRESERVE: score available from RAW fact
        assertThat(byId.get("bureau.score").get("status")).isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(((Number) byId.get("bureau.score").get("value")).intValue()).isEqualTo(710);

        // MUST_PRESERVE: BuiltIn max_dpd with exact ID
        assertThat(byId.get("bureau.max_dpd_6m").get("status")).isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(byId.get("bureau.max_dpd_6m").get("capability")).isEqualTo(true);

        // MUST_PRESERVE (with PH in context): authored clean_history
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").get("status"))
                .isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").get("producerType"))
                .isEqualTo("AUTHORED_DERIVED");

        // KNOWN_GAP: unsupported overdue family
        assertThat(byId.get("bureau.cc_overdue_amount").get("status"))
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE.name());
        assertThat(byId.get("bureau.overdue.amount").get("status"))
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE.name());
        assertThat(byId.get("bureau.overdue.age_months").get("status"))
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE.name());

        // KNOWN_GAP: unit baseline invalid dpd_30 def → not capable
        assertThat(byId.get("bureau.dpd_30_plus_count_6m").get("capability")).isEqualTo(false);
    }

    @Test
    void executionSpine_modes_shareCapabilityShape() throws Exception {
        for (EvaluationMode mode : EvaluationMode.values()) {
            List<Map<String, Object>> rows = spineHarness.captureExecutionBaseline(mode);
            writeTarget("execution-spine-" + mode.name().toLowerCase() + ".json", rows);
            assertThat(rows).isNotEmpty();
        }
    }

    @Test
    void policyStudioGoldens_andDisagreementDocumented() throws Exception {
        List<Map<String, Object>> rows = policyHarness.captureStudioPolicyGoldens(spineHarness.spine());
        writeTarget("policy-studio-goldens.json", rows);

        Map<String, Object> raw = rows.stream()
                .filter(r -> "RAW_score_gte_650".equals(r.get("caseId"))).findFirst().orElseThrow();
        assertThat(raw.get("outcome")).isEqualTo("PASS");

        Map<String, Object> unsupported = rows.stream()
                .filter(r -> "UNSUPPORTED_cc_overdue_missing".equals(r.get("caseId"))).findFirst().orElseThrow();
        assertThat(unsupported.get("outcome")).isEqualTo("DATA_INSUFFICIENT");

        Map<String, Object> disagreement = rows.stream()
                .filter(r -> "PT_VS_FROZEN_DISAGREEMENT_DOCUMENTED".equals(r.get("caseId"))).findFirst()
                .orElseThrow();
        assertThat(disagreement.get("classification")).isEqualTo(Wave0Classification.KNOWN_GAP.name());
        assertThat(disagreement.get("asOfDisagreementVisible") == null
                || Boolean.TRUE.equals(rows.get(0).get("asOfDisagreementVisible"))).isTrue();
    }

    @Test
    void scorecardBaseline_deterministicFactors() throws Exception {
        Set<String> policyParams = Set.of("bureau.score", "bureau.max_dpd_6m", "application.requested_amount");
        List<FactorInput> factors = List.of(
                new FactorInput("bureau.score", new BigDecimal("50"), true, FactorDataState.PRESENT,
                        new BigDecimal("80"), new BigDecimal("100")),
                new FactorInput("bureau.max_dpd_6m", new BigDecimal("30"), true, FactorDataState.PRESENT,
                        new BigDecimal("40"), new BigDecimal("100")),
                new FactorInput("application.requested_amount", new BigDecimal("20"), true, FactorDataState.PRESENT,
                        new BigDecimal("60"), new BigDecimal("100"))
        );
        PolicyWeightedScorecardEngine.assertFactorsSubsetOfPolicy(policyParams,
                factors.stream().map(FactorInput::canonicalParameterId).toList());

        var result = PolicyWeightedScorecardEngine.score(factors);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("canonicalFactorIds", policyParams);
        baseline.put("resolvedFactorValues", Map.of(
                "bureau.score", 710,
                "bureau.max_dpd_6m", 90,
                "application.requested_amount", 500000));
        baseline.put("valueAuthority", "Wave0_fixture_values_as_if_from_CanonicalParameterExecutionService");
        baseline.put("rawWeights", Map.of(
                "bureau.score", 50,
                "bureau.max_dpd_6m", 30,
                "application.requested_amount", 20));
        baseline.put("engineMode", PolicyWeightedScorecardEngine.MODE);
        baseline.put("outcome", result.outcome());
        baseline.put("weightedScore", result.weightedScore());
        baseline.put("dataInsufficient", result.dataInsufficient());
        baseline.put("underwritingOutcomeSeparate", "NOT_EXECUTED_FROZEN_IN_UNIT_HARNESS");
        baseline.put("ptVsFrozenDisagreement", Wave0Classification.KNOWN_GAP.name());
        baseline.put("legacyScorecardModeMarker", ScorecardSafetyScoring.MISSING_REQUIRED);
        baseline.put("classification", Wave0Classification.MUST_PRESERVE.name()
                + "_weighted_v2_subset|" + Wave0Classification.KNOWN_GAP.name()
                + "_full_live_scorecard_config");

        writeTarget("scorecard-baseline.json", baseline);
        assertThat(result.dataInsufficient()).isFalse();
        assertThat(result.weightedScore()).isNotNull();
    }

    @Test
    void w6Baseline_sourceAcquiredNotEqualParameterAvailable() throws Exception {
        Map<String, Object> w6 = spineHarness.captureW6AcquisitionVsExecutable();
        writeTarget("w6-baseline.json", w6);
        assertThat(w6.get("principle")).isEqualTo("SOURCE_ACQUIRED != PARAMETER_AVAILABLE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) w6.get("cases");
        Map<String, Object> unsupported = cases.stream()
                .filter(c -> "source_acquired_parameter_not_executable".equals(c.get("caseId")))
                .findFirst().orElseThrow();
        assertThat(unsupported.get("sourceAcquiredClaim")).isEqualTo(true);
        assertThat(unsupported.get("valueAvailable")).isEqualTo(false);
    }

    @Test
    void temporalAndLegacyInventories_present() {
        assertThat(Wave0GoldenDatasets.loadResource("clocks/asof-baseline.json")
                .path("clocks").isArray()).isTrue();
        assertThat(Wave0GoldenDatasets.loadResource("legacy/parallel-paths.json")
                .path("paths").isArray()).isTrue();
        assertThat(Wave0GoldenDatasets.loadResource("classifications/assertion-classes.json")
                .path("classes").has("MUST_PRESERVE")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> indexByCanonicalId(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("canonicalParameterId");
            if (id != null) {
                out.put(String.valueOf(id), row);
            }
        }
        return out;
    }

    private void writeTarget(String name, Object payload) throws Exception {
        Path dir = Path.of("target", "architecture-regression");
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        mapper.writeValue(file.toFile(), payload);
    }
}
