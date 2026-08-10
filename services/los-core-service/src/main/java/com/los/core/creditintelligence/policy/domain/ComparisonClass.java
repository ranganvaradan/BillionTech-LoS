package com.los.core.creditintelligence.policy.domain;

public enum ComparisonClass {
    MATCH,
    DSL_STRICTER,
    DSL_MORE_PERMISSIVE,
    LEGACY_DEFAULT_DEPENDENT,
    CANONICAL_DATA_INSUFFICIENT,
    LEGACY_SEMANTIC_DIFFERENCE,
    POLICY_MAPPING_DIFFERENCE
}
