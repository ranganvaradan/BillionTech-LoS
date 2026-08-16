package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared evaluation host context for Policy Test / W6 / underwriting.
 * Producers read facts/entities/inputs — they do not invent catalogue defaults.
 */
public final class EvaluationContext {

    private final EvaluationMode mode;
    private final LocalDate evaluationAsOf;
    private final UUID tenantId;
    private final UUID documentId;
    private final UUID applicationId;
    private final Map<String, Object> facts;
    private final Map<String, Object> inputs;
    private final Map<String, Object> entities;
    private final Deque<String> resolvingStack = new ArrayDeque<>();

    private EvaluationContext(Builder b) {
        this.mode = b.mode == null ? EvaluationMode.POLICY_TEST : b.mode;
        this.evaluationAsOf = b.evaluationAsOf;
        this.tenantId = b.tenantId;
        this.documentId = b.documentId;
        this.applicationId = b.applicationId;
        this.facts = b.facts;
        this.inputs = b.inputs;
        this.entities = b.entities;
    }

    public EvaluationMode mode() {
        return mode;
    }

    public LocalDate evaluationAsOf() {
        return evaluationAsOf;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID documentId() {
        return documentId;
    }

    public UUID applicationId() {
        return applicationId;
    }

    public Map<String, Object> facts() {
        return facts;
    }

    public Map<String, Object> inputs() {
        return inputs;
    }

    public Map<String, Object> entities() {
        return entities;
    }

    public boolean pushResolving(String canonicalId) {
        if (resolvingStack.contains(canonicalId)) {
            return false;
        }
        resolvingStack.push(canonicalId);
        return true;
    }

    public void popResolving(String canonicalId) {
        if (!resolvingStack.isEmpty() && Objects.equals(resolvingStack.peek(), canonicalId)) {
            resolvingStack.pop();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private EvaluationMode mode = EvaluationMode.POLICY_TEST;
        private LocalDate evaluationAsOf;
        private UUID tenantId;
        private UUID documentId;
        private UUID applicationId;
        private final Map<String, Object> facts = new LinkedHashMap<>();
        private final Map<String, Object> inputs = new LinkedHashMap<>();
        private final Map<String, Object> entities = new LinkedHashMap<>();

        public Builder mode(EvaluationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder evaluationAsOf(LocalDate evaluationAsOf) {
            this.evaluationAsOf = evaluationAsOf;
            return this;
        }

        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder documentId(UUID documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder applicationId(UUID applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public Builder fact(String canonicalId, Object value) {
            if (canonicalId != null && !canonicalId.isBlank() && value != null) {
                facts.put(canonicalId, value);
            }
            return this;
        }

        public Builder facts(Map<String, Object> more) {
            if (more != null) {
                more.forEach(this::fact);
            }
            return this;
        }

        public Builder input(String canonicalId, Object value) {
            if (canonicalId != null && !canonicalId.isBlank() && value != null) {
                inputs.put(canonicalId, value);
            }
            return this;
        }

        public Builder inputs(Map<String, Object> more) {
            if (more != null) {
                more.forEach(this::input);
            }
            return this;
        }

        public Builder entity(String key, Object value) {
            if (key != null && value != null) {
                entities.put(key, value);
            }
            return this;
        }

        public EvaluationContext build() {
            return new EvaluationContext(this);
        }
    }

    /** Unmodifiable snapshot of resolving stack for diagnostics. */
    public Iterable<String> resolvingPath() {
        return Collections.unmodifiableCollection(resolvingStack);
    }
}
