package com.los.core.requirement;

/**
 * Canonical / derived data readiness for Policy. Independent of customer fulfilment.
 */
public enum DataReadinessState {
    NOT_AVAILABLE,
    PROCESSING,
    EXTRACTED,
    VERIFIED,
    READY_FOR_POLICY,
    FAILED,
    DATA_INSUFFICIENT
}
