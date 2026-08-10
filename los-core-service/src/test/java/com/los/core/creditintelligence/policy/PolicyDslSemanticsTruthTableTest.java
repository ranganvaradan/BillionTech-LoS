package com.los.core.creditintelligence.policy;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full AND/OR matrix for POLICY_DSL_EVALUATION_SEMANTICS_V1 including NA and ERROR.
 */
class PolicyDslSemanticsTruthTableTest {

    private PolicyDslInterpreterV1 interpreter;
    private PolicyDslInterpreterV1.EvaluationContext ctx;

    @BeforeEach
    void setUp() {
        interpreter = new PolicyDslInterpreterV1();
        ctx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
    }

    private String and(String a, String b) {
        return interpreter.evaluate(PolicyDsl.and(lit(a), lit(b)), ctx);
    }

    private String or(String a, String b) {
        return interpreter.evaluate(PolicyDsl.or(lit(a), lit(b)), ctx);
    }

    private Object lit(String outcome) {
        return switch (outcome) {
            case "PASS" -> Map.of("const", true);
            case "FAIL" -> Map.of("const", false);
            default -> Map.of("const", outcome);
        };
    }

    @ParameterizedTest
    @CsvSource({
            "PASS, PASS, PASS",
            "PASS, FAIL, FAIL",
            "FAIL, PASS, FAIL",
            "FAIL, FAIL, FAIL",
            "PASS, DATA_INSUFFICIENT, DATA_INSUFFICIENT",
            "DATA_INSUFFICIENT, PASS, DATA_INSUFFICIENT",
            "FAIL, DATA_INSUFFICIENT, FAIL",
            "DATA_INSUFFICIENT, FAIL, FAIL",
            "PASS, ERROR, ERROR",
            "ERROR, PASS, ERROR",
            "FAIL, ERROR, FAIL",
            "ERROR, FAIL, FAIL",
            "PASS, NOT_APPLICABLE, PASS",
            "NOT_APPLICABLE, PASS, PASS",
            "NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE",
            "DATA_INSUFFICIENT, ERROR, ERROR",
            "ERROR, DATA_INSUFFICIENT, ERROR",
            "REFER, PASS, REFER",
            "PASS, REFER, REFER"
    })
    void andMatrix(String a, String b, String expected) {
        assertThat(and(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PASS, PASS, PASS",
            "PASS, FAIL, PASS",
            "FAIL, PASS, PASS",
            "FAIL, FAIL, FAIL",
            "PASS, DATA_INSUFFICIENT, PASS",
            "DATA_INSUFFICIENT, PASS, PASS",
            "FAIL, DATA_INSUFFICIENT, DATA_INSUFFICIENT",
            "DATA_INSUFFICIENT, FAIL, DATA_INSUFFICIENT",
            "PASS, ERROR, PASS",
            "ERROR, PASS, PASS",
            "FAIL, ERROR, ERROR",
            "ERROR, FAIL, ERROR",
            "PASS, NOT_APPLICABLE, PASS",
            "NOT_APPLICABLE, FAIL, FAIL",
            "NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE",
            "DATA_INSUFFICIENT, ERROR, ERROR",
            "REFER, FAIL, REFER",
            "FAIL, REFER, REFER"
    })
    void orMatrix(String a, String b, String expected) {
        assertThat(or(a, b)).isEqualTo(expected);
    }

    @Test
    void studioPassFailDiPreserved() {
        assertThat(and("PASS", "DATA_INSUFFICIENT")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(or("PASS", "DATA_INSUFFICIENT")).isEqualTo("PASS");
        assertThat(or("FAIL", "DATA_INSUFFICIENT")).isEqualTo("DATA_INSUFFICIENT");
    }
}
