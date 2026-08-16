package com.los.core.creditintelligence.policystudio.parameters.execution;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical Parameter Execution Spine — sole authority for capability and execution status.
 * Catalogue {@code implemented}/{@code production_ready}/{@code howCalculated} are never
 * treated as execution proof. Wave-1: all results enriched to one contract shape.
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
            return ExecutionResultContractEnricher.enrich(
                    ExecutionResult.notExecutable(canonicalParameterId, "Blank canonical parameter id"),
                    ctx, canonicalParameterId);
        }
        if (ctx == null) {
            return ExecutionResult.error(canonicalParameterId, "EvaluationContext is required");
        }
        if (!ctx.pushResolving(canonicalParameterId)) {
            return ExecutionResultContractEnricher.enrich(
                    ExecutionResult.builder(canonicalParameterId)
                            .status(ExecutionStatus.ERROR)
                            .capability(false)
                            .reason("Cycle detected while resolving " + canonicalParameterId)
                            .exactProducerPath("cycle")
                            .build(),
                    ctx, canonicalParameterId);
        }
        try {
            Optional<ParameterProducer> producer = registry.find(canonicalParameterId);
            if (producer.isEmpty()) {
                // Policy Test may supply an exact-ID overlay even when no producer exists —
                // value may be available for simulation; capability remains false.
                if (ctx.mode() == EvaluationMode.POLICY_TEST
                        && contextHasPresentInput(ctx, canonicalParameterId)) {
                    Object v = ctx.inputs().get(canonicalParameterId);
                    Map<String, Object> prov = new LinkedHashMap<>();
                    prov.put("sourceType", "POLICY_TEST_INPUT_WITHOUT_PRODUCER");
                    prov.put("warning", "Value supplied for simulation only; no registered producer");
                    prov.put("simulationOnly", true);
                    prov.put("simulatedValueDoesNotImplyExecutable", true);
                    return ExecutionResultContractEnricher.enrich(
                            ExecutionResult.builder(canonicalParameterId)
                                    .status(ExecutionStatus.VALUE_AVAILABLE)
                                    .value(v)
                                    .producerType(ProducerType.POLICY_TEST_INPUT)
                                    .producerId("PolicyTestInputFallback")
                                    .capability(false)
                                    .provenance(prov)
                                    .reason("No registered producer; Policy Test input overlay used")
                                    .exactProducerPath("PolicyTestInputFallback ← inputs[" + canonicalParameterId + "]")
                                    .build(),
                            ctx, canonicalParameterId);
                }
                return ExecutionResultContractEnricher.enrich(
                        ExecutionResult.notExecutable(canonicalParameterId,
                                "No registered executable producer for exact ID (catalogue flags are not proof)"),
                        ctx, canonicalParameterId);
            }
            ParameterProducer p = producer.get();
            if (!p.supports(ctx.mode())) {
                return ExecutionResultContractEnricher.enrich(
                        ExecutionResult.builder(canonicalParameterId)
                                .status(ExecutionStatus.NOT_EXECUTABLE)
                                .producerType(p.producerType())
                                .producerId(p.producerId())
                                .capability(false)
                                .reason("Producer does not support mode " + ctx.mode())
                                .exactProducerPath(p.producerId() + " (mode refused)")
                                .build(),
                        ctx, canonicalParameterId);
            }
            return ExecutionResultContractEnricher.enrich(
                    p.execute(canonicalParameterId, ctx, this), ctx, canonicalParameterId);
        } catch (Exception ex) {
            return ExecutionResultContractEnricher.enrich(
                    ExecutionResult.error(canonicalParameterId,
                            ex.getClass().getSimpleName() + ": " + ex.getMessage()),
                    ctx, canonicalParameterId);
        } finally {
            ctx.popResolving(canonicalParameterId);
        }
    }

    /**
     * Same authority as {@link #resolveAndExecute} — does not consult a parallel service.
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

    /** Present input including numeric zero / boolean false (null only = absent). */
    static boolean contextHasPresentInput(EvaluationContext ctx, String id) {
        if (ctx == null || id == null) {
            return false;
        }
        return ctx.inputs().containsKey(id) && ctx.inputs().get(id) != null;
    }
}
