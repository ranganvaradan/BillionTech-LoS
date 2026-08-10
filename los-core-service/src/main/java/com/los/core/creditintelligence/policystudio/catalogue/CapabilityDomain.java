package com.los.core.creditintelligence.policystudio.catalogue;

/**
 * Business grouping for the universal credit capability catalogue (POLICY-UX-2A).
 */
public enum CapabilityDomain {
    ELIGIBILITY("Eligibility"),
    KYC("KYC"),
    BUREAU("Bureau"),
    BANKING("Banking"),
    FINANCIAL("Financial"),
    GST_BUSINESS("GST / Business"),
    COLLATERAL("Collateral"),
    RISK_EXCEPTIONS("Risk / Exceptions"),
    LIMIT("Limit"),
    PRICING("Pricing"),
    DECISION_MANUAL_REVIEW("Decision / Manual Review");

    private final String displayName;

    CapabilityDomain(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
