package com.los.core.creditintelligence.decision.domain;

public enum DecisionComparisonClass {
    MATCH,
    CANONICAL_AMOUNT_LOWER,
    CANONICAL_AMOUNT_HIGHER,
    TENURE_DIFFERENT,
    PRICING_DIFFERENT,
    AUTHORITY_DIFFERENT,
    CONDITION_ADDED,
    DATA_INSUFFICIENT,
    LEGACY_DEFAULT_DEPENDENT
}
