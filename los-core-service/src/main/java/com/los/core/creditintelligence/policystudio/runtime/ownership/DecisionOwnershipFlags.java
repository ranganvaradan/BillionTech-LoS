package com.los.core.creditintelligence.policystudio.runtime.ownership;

/**
 * Wave-7/8/10 staged ownership flags. Live cutover remains OFF.
 * Compatibility flags enable shadow / staged disable of duplicate decision roles.
 */
public final class DecisionOwnershipFlags {

    /** Live LOS underwriting still uses LoanApplicationFlowService → UW rules + Scorecard. */
    public static final boolean LIVE_DECISION_AUTHORITY_CHANGED = false;

    /** Frozen remains current live/replay authority where configured. */
    public static final boolean FROZEN_RETIRED = false;

    /**
     * Explicit Wave-10 routing. Default {@link LiveDecisionAuthority#LEGACY_FROZEN}.
     * Flip to CANONICAL only when cutover readiness is YES (Wave-10 leaves default).
     */
    private static volatile LiveDecisionAuthority liveDecisionAuthority = LiveDecisionAuthority.LEGACY_FROZEN;

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

    /**
     * When true, {@link CanonicalUnderwritingOrchestration#assembleTargetLive} enforces
     * production certification. Default false — legacy live authority unchanged.
     */
    private static volatile boolean targetLiveCertificationGateEnabled = false;

    private DecisionOwnershipFlags() {}

    public static LiveDecisionAuthority liveDecisionAuthority() {
        return liveDecisionAuthority;
    }

    /** Test / staged flip only. Production default remains LEGACY_FROZEN. */
    public static void setLiveDecisionAuthority(LiveDecisionAuthority authority) {
        liveDecisionAuthority = authority == null ? LiveDecisionAuthority.LEGACY_FROZEN : authority;
    }

    public static boolean isCanonicalPrimary() {
        return liveDecisionAuthority == LiveDecisionAuthority.CANONICAL;
    }

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

    public static boolean targetLiveCertificationGateEnabled() {
        return targetLiveCertificationGateEnabled;
    }

    public static void setTargetLiveCertificationGateEnabled(boolean value) {
        targetLiveCertificationGateEnabled = value;
    }

    public static void resetForTests() {
        scorecardHardRulesShadowOnly = false;
        demoDefaultsNotDecisionTruth = true;
        targetLiveCertificationGateEnabled = false;
        liveDecisionAuthority = LiveDecisionAuthority.LEGACY_FROZEN;
    }
}
