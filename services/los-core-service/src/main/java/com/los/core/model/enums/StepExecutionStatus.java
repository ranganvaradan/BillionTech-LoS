package com.los.core.model.enums;

/**
 * Status of a single {@link com.los.core.model.entity.StepExecutionRecord} row
 * (inserted with STARTED, then updated to SUCCESS or FAILED when the run finishes).
 */
public enum StepExecutionStatus {
    STARTED,
    SUCCESS,
    FAILED
}
