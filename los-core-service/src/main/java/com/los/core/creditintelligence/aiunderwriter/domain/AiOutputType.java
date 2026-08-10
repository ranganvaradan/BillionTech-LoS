package com.los.core.creditintelligence.aiunderwriter.domain;

/**
 * Non-authoritative AI assistive output types (A1).
 * Never APPROVE / REJECT / SANCTION / DISBURSE.
 */
public enum AiOutputType {
    NARRATIVE,
    EXPLANATION,
    QUESTION,
    ANOMALY,
    SCENARIO,
    ALTERNATE_STRUCTURE_SUGGESTION,
    CAM_DRAFT,
    CREDIT_NOTE_DRAFT,
    POLICY_CLARIFICATION_SUGGESTION
}
