package com.los.core.creditintelligence.decisionpolicy.corpus;

/**
 * Honest origin classification for DP-V1 validation corpus applications.
 * Only ANONYMIZED_REAL_DEV_DATA and STORED_PROVIDER_DATA count as strong real/stored evidence.
 */
public enum DecisionPolicyCorpusOrigin {
    ANONYMIZED_REAL_DEV_DATA,
    STORED_PROVIDER_DATA,
    USER_SUPPLIED_SAMPLE,
    REPRESENTATIVE_FIXTURE,
    SYNTHETIC;

    public boolean countsTowardRealStoredCertification() {
        return this == ANONYMIZED_REAL_DEV_DATA || this == STORED_PROVIDER_DATA;
    }

    public static DecisionPolicyCorpusOrigin parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SYNTHETIC;
        }
        try {
            return DecisionPolicyCorpusOrigin.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SYNTHETIC;
        }
    }
}
