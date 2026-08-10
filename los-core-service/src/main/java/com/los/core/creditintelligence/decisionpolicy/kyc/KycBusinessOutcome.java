package com.los.core.creditintelligence.decisionpolicy.kyc;

/**
 * Future governed aggregate KYC evaluation outcomes for Decision Policy.
 * Does NOT replace production {@code computeKycOutcome} (PASS/FAIL/INCOMPLETE) in KYC-2.
 */
public enum KycBusinessOutcome {
    PASS,
    REFER,
    FAIL,
    MISSING_INFORMATION;

    /** Map to existing DSL outcome vocabulary where applicable. */
    public String toDslOutcome() {
        return switch (this) {
            case PASS -> "PASS";
            case REFER -> "REFER";
            case FAIL -> "FAIL";
            case MISSING_INFORMATION -> "DATA_INSUFFICIENT";
        };
    }

    public static KycBusinessOutcome fromLegacyAggregate(String legacy) {
        if (legacy == null) {
            return MISSING_INFORMATION;
        }
        return switch (legacy.trim().toUpperCase()) {
            case "PASS" -> PASS;
            case "FAIL", "FAILED", "REJECT", "REJECTED" -> FAIL;
            case "INCOMPLETE", "PENDING", "DATA_INSUFFICIENT" -> MISSING_INFORMATION;
            case "REFER", "MANUAL_REVIEW", "NEEDS_REVIEW" -> REFER;
            default -> MISSING_INFORMATION;
        };
    }
}
