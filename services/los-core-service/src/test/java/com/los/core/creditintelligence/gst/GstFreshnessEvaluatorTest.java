package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.gst.domain.GstFreshnessPolicy;
import com.los.core.creditintelligence.gst.util.GstFreshnessEvaluator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class GstFreshnessEvaluatorTest {

    @Test
    void freshWhenActualMeetsExpectedAfterLag() {
        var result = GstFreshnessEvaluator.evaluate(
                LocalDate.of(2026, 6, 25), YearMonth.of(2026, 5), 20);
        assertThat(result.fresh()).isTrue();
        assertThat(result.expectedLatestPeriod()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(result.policyVersion()).isEqualTo(GstFreshnessPolicy.GST_FRESHNESS_POLICY_V1);
    }

    @Test
    void staleWhenActualBehindExpected() {
        var result = GstFreshnessEvaluator.evaluate(
                LocalDate.of(2026, 6, 25), YearMonth.of(2026, 3), 20);
        assertThat(result.fresh()).isFalse();
        assertThat(result.evidence()).containsEntry("reason", "STALE_VS_EXPECTED");
    }

    @Test
    void missingActualIsNotFresh() {
        var result = GstFreshnessEvaluator.evaluate(LocalDate.of(2026, 6, 25), null, 20);
        assertThat(result.fresh()).isFalse();
        assertThat(result.evidence()).containsEntry("reason", "NO_FILED_PERIOD");
    }
}
