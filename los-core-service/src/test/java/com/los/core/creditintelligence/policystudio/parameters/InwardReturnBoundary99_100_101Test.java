package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BANKING-BRE-FINAL-CLOSURE-1 — 99 / 100 / 101 boundary after treat_100_as_ratio_branch.
 */
class InwardReturnBoundary99_100_101Test {

    @Test
    void afterGte100_ratioBranch_covers100And101_countCovers99() {
        Map<String, Object> expr = PolicyDsl.iff(
                PolicyDsl.gt(PolicyDsl.metric(InwardReturnCompoundSupport.TXN_METRIC), 100),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.RATIO_METRIC), 5),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.COUNT_METRIC), 5));
        Map<String, Object> patched = InwardReturnCompoundSupport.patchBoundary(
                expr, InwardReturnCompoundSupport.OPTION_RATIO);
        @SuppressWarnings("unchecked")
        Map<String, Object> cond = (Map<String, Object>) patched.get("condition");
        assertThat(cond.get("op")).isEqualTo("GTE");

        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();

        // 99 → else (count <= 5); count=3 → PASS
        assertThat(eval(interp, patched, metrics(99, 10, 3))).isEqualTo("PASS");
        // 99 with count=6 → FAIL
        assertThat(eval(interp, patched, metrics(99, 10, 6))).isEqualTo("FAIL");

        // 100 → then (ratio <= 5); ratio=4 → PASS
        assertThat(eval(interp, patched, metrics(100, 4, 99))).isEqualTo("PASS");
        // 100 with ratio=6 → FAIL
        assertThat(eval(interp, patched, metrics(100, 6, 0))).isEqualTo("FAIL");

        // 101 → then (ratio)
        assertThat(eval(interp, patched, metrics(101, 5, 99))).isEqualTo("PASS");
        assertThat(eval(interp, patched, metrics(101, 5.1, 0))).isEqualTo("FAIL");
    }

    private static Map<String, Object> metrics(int txn, double ratio, int count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(InwardReturnCompoundSupport.TXN_METRIC, txn);
        m.put(InwardReturnCompoundSupport.RATIO_METRIC, ratio);
        m.put(InwardReturnCompoundSupport.COUNT_METRIC, count);
        return m;
    }

    private static String eval(PolicyDslInterpreterV1 interp, Map<String, Object> expr, Map<String, Object> metrics) {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(metrics, Map.of(), null);
        return interp.evaluate(expr, ctx);
    }
}
