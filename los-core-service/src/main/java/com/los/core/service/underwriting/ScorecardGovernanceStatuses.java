package com.los.core.service.underwriting;

/** SCORECARD-GOVERNANCE-1 — single business lifecycle. No SCHEDULED invented. */
public final class ScorecardGovernanceStatuses {

    public static final String DRAFT = "DRAFT";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";

    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_REJECTED = "REJECTED";
    public static final String DECISION_RETURNED = "RETURNED";

    private ScorecardGovernanceStatuses() {}

    public static boolean isEditableDraft(String status) {
        return DRAFT.equalsIgnoreCase(status);
    }

    public static boolean isImmutableExecution(String status, boolean active) {
        return active
                || ACTIVE.equalsIgnoreCase(status)
                || APPROVED.equalsIgnoreCase(status)
                || IN_REVIEW.equalsIgnoreCase(status);
    }
}
