package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.util.ItrFreshnessEvaluator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ItrFreshnessEvaluatorTest {

    @Test
    void beforeDueDate_expectsPriorAy() {
        // FY 2025-26 completed Mar 2026; AY 2026-27 due Jul 31 2026; before due → expect 2025-26
        String expected = ItrFreshnessEvaluator.expectedLatestAssessmentYear(
                LocalDate.of(2026, 6, 1), 15, false);
        assertThat(expected).isEqualTo("2025-26");
    }

    @Test
    void afterDueDate_expectsLatestCompletedAy() {
        String expected = ItrFreshnessEvaluator.expectedLatestAssessmentYear(
                LocalDate.of(2026, 8, 20), 15, false);
        assertThat(expected).isEqualTo("2026-27");
    }

    @Test
    void evaluate_freshWhenActualMeetsExpected() {
        var result = ItrFreshnessEvaluator.evaluate(
                LocalDate.of(2026, 6, 1), "2025-26");
        assertThat(result.policyVersion()).isEqualTo(TaxConstants.ITR_FRESHNESS_POLICY_V1);
        assertThat(result.fresh()).isTrue();
    }

    @Test
    void evaluate_staleWhenActualBehind() {
        var result = ItrFreshnessEvaluator.evaluate(
                LocalDate.of(2026, 8, 20), "2024-25");
        assertThat(result.fresh()).isFalse();
        assertThat(result.evidence()).containsKey("reason");
    }
}
