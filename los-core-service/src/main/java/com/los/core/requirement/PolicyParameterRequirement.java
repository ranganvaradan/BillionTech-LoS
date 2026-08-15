package com.los.core.requirement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplicated Policy parameter requirement from DP-3 graph inventory.
 */
public record PolicyParameterRequirement(
        String canonicalParameterId,
        String originalToken,
        String resolutionStatus,
        boolean required,
        boolean unresolved,
        String businessName,
        List<String> ruleReferences,
        List<String> usageTypes,
        String productionReadiness,
        Boolean productionReady,
        Boolean runtimeReady,
        Boolean policyTestReady,
        String sourceFamily,
        Map<String, Object> inventoryRow
) {
    public PolicyParameterRequirement {
        if (ruleReferences == null) {
            ruleReferences = List.of();
        } else {
            ruleReferences = List.copyOf(ruleReferences);
        }
        if (usageTypes == null) {
            usageTypes = List.of();
        } else {
            usageTypes = List.copyOf(usageTypes);
        }
        if (inventoryRow == null) {
            inventoryRow = Map.of();
        } else {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            inventoryRow.forEach((k, v) -> {
                if (k != null && v != null) {
                    cleaned.put(k, v);
                }
            });
            inventoryRow = Map.copyOf(cleaned);
        }
    }

    public String logicalKey() {
        if (canonicalParameterId != null && !canonicalParameterId.isBlank()) {
            return canonicalParameterId;
        }
        return "UNRESOLVED:" + (originalToken != null ? originalToken : "UNKNOWN");
    }
}
