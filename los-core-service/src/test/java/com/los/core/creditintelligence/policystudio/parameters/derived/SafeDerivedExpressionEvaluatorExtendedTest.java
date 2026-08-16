package com.los.core.creditintelligence.policystudio.parameters.derived;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeDerivedExpressionEvaluatorExtendedTest {

    @Test
    void typeMismatchUnsupportedOpRejected() {
        var errors = SafeDerivedExpressionEvaluator.validate(
                Map.of("op", "JAVA", "code", "1+1"),
                Set.of("bureau.score"));
        assertFalse(errors.isEmpty());
    }

    @Test
    void unknownRefRejected() {
        var errors = SafeDerivedExpressionEvaluator.validate(
                Map.of("op", "REF", "id", "not.a.real.param"),
                Set.of("bureau.score"));
        assertFalse(errors.isEmpty());
    }

    @Test
    void missingInputNeverDefaultsZero() {
        var r = SafeDerivedExpressionEvaluator.evaluate(
                Map.of("op", "ADD",
                        "left", Map.of("op", "REF", "id", "a"),
                        "right", Map.of("op", "CONST", "value", 1)),
                Map.of());
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT, r.status());
        assertTrue(r.value() == null);
    }
}
