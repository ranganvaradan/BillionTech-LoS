package com.los.core.creditintelligence.policystudio.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit Policy Test simulation overlay. Never rewrites real execution capability.
 */
public record SimulationOverride(
        String canonicalId,
        Object value,
        String valueType,
        String overrideKind
) {
    public static final String KIND_SIMULATED_VALUE = "SIMULATED_VALUE";

    public static SimulationOverride of(String canonicalId, Object value) {
        return new SimulationOverride(
                canonicalId,
                value,
                value == null ? "null" : value.getClass().getSimpleName(),
                KIND_SIMULATED_VALUE);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalId", canonicalId);
        m.put("value", value);
        m.put("valueType", valueType);
        m.put("overrideKind", overrideKind);
        m.put("changesRealCapability", false);
        return m;
    }
}
