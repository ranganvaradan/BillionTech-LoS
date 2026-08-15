package com.los.core.requirement;

/**
 * Customer-side fulfilment. Independent of {@link DataReadinessState}.
 * PROVIDED ≠ READY_FOR_POLICY.
 * <p>
 * {@link #REUPLOAD_REQUIRED} is an explicit controlled outcome after document rejection —
 * not automatic on extraction/parser failure.
 */
public enum CustomerFulfilmentState {
    REQUIRED,
    REQUESTED,
    PROVIDED,
    WAIVED,
    NOT_APPLICABLE,
    /** Explicit controlled re-upload after DOCUMENT_REJECTED — not used for EXTRACTION_FAILED. */
    REUPLOAD_REQUIRED
}
