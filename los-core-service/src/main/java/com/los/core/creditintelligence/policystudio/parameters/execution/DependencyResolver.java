package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Recursive dependency resolution — always the same {@link CanonicalParameterExecutionService}.
 */
@FunctionalInterface
public interface DependencyResolver {
    ExecutionResult resolveAndExecute(String canonicalParameterId, EvaluationContext ctx);

    default boolean hasExecutionCapability(String canonicalParameterId, EvaluationContext ctx) {
        ExecutionResult r = resolveAndExecute(canonicalParameterId, ctx);
        return r != null && r.capability();
    }
}
