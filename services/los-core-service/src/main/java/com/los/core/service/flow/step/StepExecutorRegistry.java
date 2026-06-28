package com.los.core.service.flow.step;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the first {@link IStepExecutor} that {@linkplain IStepExecutor#supports(String)} the step type.
 */
@Component
public class StepExecutorRegistry {

    private final List<IStepExecutor> executors;

    public StepExecutorRegistry(List<IStepExecutor> executors) {
        this.executors = executors;
    }

    public IStepExecutor require(String stepType) {
        return executors.stream()
                .filter(e -> e.supports(stepType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No IStepExecutor registered for stepType=" + stepType));
    }
}
