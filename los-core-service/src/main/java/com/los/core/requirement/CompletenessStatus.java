package com.los.core.requirement;

/**
 * Plan-level completeness for the next stage gate (no Policy invocation in W3).
 */
public enum CompletenessStatus {
    COMPLETE_FOR_NEXT_STAGE,
    INCOMPLETE,
    BLOCKED,
    PROCESSING
}
