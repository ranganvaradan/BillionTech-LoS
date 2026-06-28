package com.los.core.service.flow.step;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepExecutorRegistryTest {

    @Test
    void requireResolvesFirstSupportingExecutor() {
        IStepExecutor a = new StubExecutor("A", "first");
        IStepExecutor b = new StubExecutor("A", "second");
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of(a, b));
        IStepExecutor resolved = registry.require("A");
        assertSame(a, resolved);
    }

    @Test
    void requireThrowsWhenNoExecutor() {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        assertThrows(IllegalStateException.class, () -> registry.require("NONE"));
    }

    @Test
    void resolvedExecutorExecutes() {
        UUID id = UUID.randomUUID();
        IStepExecutor ex = new StubExecutor("KYC", "only");
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of(ex));
        StepResult result = registry.require("KYC").execute(id, Map.of("k", "v"));
        assertNotNull(result);
    }

    private static final class StubExecutor implements IStepExecutor {
        private final String type;
        @SuppressWarnings("unused")
        private final String name;

        StubExecutor(String type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean supports(String stepType) {
            return type.equals(stepType);
        }

        @Override
        public StepResult execute(UUID applicationId, Map<String, Object> context) {
            return StepResult.ok(Map.of("applicationId", applicationId, "context", context));
        }
    }
}
