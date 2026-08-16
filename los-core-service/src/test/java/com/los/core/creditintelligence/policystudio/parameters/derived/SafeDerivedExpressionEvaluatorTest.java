package com.los.core.creditintelligence.policystudio.parameters.derived;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SafeDerivedExpressionEvaluatorTest {

    @Test
    void evaluatesArithmeticOverExactRefs() {
        Map<String, Object> expr = Map.of(
                "op", "SUB",
                "left", Map.of("op", "REF", "id", "bureau.overdue.age_months"),
                "right", Map.of("op", "CONST", "value", 1));
        var result = SafeDerivedExpressionEvaluator.evaluate(
                expr, Map.of("bureau.overdue.age_months", 6));
        assertThat(result.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_OK);
        assertThat(((Number) result.value()).doubleValue()).isEqualTo(5.0d);
    }

    @Test
    void missingInputDoesNotDefaultToZero() {
        Map<String, Object> expr = Map.of(
                "op", "ADD",
                "left", Map.of("op", "REF", "id", "bureau.max_dpd_6m"),
                "right", Map.of("op", "CONST", "value", 1));
        var result = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of());
        assertThat(result.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT);
        assertThat(result.value()).isNull();
    }

    @Test
    void validateRequiresKnownGacatIds() {
        Map<String, Object> expr = Map.of("op", "REF", "id", "bureau.score");
        assertThat(SafeDerivedExpressionEvaluator.validate(expr, Set.of("bureau.score"))).isEmpty();
        assertThat(SafeDerivedExpressionEvaluator.validate(expr, Set.of("other.id")))
                .anyMatch(e -> e.contains("bureau.score"));
    }

    @Test
    void rejectsArbitraryCodeOps() {
        Map<String, Object> expr = Map.of("op", "EVAL", "code", "1+1");
        assertThat(SafeDerivedExpressionEvaluator.validate(expr, Set.of()))
                .anyMatch(e -> e.toLowerCase().contains("unsupported"));
    }
}
