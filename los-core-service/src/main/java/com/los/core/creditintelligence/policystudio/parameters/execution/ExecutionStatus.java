package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Canonical parameter execution outcomes. Capability and execution share this vocabulary.
 */
public enum ExecutionStatus {
    VALUE_AVAILABLE,
    INPUT_REQUIRED,
    DATA_NOT_AVAILABLE,
    CALCULATION_NOT_DEFINED,
    DEPENDENCY_NOT_AVAILABLE,
    NOT_EXECUTABLE,
    ERROR
}
