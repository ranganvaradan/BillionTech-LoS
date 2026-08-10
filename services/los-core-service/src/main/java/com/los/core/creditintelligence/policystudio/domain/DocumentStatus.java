package com.los.core.creditintelligence.policystudio.domain;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    PARSED,
    INTERPRETING,
    REVIEW_REQUIRED,
    DRAFT_READY,
    APPROVED_FOR_POLICY_BUILD,
    REJECTED,
    SUPERSEDED
}
