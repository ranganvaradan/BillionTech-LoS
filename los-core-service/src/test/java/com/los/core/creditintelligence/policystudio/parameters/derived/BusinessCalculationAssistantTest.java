package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCalculationAssistantTest {

    private static final String TARGET = "bureau.credit_after_overdue.clean_history_months";
    private static final String GOLDEN_DESC =
            "find the last date one or more accounts were overdue, ie DPD > 0 and check whether last date is < 6 months from current date";

    @Test
    void interpretsBusinessDescriptionAsMonthsSinceOverdue() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        Optional<BusinessCalculationAssistant.AssistantResult> r =
                BusinessCalculationAssistant.investigate(
                        target, PolicyStudioConvergencePresenter.registry(), GOLDEN_DESC, Map.of());
        assertTrue(r.isPresent());
        assertTrue(r.get().businessInterpretation().toLowerCase().contains("most recent")
                || r.get().businessInterpretation().toLowerCase().contains("dpd"));
    }

    @Test
    void goldenDescriptionYieldsCanCalculateWithDatedHistory() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), GOLDEN_DESC, Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE, r.businessOutcome());
        assertNotNull(r.proposedExpression());
        assertEquals("MONTHS_SINCE_LAST_MATCH", r.proposedExpression().get("op"));
        assertEquals(0, ((Number) r.proposedExpression().get("matchValue")).intValue());
        assertTrue(r.maxDpdProxyRejected());
        assertFalse(r.humanExplanation().toLowerCase().contains("gacat"));
        assertFalse(r.humanExplanation().toLowerCase().contains("vocabulary"));
        assertFalse(r.humanExplanation().toLowerCase().contains("typed expression"));
    }

    @Test
    void withoutThresholdAsksBusinessClarification() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_NEEDS_CLARIFICATION, r.businessOutcome());
        assertTrue(r.proposedExpression() == null);
        assertFalse(r.clarificationQuestions().isEmpty());
        assertTrue(r.clarificationQuestions().get(0).prompt().toLowerCase().contains("dpd"));
    }

    @Test
    void clarificationContinuesToCanCalculate() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target,
                PolicyStudioConvergencePresenter.registry(),
                "months since last overdue",
                Map.of("overdue_threshold", "dpd_gt_0")).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE, r.businessOutcome());
        assertNotNull(r.proposedExpression());
    }

    @Test
    void maxDpdIdsAreRejectedAsProxies() {
        assertTrue(BusinessCalculationAssistant.isMaxDpdProxyId("bureau.max_dpd_6m"));
        assertTrue(BusinessCalculationAssistant.isMaxDpdProxyId("bureau.max_dpd_12m"));
        assertTrue(BusinessCalculationAssistant.isMaxDpdProxyId("bureau.max_dpd_24m"));
        assertFalse(BusinessCalculationAssistant.isMaxDpdProxyId("bureau.tradeline.payment_history"));
    }

    @Test
    void monthsSinceLastMatchExpressionUsesEvalAsOf() {
        Map<String, Object> expr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                "bureau.tradeline.payment_history", 0);
        assertEquals("MONTHS_SINCE_LAST_MATCH", expr.get("op"));
        @SuppressWarnings("unchecked")
        Map<String, Object> asOf = (Map<String, Object>) expr.get("asOf");
        assertEquals("EVAL_AS_OF", asOf.get("op"));
        Set<String> deps = SafeDerivedExpressionEvaluator.collectDependencies(expr);
        assertEquals(Set.of("bureau.tradeline.payment_history"), deps);
        var errors = SafeDerivedExpressionEvaluator.validate(
                expr, Set.of("bureau.tradeline.payment_history"));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void evaluatorComputesMonthsSinceLastMatch() {
        Map<String, Object> expr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                "bureau.tradeline.payment_history", 0);
        List<Map<String, Object>> history = List.of(
                Map.of("month", "2025-10", "dpd", 15),
                Map.of("month", "2025-12", "dpd", 0),
                Map.of("month", "2026-01", "dpd", 5),
                Map.of("month", "2026-03", "dpd", 0));
        var result = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(
                "bureau.tradeline.payment_history", history,
                SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-07-15"));
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_OK, result.status());
        assertEquals(6L, ((Number) result.value()).longValue());
    }

    @Test
    void evaluatorRequiresEvalAsOfNotWallClock() {
        Map<String, Object> expr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                "bureau.tradeline.payment_history", 0);
        var result = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(
                "bureau.tradeline.payment_history",
                List.of(Map.of("month", "2026-01", "dpd", 10))));
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT, result.status());
    }

    @Test
    void semanticCompatibilityAcceptsConstructedMonthsExpression() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        Map<String, Object> expr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                "bureau.tradeline.payment_history", 0);
        var compat = DerivedCalculationSemanticCompatibility.assessExpression(
                target, expr, id -> PolicyStudioConvergencePresenter.registry().findById(id).orElse(null));
        assertTrue(compat.compatible(), compat.failures().toString());
    }

    @Test
    void semanticCompatibilityRejectsMaxDpdRef() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        var compat = DerivedCalculationSemanticCompatibility.assessExpression(
                target,
                Map.of("op", "REF", "id", "bureau.max_dpd_12m"),
                id -> PolicyStudioConvergencePresenter.registry().findById(id).orElse(null));
        assertFalse(compat.compatible());
    }
}
