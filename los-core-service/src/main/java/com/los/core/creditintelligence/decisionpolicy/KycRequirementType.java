package com.los.core.creditintelligence.decisionpolicy;

/**
 * Semantic categories for KYC / Eligibility policy requirements.
 * These are NOT business measures — do not force them through BusinessMeasureDesigner.
 */
public enum KycRequirementType {
    VERIFICATION,
    INFORMATION_REQUIREMENT,
    MATCH_REQUIREMENT,
    COMPLETION_REQUIREMENT,
    MANUAL_VERIFICATION,
    ELIGIBILITY_CONDITION,
    BOUNDARY_CONDITION,
    REGULATORY_GUARDRAIL;

    public static KycRequirementType fromMetadata(Object raw) {
        if (raw == null) {
            return VERIFICATION;
        }
        String s = String.valueOf(raw).trim().toUpperCase();
        try {
            return KycRequirementType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return VERIFICATION;
        }
    }

    public String businessLabel() {
        return switch (this) {
            case VERIFICATION -> "Verification";
            case INFORMATION_REQUIREMENT -> "Information requirement";
            case MATCH_REQUIREMENT -> "Match requirement";
            case COMPLETION_REQUIREMENT -> "Completion requirement";
            case MANUAL_VERIFICATION -> "Manual verification";
            case ELIGIBILITY_CONDITION -> "Eligibility condition";
            case BOUNDARY_CONDITION -> "Boundary condition";
            case REGULATORY_GUARDRAIL -> "Regulatory / platform guardrail";
        };
    }
}
