package com.los.core.creditintelligence.policystudio.lineage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GACAT-POLICY-LINEAGE-FIX-1 — business-facing metric lineage for Policy Studio cards.
 * Metadata over existing computation helpers — does not recalculate metrics.
 */
public record PolicyMetricLineage(
        String businessMetricId,
        String businessMetricName,
        String source,
        String availability,
        String derivation,
        String window,
        String freshness,
        String missingDataBehaviour,
        String classifiedCategory,
        String computeHelperCode,
        Map<String, Object> implementationBinding
) {
    public Map<String, Object> toBusinessView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessMetricId", businessMetricId);
        m.put("businessMetricName", businessMetricName);
        m.put("source", source);
        m.put("availability", availability);
        m.put("derivation", derivation);
        m.put("window", window);
        m.put("freshness", freshness);
        m.put("missingDataBehaviour", missingDataBehaviour);
        if (classifiedCategory != null) {
            m.put("classifiedCategory", classifiedCategory);
        }
        return m;
    }

    public Map<String, Object> toTechnicalView() {
        Map<String, Object> m = toBusinessView();
        m.put("computeHelperCode", computeHelperCode);
        m.put("implementationBinding", implementationBinding == null ? Map.of() : implementationBinding);
        return m;
    }

    public static PolicyMetricLineage of(
            String id, String name, String source, String availability,
            String derivation, String window, String missing, String category, String helper) {
        return new PolicyMetricLineage(
                id, name, source, availability, derivation, window, null, missing,
                category, helper,
                Map.of(
                        "registryCode", id,
                        "helperMetricCode", helper == null ? id : helper,
                        "allowCanonicalAuthority", false));
    }
}
