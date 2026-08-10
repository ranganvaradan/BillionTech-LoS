package com.los.core.creditintelligence.reconciliation.domain;

/**
 * Shadow mismatch classifications comparing legacy single-source underwriting vs canonical reconciliation.
 */
public enum ReconciliationMismatchClassification {
    LEGACY_DEFAULT_USED,
    LEGACY_MANUAL_VALUE,
    SINGLE_SOURCE_PRODUCTION,
    GST_ITR_VARIANCE,
    GST_BANK_VARIANCE,
    ITR_BANK_VARIANCE,
    BUREAU_BANK_OBLIGATION_VARIANCE,
    DECLARED_OBLIGATION_VARIANCE,
    PERIOD_MISMATCH,
    SUBJECT_MISMATCH,
    SOURCE_INCOMPLETE,
    CANONICAL_DATA_INSUFFICIENT,
    OTHER
}
