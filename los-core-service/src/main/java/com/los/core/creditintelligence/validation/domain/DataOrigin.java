package com.los.core.creditintelligence.validation.domain;

/**
 * Honest classification of validation dataset origin. Never label USER_SUPPLIED_SAMPLE or SYNTHETIC as live.
 */
public enum DataOrigin {
    ANONYMIZED_REAL_DEV_DATA,
    STORED_PROVIDER_FIXTURE,
    USER_SUPPLIED_SAMPLE,
    REPRESENTATIVE_PROVIDER_FIXTURE,
    SYNTHETIC
}
