package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SCORECARD-CONVERGENCE-1 — thin adapter: canonical parameter → existing live scorecard context key.
 * Does not calculate ADB/FOIR/GST; delegates to {@link ScorecardPolicyEngine#resolve}.
 */
public final class ScorecardFactorValueAdapter {

    private ScorecardFactorValueAdapter() {}

    /**
     * Runtime value path remains {@link ScorecardPolicyEngine} (legacy).
     * Capability / certification display must use {@code CanonicalParameterCapabilityProjection};
     * this adapter does not claim spine execution parity for values.
     */
    public static final String RUNTIME_AUTHORITY = "LEGACY_ScorecardPolicyEngine";
    public static final String RUNTIME_NOTE =
            "Scorecard factor values are not yet resolved through CanonicalParameterExecutionService";

    public record ResolvedValue(
            String canonicalParameterId,
            Integer canonicalDefinitionVersion,
            String legacyParameterKey,
            String source,
            BigDecimal numericValue,
            String stringValue,
            String provenance) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("canonicalDefinitionVersion", canonicalDefinitionVersion);
            m.put("legacyParameterKey", legacyParameterKey);
            m.put("source", source);
            m.put("valueUsed", numericValue != null ? numericValue.toPlainString() : stringValue);
            m.put("valueProvenance", provenance);
            return m;
        }
    }

    /**
     * Resolve value for a scorecard row using legacy key (runtime authority unchanged).
     * Canonical id is attached for evidence only.
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
        // Prefer liveScorecardParameter from registry when only canonical id is known
        if (legacy == null && canonicalId != null) {
            Optional<CanonicalParameterDefinition> def =
                    CanonicalParameterRegistry.shared().findById(canonicalId);
            if (def.isPresent() && def.get().liveScorecardParameter() != null) {
                legacy = def.get().liveScorecardParameter();
            }
        }
        BigDecimal numeric = ScorecardPolicyEngine.resolve(source, legacy, app, ctx);
        String stringValue = ScorecardPolicyEngine.resolveStringValue(source, legacy, app, ctx);
        String provenance = ScorecardSafetyScoring.resolveProvenancePublic(legacy, ctx);
        return new ResolvedValue(canonicalId, defVer, legacy, source, numeric, stringValue, provenance);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
