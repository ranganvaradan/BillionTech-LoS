package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BUILT_IN adapter for IDs genuinely emitted by {@link BureauMetricService}.
 * Asserts exact metricCode == requested canonical ID. Related IDs never substitute.
 *
 * <p>In Policy Test, values may already be present as exact-ID facts/inputs (fixture or
 * precomputed). Live entity compute is optional when entities are loaded into context.
 */
public final class BuiltInBureauMetricProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "BureauMetricService.BUILT_IN";

    /** Exact IDs emitted by {@link BureauMetricService#computeAndPersist}. */
    public static final Set<String> EMITTED_IDS = Set.of(
            BureauMetricService.LIVE_UNSECURED,
            BureauMetricService.TOTAL_LIVE_EXPOSURE,
            BureauMetricService.SECURED_LIVE_EXPOSURE,
            BureauMetricService.UNSECURED_LIVE_EXPOSURE,
            BureauMetricService.TOTAL_MONTHLY_OBLIGATION,
            BureauMetricService.MAX_DPD_6M,
            BureauMetricService.MAX_DPD_12M,
            BureauMetricService.MAX_DPD_24M,
            BureauMetricService.RECENT_INQUIRIES_90D,
            BureauMetricService.SETTLED_ACCOUNT_COUNT,
            BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT,
            BureauMetricService.WRITEOFF_NON_CC,
            BureauMetricService.WRITEOFF_CC,
            BureauMetricService.STATUS_NTC,
            BureauMetricService.INQUIRIES_CURRENT_MONTH,
            BureauMetricService.INQUIRIES_LAST_3M,
            BureauMetricService.DPD_30_PLUS_COUNT_6M,
            BureauMetricService.DPD_60_PLUS_COUNT_6M,
            BureauMetricService.DPD_90_PLUS_COUNT_6M,
            BureauMetricService.MONTHS_SINCE_LAST_DELINQUENCY,
            BureauMetricService.OLDEST_TRADELINE_VINTAGE_MONTHS,
            BureauMetricService.AVERAGE_ACCOUNT_AGE_MONTHS,
            BureauMetricService.CC_OVERDUE_AMOUNT,
            BureauMetricService.CC_UTILISATION,
            BureauMetricService.OVERDUE_AMOUNT,
            BureauMetricService.OVERDUE_AGE_MONTHS,
            BureauMetricService.CREDIT_AFTER_OVERDUE_EXISTS,
            BureauMetricService.CREDIT_AFTER_OVERDUE_CLEAN_HISTORY_MONTHS,
            BureauMetricService.NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT,
            BureauMetricService.SUIT_FILED_ACCOUNT_COUNT,
            BureauMetricService.PAN_DISTINCT_COUNT,
            BureauMetricService.RESTRUCTURED_ACCOUNT_COUNT,
            BureauMetricService.DBT_ACCOUNT_COUNT,
            BureauMetricService.PWOS_ACCOUNT_COUNT,
            BureauMetricService.LSS_ACCOUNT_COUNT
    );

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
        return EMITTED_IDS.contains(canonicalParameterId);
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        return claims(canonicalParameterId);
    }

    @Override
    public ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        if (!claims(canonicalParameterId)) {
            return ExecutionResult.notExecutable(canonicalParameterId,
                    "Not a BureauMetricService emitted metricCode: " + canonicalParameterId);
        }
        // Exact-ID assert: only return when the key equals the requested ID (no related-ID map).
        Object fromInput = ctx.inputs().get(canonicalParameterId);
        if (fromInput != null) {
            return valueResult(canonicalParameterId, fromInput, "inputs", "POLICY_TEST_INPUT_OVERLAY");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> precomputed = ctx.entities().get(PersistedDerivedMetricSpine.PRECOMPUTED_METRICS)
                instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        if (precomputed.containsKey(canonicalParameterId) && precomputed.get(canonicalParameterId) != null) {
            Object v = precomputed.get(canonicalParameterId);
            return valueResult(canonicalParameterId, v,
                    "entities." + PersistedDerivedMetricSpine.PRECOMPUTED_METRICS,
                    PersistedDerivedMetricSpine.SOURCE_PERSISTED_CANONICAL_DERIVED,
                    persistedProvenance(ctx, canonicalParameterId));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> statuses = ctx.entities().get(PersistedDerivedMetricSpine.PRECOMPUTED_METRIC_STATUSES)
                instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        if (statuses.containsKey(canonicalParameterId)) {
            String st = String.valueOf(statuses.get(canonicalParameterId));
            Map<String, Object> prov = persistedProvenance(ctx, canonicalParameterId);
            prov.put("persistedStatus", st);
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                    .producerType(ProducerType.BUILT_IN)
                    .producerId(PRODUCER_ID)
                    .capability(true)
                    .reason("Persisted exact metricCode " + canonicalParameterId + " status=" + st
                            + "; zero not invented")
                    .provenance(prov)
                    .exactProducerPath(PRODUCER_ID + " ← persisted[" + canonicalParameterId + "] (" + st + ")")
                    .build();
        }
        Object fromFact = ctx.facts().get(canonicalParameterId);
        if (fromFact != null) {
            return valueResult(canonicalParameterId, fromFact, "facts", "CONTEXT_FACT_EXACT_ID");
        }
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                .producerType(ProducerType.BUILT_IN)
                .producerId(PRODUCER_ID)
                .capability(true)
                .reason("BureauMetricService producer registered for exact ID, but no exact-ID value/entities in context")
                .exactProducerPath(PRODUCER_ID + " [" + canonicalParameterId + "] (DATA_NOT_AVAILABLE)")
                .build();
    }

    private static ExecutionResult valueResult(String id, Object value, String path, String sourceType) {
        return valueResult(id, value, path, sourceType, Map.of());
    }

    private static ExecutionResult valueResult(
            String id, Object value, String path, String sourceType, Map<String, Object> extraProvenance) {
        // Exact identity: path key must equal requested id (enforced by callers using id as map key).
        if (value == null) {
            return ExecutionResult.builder(id)
                    .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                    .producerType(ProducerType.BUILT_IN)
                    .producerId(PRODUCER_ID)
                    .capability(true)
                    .reason("null value for exact metricCode " + id)
                    .exactProducerPath(PRODUCER_ID + " ← " + path + "[" + id + "]")
                    .build();
        }
        Map<String, Object> prov = new LinkedHashMap<>();
        if (extraProvenance != null) {
            prov.putAll(extraProvenance);
        }
        prov.put("sourceType", sourceType);
        prov.put("producerId", PRODUCER_ID);
        prov.put("metricCode", id);
        prov.put("metricVersion", BureauMetricService.METRIC_VERSION);
        prov.put("exactMetricCodeAssert", true);
        if (PersistedDerivedMetricSpine.SOURCE_PERSISTED_CANONICAL_DERIVED.equals(sourceType)) {
            prov.put("persistedStatus", PersistedDerivedMetricSpine.STATUS_VALUE_PRESENT);
        }
        return ExecutionResult.builder(id)
                .status(ExecutionStatus.VALUE_AVAILABLE)
                .value(value)
                .producerType(ProducerType.BUILT_IN)
                .producerId(PRODUCER_ID)
                .producerVersion(BureauMetricService.METRIC_VERSION)
                .dependencies(List.of())
                .capability(true)
                .provenance(prov)
                .exactProducerPath(PRODUCER_ID + " ← " + path + "[" + id + "] (metricCode==" + id + ")")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> persistedProvenance(EvaluationContext ctx, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object raw = ctx.entities().get(PersistedDerivedMetricSpine.PRECOMPUTED_METRIC_PROVENANCE);
        if (raw instanceof Map<?, ?> m && m.get(id) instanceof Map<?, ?> row) {
            out.putAll((Map<String, Object>) row);
        }
        return out;
    }
}
