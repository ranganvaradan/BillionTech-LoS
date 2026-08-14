package com.los.core.customercategory;

/**
 * Lifecycle for Customer Category and Policy Set.
 * APPROVED ≠ ACTIVE — activation is a separate controlled step.
 * Not wired to live underwriting routing.
 */
public enum ConfigLifecycleStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    ACTIVE,
    RETIRED
}
