package com.los.core.creditintelligence.policy.domain;

/** Lifecycle of an executable policy package. ACTIVE is never production authority in P1. */
public enum ExecutablePackageStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    SHADOW,
    ACTIVE,
    SUPERSEDED,
    RETIRED
}
