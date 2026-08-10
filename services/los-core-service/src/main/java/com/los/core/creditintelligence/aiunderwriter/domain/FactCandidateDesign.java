package com.los.core.creditintelligence.aiunderwriter.domain;

/**
 * Design-only stub for a future FACT_CANDIDATE workflow.
 * <p>
 * Intended future path (NOT activated in A1):
 * <pre>
 *   AI output → deterministic validation → human review → new fact snapshot
 * </pre>
 * A1 must never promote fact candidates. ACCEPTED_AS_NOTE ≠ fact promotion.
 * No ACCEPT_AS_FACT action exists in A1.
 */
public final class FactCandidateDesign {

    public static final String FUTURE_TYPE = "FACT_CANDIDATE";
    public static final boolean ACTIVATED_IN_A1 = false;

    private FactCandidateDesign() {
    }

    /**
     * @return always false — A1 does not activate FACT_CANDIDATE promotion.
     */
    public static boolean isPromotionAllowed() {
        return false;
    }
}
