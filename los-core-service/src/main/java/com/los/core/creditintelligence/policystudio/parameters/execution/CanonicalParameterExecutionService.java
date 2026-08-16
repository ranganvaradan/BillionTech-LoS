package com.los.core.creditintelligence.policystudio.parameters.execution;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical Parameter Execution Spine — single authority for capability and execution.
 * Catalogue {@code implemented}/{@code production_ready}/{@code howCalculated} are never
 * treated as execution proof.
 */
@Service
public class CanonicalParameterExecutionService implements DependencyResolver {

    private final ProducerRegistry registry;

    public CanonicalParameterExecutionService(ProducerRegistry registry) {
        this.registry = registry;
    }

    public ProducerRegistry registry() {
        return registry;
    }

    /**
     * Capability and execution share this method. Callers that only need capability
     * should inspect {@link ExecutionResult#capability()} (value may still be unavailable).
     */
    @Override
    public ExecutionResult resolveAndExecute(String canonicalParameterId, EvaluationContext ctx) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return ExecutionResult.notExecutable(canonicalParameterId, "Blank canonical parameter id");
        }
        if (ctx == null) {
            return ExecutionResult.error(canonicalParameterId, "EvaluationContext is required");
        }
        if (!ctx.pushResolving(canonicalParameterId)) {
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.ERROR)
                    .capability(false)
                    .reason("Cycle detected while resolving " + canonicalParameterId)
                    .exactProducerPath("cycle")
                    .build();
        }
        try {
            Optional<ParameterProducer> producer = registry.find(canonicalParameterId);
            if (producer.isEmpty()) {
                // Policy Test may supply an exact-ID overlay even when no producer exists —
                // report as POLICY_TEST_INPUT with capability false (no producer authority).
                if (ctx.mode() == EvaluationMode.POLICY_TEST
                        && ctx.inputs().get(canonicalParameterId) != null) {
                    Object v = ctx.inputs().get(canonicalParameterId);
                    Map<String, Object> prov = new LinkedHashMap<>();
                    prov.put("sourceType", "POLICY_TEST_INPUT_WITHOUT_PRODUCER");
                    prov.put("warning", "Value supplied for simulation only; no registered producer");
                    return ExecutionResult.builder(canonicalParameterId)
                            .status(ExecutionStatus.VALUE_AVAILABLE)
                            .value(v)
                            .producerType(ProducerType.POLICY_TEST_INPUT)
                            .producerId("PolicyTestInputFallback")
                            .capability(false)
                            .provenance(prov)
                            .reason("No registered producer; Policy Test input overlay used")
                            .exactProducerPath("PolicyTestInputFallback ← inputs[" + canonicalParameterId + "]")
                            .build();
                }
                return ExecutionResult.notExecutable(canonicalParameterId,
                        "No registered executable producer for exact ID (catalogue flags are not proof)");
            }
            ParameterProducer p = producer.get();
            if (!p.supports(ctx.mode())) {
                return ExecutionResult.builder(canonicalParameterId)
                        .status(ExecutionStatus.NOT_EXECUTABLE)
                        .producerType(p.producerType())
                        .producerId(p.producerId())
                        .capability(false)
                        .reason("Producer does not support mode " + ctx.mode())
                        .exactProducerPath(p.producerId() + " (mode refused)")
                        .build();
            }
            return p.execute(canonicalParameterId, ctx, this);
        } catch (Exception ex) {
            return ExecutionResult.error(canonicalParameterId,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            ctx.popResolving(canonicalParameterId);
        }
    }

    /**
     * Same authority as {@link #resolveAndExecute} — does not consult a parallel service.
     * Note: may still execute producers; for pure capability without value materialization
     * producers should short-circuit in {@link ParameterProducer#hasCapability}.
     */
    public boolean hasExecutionCapability(String canonicalParameterId, EvaluationContext ctx) {
        Optional<ParameterProducer> producer = registry.find(canonicalParameterId);
        if (producer.isEmpty()) {
            return false;
        }
        ParameterProducer p = producer.get();
        if (!p.supports(ctx.mode())) {
            return false;
        }
        return p.hasCapability(canonicalParameterId, ctx, this);
    }
}
