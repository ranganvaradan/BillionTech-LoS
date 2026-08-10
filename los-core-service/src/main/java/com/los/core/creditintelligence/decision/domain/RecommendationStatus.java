package com.los.core.creditintelligence.decision.domain;

/**
 * Recommendation lifecycle. P2 only persists {@link #RECOMMENDED}.
 */
public enum RecommendationStatus {
    RECOMMENDED,
    UNDER_REVIEW,
    ACCEPTED,
    MODIFIED,
    REJECTED
}
