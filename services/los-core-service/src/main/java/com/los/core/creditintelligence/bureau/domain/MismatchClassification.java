package com.los.core.creditintelligence.bureau.domain;

/**
 * Classification of legacy vs canonical bureau shadow comparison differences.
 */
public enum MismatchClassification {
    LEGACY_DEFAULT_USED,
    LEGACY_MANUAL_VALUE,
    CANONICAL_TRADELINE_COUNT_DIFFERENT,
    CANONICAL_DATA_INSUFFICIENT,
    UNKNOWN_PRODUCT_CLASSIFICATION,
    DUPLICATE_REMOVED,
    STALE_REPORT,
    PARSER_DIFFERENCE,
    OTHER
}
