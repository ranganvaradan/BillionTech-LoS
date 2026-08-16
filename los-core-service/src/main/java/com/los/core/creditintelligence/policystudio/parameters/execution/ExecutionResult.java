package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of {@link CanonicalParameterExecutionService#resolveAndExecute}.
 * Capability queries use the same producer path; value may still be unavailable.
 */
public record ExecutionResult(
        String canonicalParameterId,
        ExecutionStatus status,
        Object value,
        ProducerType producerType,
        String producerId,
        List<String> dependencies,
        boolean capability,
        Map<String, Object> provenance,
        String reason,
        String exactProducerPath
) {
    public ExecutionResult {
        if (dependencies == null) dependencies = List.of();
        if (provenance == null) provenance = Map.of();
    }

    public boolean valueAvailable() {
        return status == ExecutionStatus.VALUE_AVAILABLE && value != null;
    }

    public Map<String, Object> toTraceMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalParameterId", canonicalParameterId);
        m.put("status", status == null ? null : status.name());
        m.put("value", value);
        m.put("producerType", producerType == null ? null : producerType.name());
        m.put("producerId", producerId);
        m.put("dependencies", dependencies);
        m.put("capability", capability);
        m.put("provenance", provenance);
        m.put("reason", reason);
        m.put("exactProducerPath", exactProducerPath);
        return m;
    }

    public static ExecutionResult notExecutable(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.NOT_EXECUTABLE, null, null, null, List.of(),
                false, Map.of(), reason, "none");
    }

    public static ExecutionResult calculationNotDefined(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.CALCULATION_NOT_DEFINED, null, ProducerType.AUTHORED_DERIVED,
                null, List.of(), false, Map.of(), reason, "authored:absent_or_invalid");
    }

    public static ExecutionResult error(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.ERROR, null, null, null, List.of(),
                false, Map.of(), reason, "error");
    }

    public static Builder builder(String canonicalParameterId) {
        return new Builder(canonicalParameterId);
    }

    public static final class Builder {
        private final String canonicalParameterId;
        private ExecutionStatus status;
        private Object value;
        private ProducerType producerType;
        private String producerId;
        private List<String> dependencies = new ArrayList<>();
        private boolean capability;
        private Map<String, Object> provenance = new LinkedHashMap<>();
        private String reason;
        private String exactProducerPath;

        private Builder(String canonicalParameterId) {
            this.canonicalParameterId = canonicalParameterId;
        }

        public Builder status(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder producerType(ProducerType producerType) {
            this.producerType = producerType;
            return this;
        }

        public Builder producerId(String producerId) {
            this.producerId = producerId;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies == null ? new ArrayList<>() : new ArrayList<>(dependencies);
            return this;
        }

        public Builder capability(boolean capability) {
            this.capability = capability;
            return this;
        }

        public Builder provenance(Map<String, Object> provenance) {
            this.provenance = provenance == null ? new LinkedHashMap<>() : new LinkedHashMap<>(provenance);
            return this;
        }

        public Builder putProvenance(String key, Object v) {
            this.provenance.put(key, v);
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder exactProducerPath(String exactProducerPath) {
            this.exactProducerPath = exactProducerPath;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(
                    canonicalParameterId, status, value, producerType, producerId,
                    List.copyOf(dependencies), capability, Map.copyOf(provenance),
                    reason, exactProducerPath);
        }
    }
}
