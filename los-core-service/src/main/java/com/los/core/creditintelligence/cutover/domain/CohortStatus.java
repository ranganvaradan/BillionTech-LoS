package com.los.core.creditintelligence.cutover.domain;

public enum CohortStatus {
    DRAFT,
    VALIDATION,
    DUAL_RUN,
    READY,
    ACTIVE,
    ROLLED_BACK,
    SUSPENDED
}
