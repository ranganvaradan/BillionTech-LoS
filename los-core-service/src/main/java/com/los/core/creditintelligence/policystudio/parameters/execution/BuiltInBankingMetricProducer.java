package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.metrics.AdbBulkDepositAdjustmentCalculator;
import com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preserves existing Policy Test EMI bounce / Adjusted ADB calculators behind the spine.
 */
public final class BuiltInBankingMetricProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "BuiltInBankingMetricProducer";
    public static final String EMI_BOUNCE = "banking.emi_bounce_count_3m";
    public static final String ADB_3M = "banking.avg_daily_balance_3m";

    public static final String ENTITY_EMI_CONFIG = "emiBounceConfig";
    public static final String ENTITY_EMI_ENABLED = "emiBounceEnabled";
    public static final String ENTITY_ADB_CONFIG = "adbBulkConfig";
    public static final String ENTITY_ADB_ENABLED = "adbBulkEnabled";

    @Override
    public String producerId() {
        return PRODUCER_ID;
    }

    @Override
    public ProducerType producerType() {
        return ProducerType.BUILT_IN;
    }

    @Override
    public boolean claims(String canonicalParameterId) {
        return EMI_BOUNCE.equals(canonicalParameterId) || ADB_3M.equals(canonicalParameterId);
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        if (!claims(canonicalParameterId)) return false;
        if (EMI_BOUNCE.equals(canonicalParameterId)) {
            return Boolean.TRUE.equals(ctx.entities().get(ENTITY_EMI_ENABLED))
                    || ctx.inputs().containsKey(EMI_BOUNCE)
                    || ctx.facts().containsKey(EMI_BOUNCE);
        }
        if (ADB_3M.equals(canonicalParameterId)) {
            return Boolean.TRUE.equals(ctx.entities().get(ENTITY_ADB_ENABLED))
                    || ctx.inputs().containsKey(ADB_3M)
                    || ctx.facts().containsKey(ADB_3M);
        }
        return false;
    }

    @Override
    public ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        if (!claims(canonicalParameterId)) {
            return ExecutionResult.notExecutable(canonicalParameterId, "Not a banking built-in ID");
        }
        Object overlay = ctx.inputs().get(canonicalParameterId);
        if (overlay != null) {
            return value(canonicalParameterId, overlay, "inputs");
        }
        Object fact = ctx.facts().get(canonicalParameterId);
        if (fact != null && !Boolean.TRUE.equals(ctx.entities().get(ENTITY_ADB_ENABLED))) {
            // Prefer calculator overlay when ADB adjustment enabled
            if (!(ADB_3M.equals(canonicalParameterId) && Boolean.TRUE.equals(ctx.entities().get(ENTITY_ADB_ENABLED)))) {
                return value(canonicalParameterId, fact, "facts");
            }
        }

        LocalDate asOf = ctx.evaluationAsOf() != null ? ctx.evaluationAsOf() : LocalDate.of(2026, 8, 1);

        if (EMI_BOUNCE.equals(canonicalParameterId)) {
            if (!Boolean.TRUE.equals(ctx.entities().get(ENTITY_EMI_ENABLED))) {
                if (fact != null) return value(canonicalParameterId, fact, "facts");
                return dataMissing(canonicalParameterId, "EMI bounce binding not enabled on policy");
            }
            EmiBounceCountCalculator.Config config = resolveEmiConfig(ctx.entities().get(ENTITY_EMI_CONFIG));
            Map<String, Object> eval = EmiBounceCountCalculator.evaluate(
                    EmiBounceCountCalculator.stagingFixture(), config, asOf);
            if (EmiBounceCountCalculator.OUTCOME_PASS.equals(eval.get("outcome")) && eval.get("v") != null) {
                Map<String, Object> prov = new LinkedHashMap<>();
                prov.put("sourceType", "BUILT_IN");
                prov.put("calculator", "EmiBounceCountCalculator.V1");
                prov.put("howCalculated", eval.get("calculation"));
                return ExecutionResult.builder(canonicalParameterId)
                        .status(ExecutionStatus.VALUE_AVAILABLE)
                        .value(eval.get("v"))
                        .producerType(ProducerType.BUILT_IN)
                        .producerId(PRODUCER_ID)
                        .dependencies(List.of())
                        .capability(true)
                        .provenance(prov)
                        .exactProducerPath(PRODUCER_ID + " → EmiBounceCountCalculator [" + EMI_BOUNCE + "]")
                        .build();
            }
            return dataMissing(canonicalParameterId, String.valueOf(eval.get("outcome")));
        }

        if (ADB_3M.equals(canonicalParameterId)) {
            if (Boolean.TRUE.equals(ctx.entities().get(ENTITY_ADB_ENABLED))) {
                AdbBulkDepositAdjustmentCalculator.Config config =
                        resolveAdbConfig(ctx.entities().get(ENTITY_ADB_CONFIG));
                Map<String, Object> eval = AdbBulkDepositAdjustmentCalculator.evaluate(
                        AdbBulkDepositAdjustmentCalculator.stagingFixture(), config, asOf);
                if (AdbBulkDepositAdjustmentCalculator.OUTCOME_PASS.equals(eval.get("outcome"))
                        && eval.get("adjustedAdb") != null) {
                    Map<String, Object> prov = new LinkedHashMap<>();
                    prov.put("sourceType", "BUILT_IN");
                    prov.put("calculator", "AdbBulkDepositAdjustmentCalculator.V1");
                    prov.put("baseAdb", eval.get("baseAdb"));
                    prov.put("adjustedAdb", eval.get("adjustedAdb"));
                    return ExecutionResult.builder(canonicalParameterId)
                            .status(ExecutionStatus.VALUE_AVAILABLE)
                            .value(eval.get("adjustedAdb"))
                            .producerType(ProducerType.BUILT_IN)
                            .producerId(PRODUCER_ID)
                            .dependencies(List.of())
                            .capability(true)
                            .provenance(prov)
                            .exactProducerPath(PRODUCER_ID + " → AdbBulkDepositAdjustmentCalculator [" + ADB_3M + "]")
                            .build();
                }
            }
            if (fact != null) return value(canonicalParameterId, fact, "facts");
            return dataMissing(canonicalParameterId, "ADB value not available");
        }
        return ExecutionResult.notExecutable(canonicalParameterId, "unreachable");
    }

    private static EmiBounceCountCalculator.Config resolveEmiConfig(Object raw) {
        if (raw instanceof EmiBounceCountCalculator.Config c) return c;
        if (raw instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) m;
            return EmiBounceCountCalculator.Config.fromBody(body);
        }
        return EmiBounceCountCalculator.Config.defaults();
    }

    private static AdbBulkDepositAdjustmentCalculator.Config resolveAdbConfig(Object raw) {
        if (raw instanceof AdbBulkDepositAdjustmentCalculator.Config c) return c;
        if (raw instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) m;
            return AdbBulkDepositAdjustmentCalculator.Config.fromBody(body);
        }
        return AdbBulkDepositAdjustmentCalculator.Config.defaults();
    }

    private static ExecutionResult value(String id, Object v, String path) {
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("sourceType", "BUILT_IN");
        prov.put("path", path);
        return ExecutionResult.builder(id)
                .status(ExecutionStatus.VALUE_AVAILABLE)
                .value(v)
                .producerType(ProducerType.BUILT_IN)
                .producerId(PRODUCER_ID)
                .capability(true)
                .provenance(prov)
                .exactProducerPath(PRODUCER_ID + " ← " + path + "[" + id + "]")
                .build();
    }

    private static ExecutionResult dataMissing(String id, String reason) {
        return ExecutionResult.builder(id)
                .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                .producerType(ProducerType.BUILT_IN)
                .producerId(PRODUCER_ID)
                .capability(hasLooseCapability(id))
                .reason(reason)
                .exactProducerPath(PRODUCER_ID + " [" + id + "] DATA_NOT_AVAILABLE")
                .build();
    }

    private static boolean hasLooseCapability(String id) {
        return EMI_BOUNCE.equals(id) || ADB_3M.equals(id);
    }
}
