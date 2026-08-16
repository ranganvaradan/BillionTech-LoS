package com.los.core.creditintelligence.policystudio.certification;

/**
 * Live authority uses only {@link #CERTIFIED}.
 * {@link #PENDING_REVIEW} is operational and never authorizes live use.
 */
public enum CertificationStatus {
    UNCERTIFIED,
    CERTIFIED,
    REVOKED,
    PENDING_REVIEW;

    public boolean permitsLiveUse() {
        return this == CERTIFIED;
    }
}
