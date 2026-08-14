package com.los.core.service.underwriting;

import java.util.Locale;

/**
 * SCORECARD-SAFETY-FOUNDATION-1 — value provenance for scorecard inputs.
 */
public final class ScorecardValueProvenance {

    public static final String CANONICAL = "CANONICAL";
    public static final String REAL_PROVIDER = "REAL_PROVIDER";
    public static final String APPLICATION = "APPLICATION";
    public static final String DERIVED = "DERIVED";
    public static final String MANUAL_AUTHORISED = "MANUAL_AUTHORISED";
    public static final String DEMO_DEFAULT = "DEMO_DEFAULT";
    public static final String GAP_DEFAULT = "GAP_DEFAULT";
    /** Explicit Equifax / bureau simulation fixture — non-authoritative. */
    public static final String SIMULATED = "SIMULATED";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String MISSING = "MISSING";

    private ScorecardValueProvenance() {}

    public static boolean isNonAuthoritative(String provenance) {
        if (provenance == null) return false;
        String p = provenance.trim().toUpperCase(Locale.ROOT);
        return DEMO_DEFAULT.equals(p) || GAP_DEFAULT.equals(p) || SIMULATED.equals(p);
    }

    public static boolean isAuthoritative(String provenance) {
        if (provenance == null || provenance.isBlank()) return false;
        String p = provenance.trim().toUpperCase(Locale.ROOT);
        return CANONICAL.equals(p)
                || REAL_PROVIDER.equals(p)
                || APPLICATION.equals(p)
                || DERIVED.equals(p)
                || MANUAL_AUTHORISED.equals(p);
    }

    /**
     * REQUIRED factors may be satisfied only by authoritative provenance in production.
     * DEMO/GAP may satisfy REQUIRED only when explicitly evaluating DEMO_NON_PRODUCTION.
     * UNKNOWN / MISSING never satisfy REQUIRED.
     */
    public static boolean canSatisfyRequired(String provenance, boolean allowDemoNonProductionScoring) {
        if (isAuthoritative(provenance)) {
            return true;
        }
        if (allowDemoNonProductionScoring && isNonAuthoritative(provenance)) {
            return true;
        }
        return false;
    }
}
