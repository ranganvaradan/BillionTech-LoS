package com.los.core.creditintelligence.cutover.domain;

public enum ReviewDisposition {
    EXPECTED_CANONICAL,
    LEGACY_CORRECT,
    CANONICAL_CORRECT,
    NEEDS_INVESTIGATION,
    BUG,
    POLICY_CHANGE_REQUIRED
}
