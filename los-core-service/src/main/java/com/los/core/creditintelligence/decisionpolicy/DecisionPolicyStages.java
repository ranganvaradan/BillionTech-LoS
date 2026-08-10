package com.los.core.creditintelligence.decisionpolicy;

import java.util.List;

/**
 * Conceptual evaluation stages for Decision Policy packages.
 * Reuses existing {@code IDENTITY_KYC} stage code from DeclarativeOrchestrator for KYC grouping.
 * No production orchestration change in KYC-2.
 */
public final class DecisionPolicyStages {

    /** Existing DeclarativeOrchestrator default — keep for compatibility. */
    public static final String IDENTITY_KYC = "IDENTITY_KYC";

    /** Preferred Decision Policy alias for KYC & Eligibility grouping. */
    public static final String KYC_ELIGIBILITY = "KYC_ELIGIBILITY";

    public static final String CREDIT_UNDERWRITING = "CREDIT_UNDERWRITING";
    public static final String RISK_SCORE = "RISK_SCORE";
    public static final String LIMIT_PRICING = "LIMIT_PRICING";
    public static final String DECISION_REVIEW = "DECISION_REVIEW";

    private DecisionPolicyStages() {}

    public static List<String> orderedConceptualStages() {
        return List.of(
                KYC_ELIGIBILITY,
                CREDIT_UNDERWRITING,
                RISK_SCORE,
                LIMIT_PRICING,
                DECISION_REVIEW);
    }

    /** Map domain → preferred stage code for package authoring. */
    public static String stageForDomain(DecisionPolicyDomain domain) {
        if (domain == null) {
            return CREDIT_UNDERWRITING;
        }
        return switch (domain) {
            case KYC, ELIGIBILITY -> KYC_ELIGIBILITY;
            case CREDIT -> CREDIT_UNDERWRITING;
            case RISK_SCORE -> RISK_SCORE;
            case LIMIT, PRICING -> LIMIT_PRICING;
            case DECISION_REVIEW -> DECISION_REVIEW;
        };
    }

    /** Whether a stage code is KYC/eligibility (including legacy IDENTITY_KYC). */
    public static boolean isKycEligibilityStage(String stageCode) {
        if (stageCode == null) {
            return false;
        }
        String s = stageCode.trim().toUpperCase();
        return KYC_ELIGIBILITY.equals(s) || IDENTITY_KYC.equals(s) || "ELIGIBILITY".equals(s);
    }
}
