package com.los.core.creditintelligence.policystudio.truth;

/**
 * Canonical reason for {@link BusinessReadiness}.
 * Orthogonal to ExecutionStatus (VALUE_AVAILABLE / DATA_NOT_AVAILABLE / …).
 */
public enum BusinessReadinessReason {
    READY,
    SOURCE_NOT_INTEGRATED,
    SOURCE_NOT_CONFIGURED,
    RAW_FIELD_NOT_AVAILABLE,
    CALCULATION_NOT_DEFINED,
    CALCULATION_INVALID,
    DEPENDENCY_NOT_READY,
    MANUAL_INPUT,
    NOT_SUPPORTED,
    NOT_APPLICABLE,
    NOT_IN_CATALOGUE
}
