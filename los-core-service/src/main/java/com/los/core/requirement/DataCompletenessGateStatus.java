package com.los.core.requirement;

/**
 * Authoritative application-level Data Completeness Gate outcomes (W6).
 * Does not invoke Policy or Scorecard.
 */
public enum DataCompletenessGateStatus {
    READY_FOR_POLICY,
    WAITING_FOR_CUSTOMER,
    ACQUISITION_IN_PROGRESS,
    MANUAL_REVIEW_REQUIRED,
    BLOCKED
}
