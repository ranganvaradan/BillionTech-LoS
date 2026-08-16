package com.los.core.architecture.regression;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Policy DSL goldens for Wave 0 — Studio path (authoritative for studio today).
 * Frozen/live and Graph-without-spine disagreements are recorded explicitly.
 */
public final class Wave0PolicyBaselineHarness {

    private final PolicyDslInterpreterV1 dsl = new PolicyDslInterpreterV1();
    private final FixedEvaluationClock clock = FixedEvaluationClock.atLocalNoon(
            Wave0GoldenDatasets.DSL_CLOCK_DATE, ZoneId.of("Asia/Kolkata"));

    public List<Map<String, Object>> captureStudioPolicyGoldens(CanonicalParameterExecutionService spine) {
        EvaluationContext spineCtx = Wave0GoldenDatasets.fullGoldenContext(EvaluationMode.POLICY_TEST);
        Map<String, Object> metrics = resolveMetrics(spine, spineCtx);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(eval("RAW_score_gte_650",
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("BUILT_IN_max_dpd_gt_30",
                PolicyDsl.gt(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30)),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("AUTHORED_clean_history_gte_6",
                PolicyDsl.gte(PolicyDsl.metric("bureau.credit_after_overdue.clean_history_months"),
                        Map.of("const", 6)),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("UNSUPPORTED_cc_overdue_missing",
                PolicyDsl.gt(PolicyDsl.metric("bureau.cc_overdue_amount"), Map.of("const", 5000)),
                metrics, Wave0Classification.KNOWN_GAP));
        rows.add(eval("AND_score_and_max_dpd",
                PolicyDsl.and(
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                        PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 90))),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("OR_score_or_ntc",
                PolicyDsl.or(
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                        PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), Map.of("const", true))),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("NOT_ntc",
                PolicyDsl.not(PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), Map.of("const", true))),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("IN_score_band",
                PolicyDsl.in(PolicyDsl.metric("bureau.score"), List.of(700, 710, 720)),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("BETWEEN_score",
                Map.of(
                        "op", "BETWEEN",
                        "left", PolicyDsl.metric("bureau.score"),
                        "min", Map.of("const", 650),
                        "max", Map.of("const", 800)),
                metrics, Wave0Classification.MUST_PRESERVE));
        rows.add(eval("MISSING_dependency_clean_without_ph",
                PolicyDsl.gte(PolicyDsl.metric("bureau.credit_after_overdue.clean_history_months"),
                        Map.of("const", 6)),
                resolveMetrics(spine, Wave0GoldenDatasets.contextWithoutPaymentHistory(EvaluationMode.POLICY_TEST)),
                Wave0Classification.KNOWN_GAP));

        // Explicit disagreement documentation (not reconciled)
        Map<String, Object> disagreement = new LinkedHashMap<>();
        disagreement.put("caseId", "PT_VS_FROZEN_DISAGREEMENT_DOCUMENTED");
        disagreement.put("studioPath", "PolicyDslInterpreterV1");
        disagreement.put("frozenPath", "FrozenUnderwritingRuleEngine");
        disagreement.put("graphPath", "PolicyGraphPolicyTestService (caller metrics, no spine)");
        disagreement.put("shadowPath", "ShadowPolicyEngine (frozen maps)");
        disagreement.put("studioOutcomeSample", rows.get(0).get("outcome"));
        disagreement.put("frozenOutcome", "NOT_EXECUTED_IN_UNIT_HARNESS");
        disagreement.put("classification", Wave0Classification.KNOWN_GAP.name());
        disagreement.put("expectedToChangeInWaves", List.of("WAVE_5", "WAVE_6"));
        disagreement.put("note", "Wave 0 records dual engines exist; does not assert Frozen equals Studio");
        rows.add(disagreement);

        return rows;
    }

    private Map<String, Object> resolveMetrics(
            CanonicalParameterExecutionService spine, EvaluationContext ctx) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String id : List.of(
                "bureau.score",
                "bureau.max_dpd_6m",
                "bureau.status_ntc",
                "bureau.credit_after_overdue.clean_history_months",
                "bureau.cc_overdue_amount",
                "application.requested_amount")) {
            ExecutionResult r = spine.resolveAndExecute(id, ctx);
            if (r.status() == ExecutionStatus.VALUE_AVAILABLE && r.value() != null) {
                metrics.put(id, r.value());
            }
            // Intentionally do NOT inject unsupported overlays — honesty for Wave 0
        }
        // facts map for NTC
        if (ctx.facts().get("bureau.status_ntc") != null) {
            metrics.put("bureau.status_ntc", ctx.facts().get("bureau.status_ntc"));
        }
        return metrics;
    }

    private Map<String, Object> eval(
            String caseId,
            Map<String, Object> expr,
            Map<String, Object> metrics,
            Wave0Classification classification) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (metrics.containsKey("bureau.status_ntc")) {
            facts.put("bureau.status_ntc", metrics.get("bureau.status_ntc"));
        }
        String outcome = dsl.evaluate(expr, PolicyDslInterpreterV1.EvaluationContext.of(metrics, facts, clock));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("path", "STUDIO_POLICY_DSL");
        row.put("outcome", outcome);
        row.put("dslClock", Wave0GoldenDatasets.DSL_CLOCK_DATE.toString());
        row.put("spineAsOf", Wave0GoldenDatasets.POLICY_TEST_AS_OF.toString());
        row.put("asOfDisagreementVisible",
                !Wave0GoldenDatasets.DSL_CLOCK_DATE.equals(Wave0GoldenDatasets.POLICY_TEST_AS_OF));
        row.put("classification", classification.name());
        row.put("metricsKeysPresent", new ArrayList<>(metrics.keySet()));
        return row;
    }
}
