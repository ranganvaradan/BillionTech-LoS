package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MANUAL / INPUT producer for catalogue MANUAL parameters and Policy Test overlays.
 * Capability in POLICY_TEST is true when the parameter is MANUAL-typed or an input is supplied
 * for an exact ID that has no other producer (handled by the execution service fallback).
 */
public final class ManualInputProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "ManualInputProducer";

    @Override
    public String producerId() {
        return PRODUCER_ID;
    }

    @Override
    public ProducerType producerType() {
        return ProducerType.MANUAL_INPUT;
    }

    @Override
    public boolean claims(String canonicalParameterId) {
        return isManualCatalogue(canonicalParameterId);
    }

    @Override
    public boolean supports(EvaluationMode mode) {
        return mode == EvaluationMode.POLICY_TEST || mode == EvaluationMode.UNDERWRITING;
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        return claims(canonicalParameterId) && supports(ctx.mode());
    }

    @Override
    public ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        if (!claims(canonicalParameterId)) {
            return ExecutionResult.notExecutable(canonicalParameterId, "Not a MANUAL catalogue parameter");
        }
        Object v = null;
        if (ctx.inputs().containsKey(canonicalParameterId) && ctx.inputs().get(canonicalParameterId) != null) {
            v = ctx.inputs().get(canonicalParameterId);
        } else if (ctx.facts().containsKey(canonicalParameterId) && ctx.facts().get(canonicalParameterId) != null) {
            v = ctx.facts().get(canonicalParameterId);
        }
        if (v == null) {
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.INPUT_REQUIRED)
                    .producerType(ProducerType.MANUAL_INPUT)
                    .producerId(PRODUCER_ID)
                    .dependencies(List.of())
                    .capability(true)
                    .reason("Manual input required for " + canonicalParameterId)
                    .missingReason("Manual input required for " + canonicalParameterId)
                    .exactProducerPath(PRODUCER_ID + " (INPUT_REQUIRED)")
                    .build();
        }
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("sourceType", "MANUAL_INPUT");
        prov.put("producerId", PRODUCER_ID);
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.VALUE_AVAILABLE)
                .value(v)
                .producerType(ProducerType.MANUAL_INPUT)
                .producerId(PRODUCER_ID)
                .dependencies(List.of())
                .capability(true)
                .provenance(prov)
                .exactProducerPath(PRODUCER_ID + " ← inputs/facts[" + canonicalParameterId + "]")
                .build();
    }

    private static boolean isManualCatalogue(String id) {
        if (id == null || id.isBlank()) return false;
        return PolicyStudioConvergencePresenter.registry().findById(id)
                .map(CanonicalParameterDefinition::type)
                .map(t -> CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(t)
                        || "INPUT".equalsIgnoreCase(t)
                        || "APPLICATION_INPUT".equalsIgnoreCase(t))
                .orElse(false);
    }
}
