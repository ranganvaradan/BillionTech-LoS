package com.los.core.creditintelligence.decision.domain;

public enum RecommendationOutcome {
    APPROVE,
    APPROVE_WITH_CONDITIONS,
    REFER,
    DECLINE,
    DATA_INSUFFICIENT,
    COUNTER_OFFER
}
