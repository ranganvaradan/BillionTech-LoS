package com.los.core.creditintelligence.policystudio.domain;

public enum AmbiguityResolutionAction {
    SELECT_CANDIDATE,
    CREATE_NEW_METRIC,
    CREATE_POLICY_PARAMETER,
    CREATE_VOCABULARY_TERM,
    MARK_NOT_APPLICABLE,
    REJECT_INTERPRETATION,
    EDIT_INTERPRETATION,
    REQUEST_CLARIFICATION
}
