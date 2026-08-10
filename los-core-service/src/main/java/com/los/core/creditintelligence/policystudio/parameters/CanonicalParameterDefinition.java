package com.los.core.creditintelligence.policystudio.parameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POLICY-CONVERGENCE-1 — thin business-facing parameter read model.
 * Does not persist; does not create a new engine or catalogue DB.
 */
public record CanonicalParameterDefinition(
        String id,
        String businessName,
        String evaluatedFrom,
        String type,
        String unit,
        String period,
        String availability,
        String calculationSummary,
        List<String> requiredPrimitives,
        String existingImplementationBinding,
        List<String> aliases,
        String liveRuleParameter,
        String liveScorecardParameter
) {
    public static final String RAW = "RAW";
    public static final String DERIVED = "DERIVED";
    public static final String MANUAL = "MANUAL";

    public Map<String, Object> toBusinessView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("businessName", businessName);
        m.put("evaluatedFrom", evaluatedFrom);
        m.put("type", type);
        if (unit != null) m.put("unit", unit);
        if (period != null) m.put("period", period);
        if (availability != null) m.put("availability", availability);
        if (calculationSummary != null) m.put("calculationSummary", calculationSummary);
        if (requiredPrimitives != null && !requiredPrimitives.isEmpty()) {
            m.put("requiredPrimitives", requiredPrimitives);
        }
        if (existingImplementationBinding != null) {
            m.put("existingImplementationBinding", existingImplementationBinding);
        }
        if (aliases != null && !aliases.isEmpty()) m.put("aliases", aliases);
        if (liveRuleParameter != null) m.put("liveRuleParameter", liveRuleParameter);
        if (liveScorecardParameter != null) m.put("liveScorecardParameter", liveScorecardParameter);
        return m;
    }
}
