package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates that max_dpd_12m cannot semantically represent clean_history_months.
 */
class DerivedCalculationSemanticCompatibilityTest {

    private static CanonicalParameterDefinition id(String canonicalId) {
        return PolicyStudioConvergencePresenter.registry().findById(canonicalId).orElseThrow();
    }

    @Test
    void maxDpd12mCannotRepresentCleanHistoryMonths() {
        var target = id("bureau.credit_after_overdue.clean_history_months");
        var source = id("bureau.max_dpd_12m");
        var r = DerivedCalculationSemanticCompatibility.assess(target, source, true);
        assertFalse(r.compatible(), () -> "failures expected; got evidence=" + r.evidence());
        assertFalse(r.strongEquivalence());
        String joined = String.join(" | ", r.failures()).toLowerCase();
        assertTrue(joined.contains("unit") || joined.contains("dimension") || joined.contains("months")
                        || joined.contains("days") || joined.contains("semantic")
                        || joined.contains("temporal") || joined.contains("vocabulary")
                        || joined.contains("configuration"),
                () -> "expected dimensional/semantic failure, got: " + r.failures());
    }

    @Test
    void directRefExpressionToMaxDpdRejectedForCleanHistory() {
        var target = id("bureau.credit_after_overdue.clean_history_months");
        var r = DerivedCalculationSemanticCompatibility.assessExpression(
                target,
                Map.of("op", "REF", "id", "bureau.max_dpd_12m"),
                id -> PolicyStudioConvergencePresenter.registry().findById(id).orElse(null));
        assertFalse(r.compatible());
    }

    @Test
    void unitFamiliesDistinguishMonthsFromDays() {
        assertTrue(DerivedCalculationSemanticCompatibility.dimFamily("MONTHS")
                .equals("DURATION_CALENDAR"));
        assertTrue(DerivedCalculationSemanticCompatibility.dimFamily("DAYS")
                .equals("DURATION_DAYS"));
        assertFalse(DerivedCalculationSemanticCompatibility.dimFamily("MONTHS")
                .equals(DerivedCalculationSemanticCompatibility.dimFamily("DAYS")));
    }
}
