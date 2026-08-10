package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;

/**
 * Thread-local holder for the active {@link CiEvaluationContext} during shadow/replay.
 * Transitional: used so LegacyUnderwritingContextAdapter / metric services can resolve
 * pinned metrics and as-of clocks without signature churn on every call site.
 */
public final class EvaluationContextHolder {

    private static final ThreadLocal<CiEvaluationContext> CURRENT = new ThreadLocal<>();

    private EvaluationContextHolder() {
    }

    public static void set(CiEvaluationContext ctx) {
        CURRENT.set(ctx);
    }

    public static CiEvaluationContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
