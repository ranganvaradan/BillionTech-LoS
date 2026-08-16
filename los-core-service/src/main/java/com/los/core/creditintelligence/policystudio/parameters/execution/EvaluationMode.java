package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Host mode for the canonical parameter execution spine.
 * Same service; hosts differ only in how they populate {@link EvaluationContext}.
 */
public enum EvaluationMode {
    POLICY_TEST,
    W6_ACQUISITION,
    UNDERWRITING
}
