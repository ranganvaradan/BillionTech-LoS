package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Enriches producer {@link ExecutionResult}s with shared contract fields from context + GACAT.
 * Does not invent values or change status/capability semantics.
 */
public final class ExecutionResultContractEnricher {

    private ExecutionResultContractEnricher() {}

    public static ExecutionResult enrich(ExecutionResult raw, EvaluationContext ctx, String canonicalId) {
        if (raw == null) {
            return ExecutionResult.error(canonicalId, "Null ExecutionResult from producer");
        }
        String valueType = null;
        String unit = null;
        try {
            var opt = com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter
                    .registry().findById(canonicalId);
            if (opt.isPresent()) {
                unit = opt.get().unit();
                valueType = inferValueType(raw.value(), opt.get().type(), unit);
            } else {
                valueType = inferValueType(raw.value(), null, null);
            }
        } catch (Exception ignored) {
            valueType = inferValueType(raw.value(), null, null);
        }
        ExecutionResult enriched = raw.enrichFromContext(ctx, valueType, unit);
        // Ensure simulation provenance is explicit when POLICY_TEST overlay without producer
        if (enriched.producerType() == ProducerType.POLICY_TEST_INPUT
                || enriched.simulatedValue()) {
            java.util.Map<String, Object> prov = new java.util.LinkedHashMap<>(enriched.provenance());
            prov.putIfAbsent("simulationOnly", true);
            prov.put("simulatedValueDoesNotImplyExecutable", !enriched.capability());
            return new ExecutionResult(
                    enriched.canonicalParameterId(),
                    enriched.status(),
                    enriched.value(),
                    enriched.producerType(),
                    enriched.producerId(),
                    enriched.dependencies(),
                    enriched.capability(),
                    java.util.Map.copyOf(prov),
                    enriched.reason(),
                    enriched.exactProducerPath(),
                    enriched.valueType(),
                    enriched.unit(),
                    enriched.producerVersion(),
                    enriched.evaluationAsOf(),
                    enriched.sourceSnapshotVersion(),
                    enriched.missingReason(),
                    enriched.mode());
        }
        return enriched;
    }

    static String inferValueType(Object value, String catalogueType, String unit) {
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof java.util.Collection<?>) {
            return "COLLECTION";
        }
        if (value instanceof Number) {
            if (unit != null) {
                String u = unit.toUpperCase(java.util.Locale.ROOT);
                if (u.contains("INR") || u.contains("MONEY") || u.contains("AMOUNT")) {
                    return "MONEY";
                }
                if (u.contains("PERCENT") || u.contains("%")) {
                    return "PERCENTAGE";
                }
                if (u.contains("DAY")) {
                    return "INTEGER";
                }
            }
            return value instanceof Integer || value instanceof Long ? "INTEGER" : "DECIMAL";
        }
        if (value instanceof String) {
            return "STRING";
        }
        if (catalogueType != null && !catalogueType.isBlank()) {
            return catalogueType.toUpperCase(java.util.Locale.ROOT);
        }
        return value == null ? null : value.getClass().getSimpleName();
    }
}
