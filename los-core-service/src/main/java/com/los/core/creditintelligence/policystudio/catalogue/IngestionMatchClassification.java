package com.los.core.creditintelligence.policystudio.catalogue;

/**
 * POLICY-UX-2D — clause classification after document ingestion.
 */
public enum IngestionMatchClassification {
    EXACT_EXISTING_CAPABILITY,
    EXISTING_CAPABILITY_PARAMETER_CHANGE,
    EXISTING_CAPABILITY_MANUAL_DATA,
    NEW_AUTOMATABLE_RULE,
    MANUAL_INPUT,
    MANUAL_REVIEW,
    PRODUCT_CONFIG,
    DOCUMENT_REQUIREMENT,
    PORTFOLIO_CONTROL,
    SERVICING_RULE,
    NARRATIVE,
    AMBIGUOUS;

    public boolean underwritingExecutable() {
        return this == EXACT_EXISTING_CAPABILITY
                || this == EXISTING_CAPABILITY_PARAMETER_CHANGE
                || this == EXISTING_CAPABILITY_MANUAL_DATA
                || this == NEW_AUTOMATABLE_RULE;
    }

    public String displayGroup() {
        return switch (this) {
            case EXACT_EXISTING_CAPABILITY, EXISTING_CAPABILITY_PARAMETER_CHANGE,
                    EXISTING_CAPABILITY_MANUAL_DATA, NEW_AUTOMATABLE_RULE -> null; // use capability domain
            case MANUAL_INPUT, MANUAL_REVIEW -> "Decision / Review";
            case PRODUCT_CONFIG -> "Product / Configuration";
            case DOCUMENT_REQUIREMENT -> "Documents";
            case PORTFOLIO_CONTROL -> "Portfolio Controls";
            case SERVICING_RULE -> "Servicing";
            case NARRATIVE, AMBIGUOUS -> "Narrative / Excluded";
        };
    }
}
