package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Process-wide holder so static Gate3 / readiness helpers can consume the Spring-backed spine
 * without inventing a second execution authority.
 *
 * <p>Unit tests call {@link #install} with {@link ExecutionSpineProducerBootstrap#standalone}.
 * Spring installs via {@link ExecutionCapabilityAuthorityBootstrap}.
 * If neither has run, a producer-only standalone (no authored defs) is installed lazily.
 */
public final class ExecutionCapabilityAuthority {

    private static final Object LOCK = new Object();
    private static volatile CanonicalParameterExecutionService spine;

    private ExecutionCapabilityAuthority() {}

    public static void install(CanonicalParameterExecutionService service) {
        spine = service;
    }

    public static void clear() {
        spine = null;
    }

    public static boolean isInstalled() {
        return spine != null;
    }

    public static CanonicalParameterExecutionService require() {
        ensureInstalled();
        return spine;
    }

    /** Null-safe access for optional wiring during early bootstrap / cold paths. */
    public static CanonicalParameterExecutionService orNull() {
        return spine;
    }

    public static boolean hasExecutionCapability(String canonicalParameterId, EvaluationMode mode) {
        ensureInstalled();
        EvaluationContext ctx = EvaluationContext.builder().mode(mode).build();
        return spine.hasExecutionCapability(canonicalParameterId, ctx);
    }

    private static void ensureInstalled() {
        if (spine != null) {
            return;
        }
        synchronized (LOCK) {
            if (spine == null) {
                spine = ExecutionSpineProducerBootstrap.standalone((id, tenant) -> java.util.Optional.empty());
            }
        }
    }
}
