package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Wave-1 documentation of which vocabularies are execution vs adjacent concerns.
 * Not a runtime switch — prevents collapsing distinct business concepts.
 */
public final class ExecutionStatusVocabularyGuide {

    private ExecutionStatusVocabularyGuide() {}

    /** Statuses owned by {@link ExecutionStatus} / CPES. */
    public static final String LAYER_EXECUTION = "EXECUTION_STATUS";

    /** W6 / acquisition adapters — SOURCE_ACQUIRED, PROVIDER_FAILED, CONSENT_PENDING, … */
    public static final String LAYER_ACQUISITION = "ACQUISITION_STATUS";

    /** Derived calc DEFINED/TESTED/PRODUCTION_READY; research READY_FOR_REVIEW — not execution. */
    public static final String LAYER_AUTHORING = "AUTHORING_STATUS";

    /** Policy DSL PASS/FAIL/REFER/DATA_INSUFFICIENT; rule disposition ACCEPTED — not execution. */
    public static final String LAYER_POLICY = "POLICY_STATUS";

    /** Production certification — NOT ESTABLISHED in Wave 1. */
    public static final String LAYER_CERTIFICATION = "CERTIFICATION_STATUS";

    /** D&amp;P SUPPORT_SUPPORTED_RAW / Gate3 labels — display projection only. */
    public static final String LAYER_LEGACY_DISPLAY = "LEGACY_DISPLAY_STATUS";
}
