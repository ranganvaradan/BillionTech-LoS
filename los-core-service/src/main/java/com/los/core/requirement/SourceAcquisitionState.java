package com.los.core.requirement;

/**
 * Automatic / provider acquisition attempt state. Not collapsed into customer fulfilment
 * or {@link DataReadinessState} (W3 separation remains authoritative).
 * <p>
 * W6 expands the vocabulary for deterministic orchestration. Legacy aliases retained
 * for existing plans/tests.
 */
public enum SourceAcquisitionState {
    /** Item does not require platform source execution. */
    NOT_REQUIRED,
    /** Waiting for prerequisites / not yet queued. */
    WAITING,
    /** Legacy alias of WAITING for plans created before W6. */
    NOT_STARTED,
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    /** Legacy generic failure — treat as terminal unless attempt ledger says retryable. */
    FAILED,
    TIMED_OUT,
    UNAVAILABLE,
    /** Preferred source terminal; customer must use allowed fallback (e.g. bank statement). */
    CUSTOMER_ACTION_REQUIRED,
    /** Legacy alias of CUSTOMER_ACTION_REQUIRED. */
    CUSTOMER_FALLBACK,
    MANUAL_REVIEW_REQUIRED,
    /** Legacy alias of MANUAL_REVIEW_REQUIRED. */
    MANUAL_REVIEW;

    public boolean isTerminalSuccess() {
        return this == SUCCEEDED;
    }

    public boolean isInFlight() {
        return this == QUEUED || this == IN_PROGRESS;
    }

    public boolean isRetryableFailure() {
        return this == FAILED_RETRYABLE || this == TIMED_OUT;
    }

    public boolean isTerminalFailure() {
        return this == FAILED_TERMINAL
                || this == FAILED
                || this == UNAVAILABLE
                || this == TIMED_OUT;
    }

    public boolean requiresCustomerAction() {
        return this == CUSTOMER_ACTION_REQUIRED || this == CUSTOMER_FALLBACK;
    }

    public boolean requiresManualReview() {
        return this == MANUAL_REVIEW_REQUIRED || this == MANUAL_REVIEW;
    }
}
