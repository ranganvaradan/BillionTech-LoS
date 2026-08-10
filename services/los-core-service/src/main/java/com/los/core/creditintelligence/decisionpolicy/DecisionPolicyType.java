package com.los.core.creditintelligence.decisionpolicy;

/**
 * Policy catalogue type evolution.
 * Existing {@code CREDIT_POLICY} documents continue to load unchanged.
 * {@code DECISION_POLICY} is an additive unified type; credit-only packages remain valid.
 */
public final class DecisionPolicyType {

    public static final String CREDIT_POLICY = "CREDIT_POLICY";
    public static final String DECISION_POLICY = "DECISION_POLICY";
    public static final String BUREAU_POLICY = "BUREAU_POLICY";
    public static final String BANKING_POLICY = "BANKING_POLICY";

    private DecisionPolicyType() {}

    /** True for types that may contain KYC/Eligibility sections. */
    public static boolean supportsKycEligibilitySection(String policyType) {
        String t = normalize(policyType);
        return DECISION_POLICY.equals(t);
    }

    /** True for types that may contain credit underwriting rules. */
    public static boolean supportsCreditSection(String policyType) {
        String t = normalize(policyType);
        return CREDIT_POLICY.equals(t)
                || DECISION_POLICY.equals(t)
                || BUREAU_POLICY.equals(t)
                || BANKING_POLICY.equals(t);
    }

    /**
     * Backward-compatible: unknown/null → CREDIT_POLICY.
     * CREDIT_POLICY remains the default for existing documents.
     */
    public static String normalize(String policyType) {
        if (policyType == null || policyType.isBlank()) {
            return CREDIT_POLICY;
        }
        return policyType.trim().toUpperCase();
    }

    public static boolean isKnown(String policyType) {
        String t = normalize(policyType);
        return CREDIT_POLICY.equals(t)
                || DECISION_POLICY.equals(t)
                || BUREAU_POLICY.equals(t)
                || BANKING_POLICY.equals(t);
    }
}
