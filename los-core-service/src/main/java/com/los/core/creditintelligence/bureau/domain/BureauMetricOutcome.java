package com.los.core.creditintelligence.bureau.domain;

/**
 * Metric computation outcome. Aligns with {@link com.los.core.creditintelligence.domain.RuleOutcome}
 * for shadow rule persistence.
 */
public enum BureauMetricOutcome {
    PASS,
    FAIL,
    REFER,
    DATA_INSUFFICIENT,
    ERROR
}
