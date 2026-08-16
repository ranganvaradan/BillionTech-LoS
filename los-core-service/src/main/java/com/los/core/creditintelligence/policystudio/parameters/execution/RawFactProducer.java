package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAW producer — reads the exact canonical ID from {@link EvaluationContext#facts()}.
 * Does not manufacture catalogue defaults.
 * Empty collections are VALUE_AVAILABLE (present but empty); missing keys are DATA_NOT_AVAILABLE.
 */
public final class RawFactProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "RawFactProducer";

    private final Set<String> claimedIds;

    public RawFactProducer(Set<String> claimedIds) {
        this.claimedIds = claimedIds == null ? Set.of() : Set.copyOf(claimedIds);
    }

    @Override
    public String producerId() {
        return PRODUCER_ID;
    }

    @Override
    public ProducerType producerType() {
        return ProducerType.RAW;
    }

    @Override
    public boolean claims(String canonicalParameterId) {
        return claimedIds.contains(canonicalParameterId);
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        return claims(canonicalParameterId) && supports(ctx.mode());
    }

    @Override
    public ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        if (!claims(canonicalParameterId)) {
            return ExecutionResult.notExecutable(canonicalParameterId, "RAW producer does not claim " + canonicalParameterId);
        }
        // Overlay: key present with non-null value (0 / false / empty collection are valid values)
        if (ctx.inputs().containsKey(canonicalParameterId) && ctx.inputs().get(canonicalParameterId) != null) {
            Object fromInput = ctx.inputs().get(canonicalParameterId);
            Map<String, Object> prov = new LinkedHashMap<>();
            prov.put("sourceType", "POLICY_TEST_INPUT_OVERLAY");
            prov.put("producerId", PRODUCER_ID);
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.VALUE_AVAILABLE)
                    .value(fromInput)
                    .producerType(ProducerType.RAW)
                    .producerId(PRODUCER_ID)
                    .dependencies(List.of())
                    .capability(true)
                    .provenance(prov)
                    .exactProducerPath("RawFactProducer ← inputs[" + canonicalParameterId + "]")
                    .build();
        }
        if (!ctx.facts().containsKey(canonicalParameterId)) {
            return missing(canonicalParameterId);
        }
        Object factVal = ctx.facts().get(canonicalParameterId);
        // Null scalar: absent value. Empty Collection: present empty (VALUE_AVAILABLE).
        if (factVal == null) {
            return missing(canonicalParameterId);
        }
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("sourceType", "RAW_FACT");
        prov.put("producerId", PRODUCER_ID);
        if (factVal instanceof Collection<?> c) {
            prov.put("rowCount", c.size());
        }
        if (ctx.evaluationAsOf() != null) {
            prov.put("asOf", ctx.evaluationAsOf().toString());
        }
        Object snap = ctx.entities().get(CanonicalFactMaterializer.ENTITY_SOURCE_SNAPSHOT_VERSION);
        if (snap != null) {
            prov.put("sourceSnapshotVersion", String.valueOf(snap));
        }
        Object mat = ctx.entities().get(CanonicalFactMaterializer.ENTITY_MATERIALIZATION_PROVENANCE);
        if (mat != null) {
            prov.put("materialization", mat);
        }
        Object br = ctx.entities().get(CanonicalFactMaterializer.ENTITY_BUREAU_REPORT_ID);
        if (br != null) {
            prov.put("bureauReportId", String.valueOf(br));
        }
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.VALUE_AVAILABLE)
                .value(factVal)
                .producerType(ProducerType.RAW)
                .producerId(PRODUCER_ID)
                .dependencies(List.of())
                .capability(true)
                .provenance(prov)
                .exactProducerPath("RawFactProducer ← facts[" + canonicalParameterId + "]")
                .build();
    }

    private static ExecutionResult missing(String canonicalParameterId) {
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                .producerType(ProducerType.RAW)
                .producerId(PRODUCER_ID)
                .capability(true)
                .reason("Exact RAW fact not present in EvaluationContext: " + canonicalParameterId)
                .missingReason("Exact RAW fact not present in EvaluationContext: " + canonicalParameterId)
                .exactProducerPath("RawFactProducer ← facts[" + canonicalParameterId + "] (missing)")
                .build();
    }
}
