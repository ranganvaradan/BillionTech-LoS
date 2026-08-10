package com.los.core.creditintelligence.policystudio.catalogue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business-editable parameter descriptor for a catalogue capability.
 */
public record ParameterDefinition(
        String name,
        String label,
        String type,
        String unit,
        Object defaultValue,
        String description
) {
    public Map<String, Object> toBusinessView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        m.put("type", type);
        if (unit != null) {
            m.put("unit", unit);
        }
        if (defaultValue != null) {
            m.put("defaultValue", defaultValue);
        }
        if (description != null) {
            m.put("description", description);
        }
        return m;
    }
}
