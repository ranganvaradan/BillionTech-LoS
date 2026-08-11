package com.los.core.creditintelligence.policystudio.metrics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmiBounceCountCalculatorTest {

    @Test
    void stagingFixture_fiveEmiAttempts_twoBounces_countTwo() {
        List<EmiBounceCountCalculator.Txn> txns = EmiBounceCountCalculator.stagingFixture();
        Map<String, Object> eval = EmiBounceCountCalculator.evaluate(
                txns, EmiBounceCountCalculator.Config.defaults(), LocalDate.of(2026, 8, 1));
        assertThat(eval.get("outcome")).isEqualTo(EmiBounceCountCalculator.OUTCOME_PASS);
        assertThat(eval.get("emiCandidates")).isEqualTo(5);
        assertThat(eval.get("matchedBouncedEmiEvents")).isEqualTo(2);
        assertThat(eval.get("emiBounceCount")).isEqualTo(2);
        assertThat(eval.get("v")).isEqualTo(2);
        assertThat(eval.get("binding")).isEqualTo(EmiBounceCountCalculator.BINDING);
    }

    @Test
    void emptyTransactions_dataInsufficient_notZero() {
        Map<String, Object> eval = EmiBounceCountCalculator.evaluate(
                List.of(), EmiBounceCountCalculator.Config.defaults(), LocalDate.of(2026, 8, 1));
        assertThat(eval.get("outcome")).isEqualTo(EmiBounceCountCalculator.OUTCOME_DI);
        assertThat(eval.get("v")).isNull();
        assertThat(eval.get("emiBounceCount")).isNull();
    }
}
