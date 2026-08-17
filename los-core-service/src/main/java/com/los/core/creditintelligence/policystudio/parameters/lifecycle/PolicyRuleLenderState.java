package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

/**
 * POLICY-STUDIO-RULE-LIFECYCLE-AND-STATE-MODEL-CLOSURE-1
 * Single lender-facing state for a Policy rule. One rule → one state.
 */
public enum PolicyRuleLenderState {
    /** Parameter/source/calculation/business clarification still outstanding. */
    NEEDS_INPUT,
    /** Setup complete; lender has not confirmed rule meaning / calculation. */
    READY_FOR_CONFIRMATION,
    /** Confirmed and executable in Policy Test (production may still be pending). */
    READY_TO_TEST,
    /** Explicitly accepted and ready to test (same readiness as READY_TO_TEST; badge emphasizes acceptance). */
    ACCEPTED_READY_TO_TEST,
    /** Required data/source unavailable. */
    DATA_NOT_AVAILABLE,
    /** Testable but not certified for live production. */
    PRODUCTION_BLOCKED,
    /** Accepted and production-certified path. */
    PRODUCTION_READY,
    /** Lender ignored the rule for automation — still a genuine policy rule, not business N/A. */
    IGNORED,
    /** Kept as a policy requirement (not automated) — still a genuine policy rule, not business N/A. */
    POLICY_REQUIREMENT,
    /** Truly out of underwriting-automation scope (classification / data-requirement / deleted / compound child). */
    NOT_APPLICABLE
}
