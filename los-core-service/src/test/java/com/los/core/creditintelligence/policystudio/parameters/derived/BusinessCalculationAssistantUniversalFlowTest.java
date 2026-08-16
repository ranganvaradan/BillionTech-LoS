package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCalculationAssistantUniversalFlowTest {

    @Test
    void targetSemanticsSuppressUnnecessaryClarificationFor30PlusCount() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.dpd_30_plus_count_6m").orElseThrow();
        var sem = BusinessCalculationAssistant.extractTargetSemantics(target, "", Map.of());
        assertEquals("COUNT_DPD_MONTHS", sem.intent());
        assertEquals(30, sem.dpdThreshold());
        assertEquals(6, sem.trailingMonths());
        assertEquals("GTE", sem.dpdMatchOp());
    }

    @Test
    void dpd30PlusYieldsCanCalculateWithoutClarification() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.dpd_30_plus_count_6m").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE, r.businessOutcome());
        assertTrue(r.clarificationQuestions() == null || r.clarificationQuestions().isEmpty());
        assertNotNull(r.proposedExpression());
        assertEquals("COUNT_PERIODS_MATCHING", r.proposedExpression().get("op"));
        assertEquals(30, ((Number) r.proposedExpression().get("matchValue")).intValue());
        assertEquals(6, ((Number) r.proposedExpression().get("windowMonths")).intValue());
        assertTrue(r.maxDpdProxyRejected());
        assertFalse(r.humanExplanation().toLowerCase().contains("any dpd above 0"));
    }

    @Test
    void maxDpdNotUsedAsProxyForMonthCount() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.dpd_30_plus_count_6m").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("max_dpd")));
        Set<String> deps = SafeDerivedExpressionEvaluator.collectDependencies(r.proposedExpression());
        assertFalse(deps.stream().anyMatch(BusinessCalculationAssistant::isMaxDpdProxyId));
    }

    @Test
    void countPeriodsMatchingEvaluator() {
        Map<String, Object> expr = BusinessCalculationAssistant.countPeriodsMatchingExpression(
                "bureau.tradeline.payment_history", "GTE", 30, 6);
        List<Map<String, Object>> history = List.of(
                Map.of("month", "2026-01", "dpd", 0),
                Map.of("month", "2026-02", "dpd", 35),
                Map.of("month", "2026-03", "dpd", 40),
                Map.of("month", "2026-03", "dpd", 60), // same month — distinct
                Map.of("month", "2025-10", "dpd", 90) // outside window for asOf 2026-07
        );
        var result = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(
                "bureau.tradeline.payment_history", history,
                SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-07-15"));
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_OK, result.status());
        // window Jul-2 months back: Feb..Jul → Feb and Mar match = 2 distinct
        assertEquals(2L, ((Number) result.value()).longValue());
    }

    @Test
    void ccOverdueShowsKnownExistingWithoutWorkItOut() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.cc_overdue_amount").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_MISSING_DATA, r.businessOutcome());
        assertFalse(r.knownExistingCalculation());
        assertEquals(BusinessCalculationAssistant.KIND_CONFIRM_EXISTING, r.proposalKind());
        assertNull(r.proposedExpression());
        assertTrue(r.humanExplanation().toLowerCase().contains("filter")
                || r.humanExplanation().toLowerCase().contains("executable")
                || r.humanExplanation().toLowerCase().contains("credit-card"));
    }

    @Test
    void incompatibleRedefinitionTriggersConflict() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.cc_overdue_amount").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target,
                PolicyStudioConvergencePresenter.registry(),
                "Include personal loans and business loans also",
                Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_SEMANTIC_CONFLICT, r.businessOutcome());
        assertFalse(r.conflictChoices().isEmpty());
    }

    @Test
    void cleanHistoryStillNeedsClarificationWithoutThreshold() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.credit_after_overdue.clean_history_months").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_NEEDS_CLARIFICATION, r.businessOutcome());
    }

    @Test
    void cleanHistoryWithDpdGt0StillCanCalculate() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.credit_after_overdue.clean_history_months").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target,
                PolicyStudioConvergencePresenter.registry(),
                "find the last date one or more accounts were overdue, ie DPD > 0",
                Map.of()).orElseThrow();
        assertEquals(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE, r.businessOutcome());
        assertEquals("MONTHS_SINCE_LAST_MATCH", r.proposedExpression().get("op"));
    }

    @Test
    void plusThresholdAndSixMonthParsing() {
        assertEquals(30, BusinessCalculationAssistant.thresholdFromText("Count of 30+ DPD months (6m)"));
        assertEquals(6, BusinessCalculationAssistant.windowFromText("Count of 30+ DPD months (6m)"));
        assertEquals(30, BusinessCalculationAssistant.thresholdFromText("dpd≥30 in trailing 6m"));
    }
}
