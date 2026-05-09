package com.los.core.service.flow.step;

import java.util.Map;

/**
 * Outcome of a single {@link IStepExecutor} run. This is a lightweight abstraction
 * (not a persisted workflow state machine).
 */
public record StepResult(
        boolean success,
        Map<String, Object> output,
        String nextActionHint) {

    public static StepResult ok(Map<String, Object> output) {
        return new StepResult(true, output != null ? output : Map.of(), null);
    }

    public static StepResult fail(Map<String, Object> output) {
        return new StepResult(false, output != null ? output : Map.of(), null);
    }
}
