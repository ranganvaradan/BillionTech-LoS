package com.los.core.creditintelligence.decision.domain;

/** P2 may identify deviations but never auto-approves. */
public enum DeviationStatus {
    REQUESTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED
}
