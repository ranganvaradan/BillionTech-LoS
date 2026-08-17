package com.los.core.architecture.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vikasam policy 4543e643-… — read-only regression baseline (13 operands).
 * Does not mutate the live policy document.
 */
class Wave0VikasamBaselineTest {

    @Test
    void vikasam13_baselineTable() throws Exception {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        List<Map<String, Object>> rows = harness.captureVikasam13(EvaluationMode.POLICY_TEST);

        ObjectMapper mapper = Wave0GoldenDatasets.mapper().copy().enable(SerializationFeature.INDENT_OUTPUT);
        Path out = Path.of("target", "architecture-regression", "vikasam-13-baseline.json");
        Files.createDirectories(out.getParent());
        mapper.writeValue(out.toFile(), Map.of(
                "policyId", Wave0GoldenDatasets.VIKASAM_POLICY_ID,
                "vikasamMutated", false,
                "ruleResolutionsInLocalFixture", "empty (see data/policy-studio-resolutions/...)",
                "dispositionNote", "ACCEPTED dispositions not stored in local resolution file",
                "rows", rows,
                "frozenLiveResult", "NOT_EXECUTED_IN_UNIT_HARNESS|KNOWN_GAP|EXPECTED_TO_CHANGE:WAVE_5",
                "scorecardResult", "NOT_BOUND_IN_UNIT_HARNESS|KNOWN_GAP",
                "w6RequirementResult", "SEE_w6-baseline.json"
        ));

        assertThat(rows).hasSize(13);

        Map<String, Map<String, Object>> byId = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            byId.put(String.valueOf(r.get("canonicalParameterId")), r);
        }

        assertThat(byId.get("bureau.score").get("status")).isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(byId.get("bureau.max_dpd_6m").get("status")).isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").get("status"))
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE.name());
        assertThat(byId.get("bureau.dpd_30_plus_count_6m").get("capability")).isEqualTo(true);
        assertThat(byId.get("bureau.cc_overdue_amount").get("status"))
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE.name());
        assertThat(byId.get("bureau.overdue.amount").get("status"))
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE.name());
        assertThat(byId.get("bureau.overdue.age_months").get("status"))
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE.name());
    }
}
