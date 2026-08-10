package com.los.core.creditintelligence.reconciliation.domain;

public enum ReconciliationOutcome {
    MATCH,
    ACCEPTABLE_VARIANCE,
    MATERIAL_VARIANCE,
    CONFLICT,
    DATA_INSUFFICIENT,
    NOT_APPLICABLE,
    ERROR
}
