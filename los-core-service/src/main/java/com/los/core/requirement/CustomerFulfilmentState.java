package com.los.core.requirement;

/**
 * Customer-side fulfilment. Independent of {@link DataReadinessState}.
 * PROVIDED ≠ READY_FOR_POLICY.
 */
public enum CustomerFulfilmentState {
    REQUIRED,
    REQUESTED,
    PROVIDED,
    WAIVED,
    NOT_APPLICABLE
}
