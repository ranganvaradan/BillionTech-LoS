package com.los.core.creditintelligence.decisionpolicy;

/**
 * Business domain classification for Decision Policy rules.
 * Stored in rule {@code metadata.decisionDomain} / {@code scope.decisionDomain} — no DB migration.
 * Existing credit rules without this field are treated as {@link #CREDIT}.
 */
public enum DecisionPolicyDomain {
    KYC,
    ELIGIBILITY,
    CREDIT,
    RISK_SCORE,
    LIMIT,
    PRICING,
    DECISION_REVIEW;

    public boolean isKycOrEligibility() {
        return this == KYC || this == ELIGIBILITY;
    }

    public boolean isCreditFamily() {
        return this == CREDIT || this == RISK_SCORE || this == LIMIT || this == PRICING;
    }

    public static DecisionPolicyDomain fromMetadata(Object raw) {
        if (raw == null) {
            return CREDIT;
        }
        String s = String.valueOf(raw).trim().toUpperCase();
        if (s.isEmpty()) {
            return CREDIT;
        }
        try {
            return DecisionPolicyDomain.valueOf(s);
        } catch (IllegalArgumentException ex) {
            // Legacy aliases
            if ("UNDERWRITING".equals(s) || "SCORECARD".equals(s)) {
                return CREDIT;
            }
            if ("IDENTITY".equals(s) || "IDENTITY_KYC".equals(s)) {
                return KYC;
            }
            return CREDIT;
        }
    }

    public String businessLabel() {
        return switch (this) {
            case KYC -> "KYC";
            case ELIGIBILITY -> "Eligibility";
            case CREDIT -> "Credit Underwriting";
            case RISK_SCORE -> "Risk / Score";
            case LIMIT -> "Limit";
            case PRICING -> "Pricing";
            case DECISION_REVIEW -> "Decision / Review";
        };
    }
}
