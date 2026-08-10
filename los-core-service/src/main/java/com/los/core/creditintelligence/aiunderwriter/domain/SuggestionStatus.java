package com.los.core.creditintelligence.aiunderwriter.domain;

public enum SuggestionStatus {
    GENERATED,
    PENDING_REVIEW,
    ACCEPTED_AS_NOTE,
    EDITED,
    REJECTED,
    SUPERSEDED,
    REJECTED_GROUNDING_FAILURE
}
