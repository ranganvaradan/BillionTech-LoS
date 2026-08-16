package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical parameter value resolution for Scorecard / Underwriting — spine only.
 * Never falls back to {@link ScorecardPolicyEngine} for GACAT-mapped parameters.
 */
public final class CanonicalScorecardValueResolver {

    public static final String AUTHORITY = "CanonicalParameterExecutionService";

    private CanonicalScorecardValueResolver() {}

    public record ResolveOutcome(
            String canonicalParameterId,
            ExecutionStatus status,
            BigDecimal numericValue,
            String stringValue,
            Object rawValue,
            String producerId,
            String producerType,
            String exactProducerPath,
            Map<String, Object> provenance,
            String reason,
            boolean legacyFallbackUsed,
            boolean capability,
            Map<String, Object> executionContract) {

        public boolean valueAvailable() {
            return status == ExecutionStatus.VALUE_AVAILABLE;
        }

        public Map<String, Object> toTraceMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("executionStatus", status == null ? null : status.name());
            m.put("valueUsed", numericValue != null ? numericValue.toPlainString() : stringValue);
            m.put("valueAvailable", valueAvailable());
            m.put("capability", capability);
            m.put("producerId", producerId);
            m.put("producerType", producerType);
            m.put("exactProducerPath", exactProducerPath);
            m.put("provenance", provenance);
            m.put("reason", reason);
            m.put("legacyFallbackUsed", legacyFallbackUsed);
            m.put("valueAuthority", AUTHORITY);
            m.put("executionContract", executionContract);
            m.put("certificationStatus", "NOT_ESTABLISHED");
            return m;
        }
    }

    public static ResolveOutcome resolveCanonical(String canonicalParameterId, EvaluationContext evalCtx) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return unavailable(null, ExecutionStatus.NOT_EXECUTABLE, "Blank canonical parameter id");
        }
        CanonicalParameterExecutionService spine = ExecutionCapabilityAuthority.require();
        ExecutionResult er = spine.resolveAndExecute(canonicalParameterId.trim(), evalCtx);
        return fromExecution(er);
    }

    /**
     * Resolve a scorecard legacy key when it maps EXACT/SAFE_ALIAS to GACAT.
     * Returns empty optional when the key is not a canonical mapping (caller may use
     * non-canonical scorecard business inputs).
     */
    public static java.util.Optional<ResolveOutcome> resolveMappedLegacyKey(
            String legacyParameterKey, EvaluationContext evalCtx) {
        if (legacyParameterKey == null || legacyParameterKey.isBlank()) {
            return java.util.Optional.empty();
        }
        // Already a canonical id
        if (legacyParameterKey.contains(".")) {
            return java.util.Optional.of(resolveCanonical(legacyParameterKey, evalCtx));
        }
        ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(legacyParameterKey);
        if (bind.canonicalParameterId() == null) {
            return java.util.Optional.empty();
        }
        if (!ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                && !ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(resolveCanonical(bind.canonicalParameterId(), evalCtx));
    }

    public static ResolveOutcome fromExecution(ExecutionResult er) {
        if (er == null) {
            return unavailable(null, ExecutionStatus.ERROR, "Null ExecutionResult");
        }
        BigDecimal num = null;
        String str = null;
        if (er.value() != null) {
            num = toBigDecimal(er.value());
            str = String.valueOf(er.value());
        }
        return new ResolveOutcome(
                er.canonicalParameterId(),
                er.status(),
                er.valueAvailable() ? num : null,
                er.valueAvailable() ? str : null,
                er.value(),
                er.producerId(),
                er.producerType() == null ? null : er.producerType().name(),
                er.exactProducerPath(),
                er.provenance(),
                er.reason(),
                false,
                er.capability(),
                er.toCanonicalContractMap());
    }

    private static ResolveOutcome unavailable(String id, ExecutionStatus status, String reason) {
        return new ResolveOutcome(id, status, null, null, null, null, null, null, Map.of(), reason, false,
                false, Map.of("status", status == null ? null : status.name(),
                "capability", false, "executionAuthority", AUTHORITY));
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (value instanceof Boolean bool) {
            return bool ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
