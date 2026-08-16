package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thin adapter: scorecard row → exact canonical ID → {@link CanonicalParameterExecutionService}.
 * Does not calculate ADB/FOIR/GST; does not fall back to legacy numeric resolution for
 * GACAT-mapped parameters.
 */
public final class ScorecardFactorValueAdapter {

    private ScorecardFactorValueAdapter() {}

    public static final String RUNTIME_AUTHORITY = CanonicalScorecardValueResolver.AUTHORITY;
    public static final String RUNTIME_NOTE =
            "Canonical factor values resolve only through CanonicalParameterExecutionService";

    public record ResolvedValue(
            String canonicalParameterId,
            Integer canonicalDefinitionVersion,
            String legacyParameterKey,
            String source,
            BigDecimal numericValue,
            String stringValue,
            String provenance,
            ExecutionStatus executionStatus,
            String producerId,
            String reason,
            boolean legacyFallbackUsed) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("canonicalDefinitionVersion", canonicalDefinitionVersion);
            m.put("legacyParameterKey", legacyParameterKey);
            m.put("source", source);
            m.put("valueUsed", numericValue != null ? numericValue.toPlainString() : stringValue);
            m.put("valueProvenance", provenance);
            m.put("executionStatus", executionStatus == null ? null : executionStatus.name());
            m.put("producerId", producerId);
            m.put("reason", reason);
            m.put("legacyFallbackUsed", legacyFallbackUsed);
            m.put("valueAuthority", RUNTIME_AUTHORITY);
            return m;
        }
    }

    /**
     * Resolve value for a scorecard row. Canonical-mapped factors use the spine only.
     */
    public static ResolvedValue resolveRow(
            Map<String, Object> row,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        String legacy = str(row.get("parameter"));
        if (legacy == null) {
            legacy = str(row.get("legacyParameterKey"));
        }
        String source = str(row.get("source"));
        String canonicalId = str(row.get("canonicalParameterId"));
        Integer defVer = row.get("canonicalDefinitionVersion") instanceof Number n
                ? n.intValue() : null;
        if (canonicalId == null && legacy != null) {
            ScorecardCanonicalFactorMapper.Binding b = ScorecardCanonicalFactorMapper.resolve(legacy);
            if (b.canonicalParameterId() != null) {
                canonicalId = b.canonicalParameterId();
                defVer = b.canonicalDefinitionVersion();
            }
        }
        if (legacy == null && canonicalId != null) {
            Optional<CanonicalParameterDefinition> def =
                    CanonicalParameterRegistry.shared().findById(canonicalId);
            if (def.isPresent() && def.get().liveScorecardParameter() != null) {
                legacy = def.get().liveScorecardParameter();
            }
        }

        EvaluationContext evalCtx = UnderwritingEvaluationContextFactory.forUnderwriting(app, ctx);

        if (canonicalId != null) {
            CanonicalScorecardValueResolver.ResolveOutcome outcome =
                    CanonicalScorecardValueResolver.resolveCanonical(canonicalId, evalCtx);
            String prov = outcome.exactProducerPath() != null
                    ? outcome.exactProducerPath()
                    : (outcome.producerId() != null ? outcome.producerId() : RUNTIME_AUTHORITY);
            return new ResolvedValue(
                    canonicalId,
                    defVer,
                    legacy,
                    source,
                    outcome.numericValue(),
                    outcome.stringValue(),
                    prov,
                    outcome.status(),
                    outcome.producerId(),
                    outcome.reason(),
                    false);
        }

        // Non-canonical scorecard input (AGE, OCCUPATION, …)
        BigDecimal numeric = ScorecardPolicyEngine.resolveNonCanonicalScorecardInput(source, legacy, app, ctx);
        String stringValue = null;
        if (numeric == null) {
            stringValue = ApplicationScorecardParameterResolver.resolveString(legacy, app);
        }
        return new ResolvedValue(
                null,
                defVer,
                legacy,
                source,
                numeric,
                stringValue,
                "NON_CANONICAL_SCORECARD_INPUT",
                numeric != null || stringValue != null
                        ? ExecutionStatus.VALUE_AVAILABLE
                        : ExecutionStatus.DATA_NOT_AVAILABLE,
                "ScorecardNonCanonicalInput",
                numeric != null || stringValue != null ? null : "No non-canonical scorecard input",
                false);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
