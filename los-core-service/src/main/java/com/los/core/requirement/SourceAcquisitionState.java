package com.los.core.requirement;

/**
 * Automatic / provider acquisition attempt state. Not collapsed into customer fulfilment.
 */
public enum SourceAcquisitionState {
    NOT_STARTED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    UNAVAILABLE,
    CUSTOMER_FALLBACK,
    MANUAL_REVIEW
}
