package com.los.core.creditintelligence.reconciliation.domain;

public enum ReconciliationDataStatus {
    COMPLETE,
    PARTIAL,
    MISSING_LEFT,
    MISSING_RIGHT,
    MISSING_BOTH,
    PERIOD_MISMATCH,
    LOW_CONFIDENCE,
    STALE,
    CONFLICTED_SOURCE
}
