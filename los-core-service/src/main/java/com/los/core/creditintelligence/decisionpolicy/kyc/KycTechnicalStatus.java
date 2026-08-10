package com.los.core.creditintelligence.decisionpolicy.kyc;

/**
 * Technical / provider execution status — distinct from {@link KycBusinessOutcome}.
 * PROVIDER_UNAVAILABLE and TIMEOUT must never be treated as borrower FAIL.
 */
public enum KycTechnicalStatus {
    AVAILABLE,
    UNAVAILABLE,
    TIMEOUT,
    ERROR,
    FALLBACK_USED,
    SUCCESS,
    PENDING,
    SKIPPED;

    public boolean isTechnicalFailure() {
        return this == UNAVAILABLE || this == TIMEOUT || this == ERROR;
    }

    public boolean indicatesProviderContinuityIssue() {
        return this == UNAVAILABLE || this == TIMEOUT || this == ERROR;
    }
}
