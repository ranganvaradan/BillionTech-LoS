package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

/**
 * Canonical shadow final decision. Not written to loan_applications.credit_decision.
 */
public enum CanonicalShadowDecision {
    APPROVE,
    REJECT,
    MANUAL_REVIEW,
    DATA_INSUFFICIENT,
    NOT_EXECUTABLE
}
