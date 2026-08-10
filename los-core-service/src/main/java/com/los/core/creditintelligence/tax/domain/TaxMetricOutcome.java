package com.los.core.creditintelligence.tax.domain;

public enum TaxMetricOutcome {
    PASS,
    FAIL,
    REFER,
    DATA_INSUFFICIENT,
    NOT_APPLICABLE,
    ERROR,
    MATCH,
    ACCEPTABLE_VARIANCE,
    MATERIAL_VARIANCE,
    CONFLICT
}
