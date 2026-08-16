package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Exact-ID producer. One registered producer satisfies exactly one canonical parameter ID
 * (or a multiplex that asserts metricCode == requestedId before returning).
 */
public interface ParameterProducer {

    String producerId();

    ProducerType producerType();

    /** Whether this producer claims the exact canonical ID. */
    boolean claims(String canonicalParameterId);

    /** Mode support (e.g. LIVE_ONLY refused in POLICY_TEST without fixture facts). */
    default boolean supports(EvaluationMode mode) {
        return true;
    }

    /**
     * Same authority as execute: true iff this producer can produce the ID when data is present.
     * Must not consult catalogue implemented/production_ready flags as proof.
     */
    boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver);

    ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver);
}
