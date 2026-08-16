package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical result of {@link CanonicalParameterExecutionService#resolveAndExecute}.
 *
 * <p><b>Capability</b> (producer exists for this exact ID/mode) is independent of
 * <b>value availability</b> (status == VALUE_AVAILABLE). Zero / false / empty-but-valid
 * collections are values — not missing data.
 *
 * <p>Catalogue {@code implemented}/{@code production_ready} are never execution truth.
 * Certification remains NOT ESTABLISHED (not part of this contract's executable truth).
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
        String exactProducerPath,
        String valueType,
        String unit,
        String producerVersion,
        LocalDate evaluationAsOf,
        String sourceSnapshotVersion,
        String missingReason,
        EvaluationMode mode
) {
    public ExecutionResult {
        if (dependencies == null) dependencies = List.of();
        if (provenance == null) provenance = Map.of();
        if (missingReason == null && status != null && status != ExecutionStatus.VALUE_AVAILABLE
                && reason != null && !reason.isBlank()) {
            missingReason = reason;
        }
    }

    /**
     * True iff status is {@link ExecutionStatus#VALUE_AVAILABLE}.
     * Value may be {@code 0}, {@code false}, {@code ""}, or an empty valid collection.
     */
    public boolean valueAvailable() {
        return status == ExecutionStatus.VALUE_AVAILABLE;
    }

    /** Wave-1 family label: RAW / BUILT_IN / AUTHORED / MANUAL / POLICY_TEST_INPUT. */
    public String producerTypeFamily() {
        if (producerType == null) {
            return null;
        }
        return switch (producerType) {
            case RAW -> "RAW";
            case BUILT_IN -> "BUILT_IN";
            case AUTHORED_DERIVED -> "AUTHORED";
            case MANUAL_INPUT -> "MANUAL";
            case POLICY_TEST_INPUT -> "POLICY_TEST_INPUT";
        };
    }

    public boolean simulatedValue() {
        if (producerType == ProducerType.POLICY_TEST_INPUT) {
            return true;
        }
        Object st = provenance.get("sourceType");
        return st != null && String.valueOf(st).contains("POLICY_TEST_INPUT");
    }

    /**
     * Full consumer-facing contract map — single shape for W6 / PT / Scorecard / D&amp;P / Studio.
     */
    public Map<String, Object> toCanonicalContractMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalId", canonicalParameterId);
        m.put("canonicalParameterId", canonicalParameterId);
        m.put("status", status == null ? null : status.name());
        m.put("value", value);
        m.put("valueAvailable", valueAvailable());
        m.put("valueType", valueType);
        m.put("unit", unit);
        m.put("producerType", producerType == null ? null : producerType.name());
        m.put("producerTypeFamily", producerTypeFamily());
        m.put("producerId", producerId);
        m.put("producerVersion", producerVersion);
        m.put("dependencies", dependencies);
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        m.put("sourceSnapshotVersion", sourceSnapshotVersion);
        m.put("provenance", provenance);
        m.put("missingReason", missingReason != null ? missingReason : reason);
        m.put("reason", reason);
        m.put("mode", mode == null ? null : mode.name());
        m.put("capability", capability);
        m.put("exactProducerPath", exactProducerPath);
        m.put("simulatedValue", simulatedValue());
        m.put("executionAuthority", "CanonicalParameterExecutionService");
        m.put("certificationStatus", "NOT_ESTABLISHED");
        return m;
    }

    public Map<String, Object> toTraceMap() {
        Map<String, Object> m = toCanonicalContractMap();
        // Keep legacy keys used by Wave 0 / existing traces
        m.put("canonicalParameterId", canonicalParameterId);
        return m;
    }

    /**
     * Stamp mode / asOf / snapshot / catalogue type-unit without changing status semantics.
     */
    public ExecutionResult enrichFromContext(EvaluationContext ctx, String valueType, String unit) {
        if (ctx == null) {
            return this;
        }
        String snap = null;
        Object sv = ctx.entities().get("sourceSnapshotVersion");
        if (sv != null) {
            snap = String.valueOf(sv);
        }
        String ver = producerVersion;
        if (ver == null && provenance.get("metricVersion") != null) {
            ver = String.valueOf(provenance.get("metricVersion"));
        }
        if (ver == null && provenance.get("definitionVersion") != null) {
            ver = String.valueOf(provenance.get("definitionVersion"));
        }
        String miss = missingReason;
        if (miss == null && status != ExecutionStatus.VALUE_AVAILABLE) {
            miss = reason;
        }
        return new ExecutionResult(
                canonicalParameterId, status, value, producerType, producerId, dependencies,
                capability, provenance, reason, exactProducerPath,
                valueType != null ? valueType : this.valueType,
                unit != null ? unit : this.unit,
                ver,
                evaluationAsOf != null ? evaluationAsOf : ctx.evaluationAsOf(),
                sourceSnapshotVersion != null ? sourceSnapshotVersion : snap,
                miss,
                mode != null ? mode : ctx.mode());
    }

    public static ExecutionResult notExecutable(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.NOT_EXECUTABLE, null, null, null, List.of(),
                false, Map.of(), reason, "none",
                null, null, null, null, null, reason, null);
    }

    public static ExecutionResult calculationNotDefined(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.CALCULATION_NOT_DEFINED, null, ProducerType.AUTHORED_DERIVED,
                null, List.of(), false, Map.of(), reason, "authored:absent_or_invalid",
                null, null, null, null, null, reason, null);
    }

    public static ExecutionResult error(String id, String reason) {
        return new ExecutionResult(
                id, ExecutionStatus.ERROR, null, null, null, List.of(),
                false, Map.of(), reason, "error",
                null, null, null, null, null, reason, null);
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
        private String valueType;
        private String unit;
        private String producerVersion;
        private LocalDate evaluationAsOf;
        private String sourceSnapshotVersion;
        private String missingReason;
        private EvaluationMode mode;

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

        public Builder valueType(String valueType) {
            this.valueType = valueType;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder producerVersion(String producerVersion) {
            this.producerVersion = producerVersion;
            return this;
        }

        public Builder evaluationAsOf(LocalDate evaluationAsOf) {
            this.evaluationAsOf = evaluationAsOf;
            return this;
        }

        public Builder sourceSnapshotVersion(String sourceSnapshotVersion) {
            this.sourceSnapshotVersion = sourceSnapshotVersion;
            return this;
        }

        public Builder missingReason(String missingReason) {
            this.missingReason = missingReason;
            return this;
        }

        public Builder mode(EvaluationMode mode) {
            this.mode = mode;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(
                    canonicalParameterId, status, value, producerType, producerId,
                    List.copyOf(dependencies), capability, Map.copyOf(provenance),
                    reason, exactProducerPath,
                    valueType, unit, producerVersion, evaluationAsOf, sourceSnapshotVersion,
                    missingReason, mode);
        }
    }
}
