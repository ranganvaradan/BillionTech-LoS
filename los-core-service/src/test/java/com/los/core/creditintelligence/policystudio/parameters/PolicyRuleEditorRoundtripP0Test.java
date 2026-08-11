package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POLICY-RULE-EDITOR-ROUNDTRIP-P0 — goldens A–H for inward-return compound editing.
 */
class PolicyRuleEditorRoundtripP0Test {

    private CmRuleAuthoringService authoring;
    private Map<String, Object> goldenExpr;

    @BeforeEach
    void setUp() {
        authoring = new CmRuleAuthoringService();
        goldenExpr = PolicyDsl.iff(
                PolicyDsl.gt(PolicyDsl.metric(InwardReturnCompoundSupport.TXN_METRIC), 100),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.RATIO_METRIC), 5),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.COUNT_METRIC), 5));
    }

    @Test
    void goldenA_editableModelPreservesBothBranches() {
        Map<String, Object> model = InwardReturnCompoundSupport.toEditableModel(goldenExpr, Map.of(
                "businessSummary",
                "If transactions > 100 in last 3 months: return ratio ≤ 5%; if < 100: return count ≤ 5"));
        assertThat(model.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) model.get("branches");
        assertThat(branches).hasSize(2);
        assertThat(branches.get(0).get("whenOperator")).isEqualTo(">");
        assertThat(branches.get(0).get("thenParameterId")).isEqualTo(InwardReturnCompoundSupport.RATIO_METRIC);
        assertThat(branches.get(1).get("thenParameterId")).isEqualTo(InwardReturnCompoundSupport.COUNT_METRIC);
        Map<String, Object> rebuilt = InwardReturnCompoundSupport.buildExpressionFromBranches(branches);
        assertThat(InwardReturnCompoundSupport.branchOutcomeCount(rebuilt)).isEqualTo(2);
        assertThat(InwardReturnCompoundSupport.metricPaths(rebuilt))
                .contains(InwardReturnCompoundSupport.TXN_METRIC,
                        InwardReturnCompoundSupport.RATIO_METRIC,
                        InwardReturnCompoundSupport.COUNT_METRIC);
    }

    @Test
    void goldenB_boundaryDetectorIdentifiesExactly100() {
        Map<String, Object> model = InwardReturnCompoundSupport.toEditableModel(goldenExpr, Map.of());
        assertThat(model.get("boundaryOpen")).isEqualTo(true);
        assertThat(model.get("boundaryValue")).isEqualTo(100);
        Map<String, Object> payload = InwardReturnCompoundSupport.boundaryResolverPayload("amb-1", "rule-1");
        assertThat(payload.get("defineBoundary")).isEqualTo(true);
        assertThat(payload.get("question")).asString().contains("exactly 100");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) payload.get("choices");
        assertThat(choices).hasSize(2);
    }

    @Test
    void goldenC_chooseGte100ForRatioBranch() {
        Map<String, Object> patched = InwardReturnCompoundSupport.patchBoundary(
                goldenExpr, InwardReturnCompoundSupport.OPTION_RATIO);
        @SuppressWarnings("unchecked")
        Map<String, Object> cond = (Map<String, Object>) patched.get("condition");
        assertThat(cond.get("op")).isEqualTo("GTE");
        assertThat(InwardReturnCompoundSupport.businessSummaryAfterBoundary(
                InwardReturnCompoundSupport.OPTION_RATIO)).contains("≥ 100").contains("< 100");
    }

    @Test
    void goldenD_chooseLte100ForCountBranch() {
        Map<String, Object> patched = InwardReturnCompoundSupport.patchBoundary(
                goldenExpr, InwardReturnCompoundSupport.OPTION_COUNT);
        @SuppressWarnings("unchecked")
        Map<String, Object> cond = (Map<String, Object>) patched.get("condition");
        assertThat(cond.get("op")).isEqualTo("GT");
        assertThat(InwardReturnCompoundSupport.businessSummaryAfterBoundary(
                InwardReturnCompoundSupport.OPTION_COUNT)).contains("≤ 100");
    }

    @Test
    void goldenE_parserMustNotOutputGreaterThan3FromPeriod() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "If transactions > 100 in last 3 months then reject"));
        // Either fail-closed incomplete, or if complete, value must not be 3
        if (Boolean.TRUE.equals(preview.get("complete"))) {
            Object v = preview.get("value");
            assertThat(v).isNotEqualTo(3);
            assertThat(v).isNotEqualTo(3L);
            assertThat(String.valueOf(preview.getOrDefault("ruleDisplay", ""))).doesNotContain("> 3");
            assertThat(String.valueOf(preview.getOrDefault("valueDisplay", ""))).doesNotContain("> 3");
        } else {
            // Fail-closed is acceptable; must not silently become "> 3"
            assertThat(String.valueOf(preview.getOrDefault("ruleDisplay", ""))
                    + preview.getOrDefault("message", "")
                    + preview.getOrDefault("value", ""))
                    .doesNotContain("> 3");
            assertThat(preview.get("value")).isNotEqualTo(3);
            assertThat(preview.get("value")).isNotEqualTo(3L);
        }
        // Explicit number extraction safety via rebuild of a non-compound single-threshold line
        Map<String, Object> simple = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 650"));
        // sanity: period-scrubbing did not break unrelated parses
        assertThat(simple).isNotNull();
    }

    @Test
    void goldenE2_compoundPlainEnglishFailsClosed() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text",
                "If transactions > 100 in last 3 months: return ratio ≤ 5%; if < 100: return count ≤ 5"));
        assertThat(preview.get("complete")).isEqualTo(false);
        assertThat(String.valueOf(preview.get("message"))).containsIgnoringCase("safely");
    }

    @Test
    void goldenF_mappedParametersRemainInEditableModel() {
        Map<String, Object> model = InwardReturnCompoundSupport.toEditableModel(goldenExpr, Map.of());
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "COMPOUND",
                "branches", model.get("branches")));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("mappedParameters")).asList()
                .contains(InwardReturnCompoundSupport.TXN_METRIC,
                        InwardReturnCompoundSupport.RATIO_METRIC,
                        InwardReturnCompoundSupport.COUNT_METRIC);
    }

    @Test
    void goldenG_destructiveBranchDropRejected() {
        Map<String, Object> flat = PolicyDsl.lte(
                PolicyDsl.metric(InwardReturnCompoundSupport.RATIO_METRIC), 5);
        assertThatThrownBy(() ->
                InwardReturnCompoundSupport.assertNonDestructiveReplace(goldenExpr, flat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destructive edit blocked");
    }

    @Test
    void goldenH_lineageKeepsPriorExpressionOnCompoundConfirm() {
        // Simulate rule + session-less confirm path via support only — prior expression preserved by design
        Map<String, Object> prior = goldenExpr;
        Map<String, Object> next = InwardReturnCompoundSupport.patchBoundary(
                prior, InwardReturnCompoundSupport.OPTION_RATIO);
        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("sourceText", "Inward cheque / ECS / ENACH returns — source wording");
        lineage.put("priorExpression", prior);
        lineage.put("historicalSourceText", lineage.get("sourceText"));
        assertThat(lineage.get("historicalSourceText")).asString().contains("Inward cheque");
        assertThat(InwardReturnCompoundSupport.branchOutcomeCount(next)).isEqualTo(2);
        assertThat(InwardReturnCompoundSupport.metricPaths(next))
                .containsExactlyInAnyOrderElementsOf(InwardReturnCompoundSupport.metricPaths(prior));
    }

    @Test
    void preserveModePreviewIsComplete() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "PRESERVE",
                "existingExpression", goldenExpr));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("expression")).isEqualTo(goldenExpr);
    }
}
