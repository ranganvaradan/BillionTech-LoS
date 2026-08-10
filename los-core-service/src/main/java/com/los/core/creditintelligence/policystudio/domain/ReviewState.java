package com.los.core.creditintelligence.policystudio.domain;

public enum ReviewState {
    AI_DRAFTED,
    MAPPING_REVIEW,
    METRIC_REVIEW,
    RULE_REVIEW,
    TEST_REVIEW,
    CREDIT_MANAGER_APPROVED,
    CHECKER_APPROVED,
    READY_FOR_POLICY_BUILD,
    REJECTED
}
