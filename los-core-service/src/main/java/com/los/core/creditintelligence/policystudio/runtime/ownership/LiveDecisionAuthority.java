package com.los.core.creditintelligence.policystudio.runtime.ownership;

/**
 * Wave-10 live decision authority routing.
 * Default remains {@link #LEGACY_FROZEN} until LIVE_CUTOVER_READY = YES and explicit flip.
 */
public enum LiveDecisionAuthority {
    /** LoanApplicationFlowService → UnderwritingRuleEngine + ScorecardPolicyEngine (+ Frozen where configured). */
    LEGACY_FROZEN,
    /** CanonicalUnderwritingOrchestration → CPR + CPES + certification gate. */
    CANONICAL
}
