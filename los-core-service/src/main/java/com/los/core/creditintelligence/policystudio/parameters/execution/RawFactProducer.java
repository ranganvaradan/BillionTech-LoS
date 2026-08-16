package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAW producer — reads the exact canonical ID from {@link EvaluationContext#facts()}.
 * Does not manufacture catalogue defaults.
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
        if (!ctx.facts().containsKey(canonicalParameterId) || ctx.facts().get(canonicalParameterId) == null) {
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
        Object fact = ctx.facts().get(canonicalParameterId);
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("sourceType", "RAW_FACT");
        prov.put("producerId", PRODUCER_ID);
        if (ctx.evaluationAsOf() != null) {
            prov.put("asOf", ctx.evaluationAsOf().toString());
        }
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.VALUE_AVAILABLE)
                .value(fact)
                .producerType(ProducerType.RAW)
                .producerId(PRODUCER_ID)
                .dependencies(List.of())
                .capability(true)
                .provenance(prov)
                .exactProducerPath("RawFactProducer ← facts[" + canonicalParameterId + "]")
                .build();
    }
}
