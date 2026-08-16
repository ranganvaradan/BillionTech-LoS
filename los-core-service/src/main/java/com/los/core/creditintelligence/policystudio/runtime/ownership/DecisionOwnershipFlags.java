package com.los.core.creditintelligence.policystudio.runtime.ownership;

/**
 * Wave-7 staged ownership flags. Live cutover remains OFF.
 * Compatibility flags enable shadow / staged disable of duplicate decision roles.
 */
public final class DecisionOwnershipFlags {

    /** Live LOS underwriting still uses LoanApplicationFlowService → UW rules + Scorecard. */
    public static final boolean LIVE_DECISION_AUTHORITY_CHANGED = false;

    /** Frozen remains current live/replay authority where configured. */
    public static final boolean FROZEN_RETIRED = false;

    /**
     * When true, {@link com.los.core.service.underwriting.ScorecardPolicyEngine} records hard-rule
     * matches observationally but does not early-exit REJECT/MANUAL — Policy owns eligibility.
     * Default false = live behavior unchanged (rollback = leave false).
     */
    private static volatile boolean scorecardHardRulesShadowOnly = false;

    /**
     * When true, CreditControl demo/gap defaults are never treated as authoritative decision truth
     * by {@link CanonicalUnderwritingOrchestration}. Prep may still emit them with provenance.
     */
    private static volatile boolean demoDefaultsNotDecisionTruth = true;

    private DecisionOwnershipFlags() {}

    public static boolean scorecardHardRulesShadowOnly() {
        return scorecardHardRulesShadowOnly;
    }

    public static void setScorecardHardRulesShadowOnly(boolean value) {
        scorecardHardRulesShadowOnly = value;
    }

    public static boolean demoDefaultsNotDecisionTruth() {
        return demoDefaultsNotDecisionTruth;
    }

    public static void setDemoDefaultsNotDecisionTruth(boolean value) {
        demoDefaultsNotDecisionTruth = value;
    }

    public static void resetForTests() {
        scorecardHardRulesShadowOnly = false;
        demoDefaultsNotDecisionTruth = true;
    }
}
