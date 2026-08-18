package com.los.core.creditintelligence.policystudio.graph;

import java.util.Map;

/**
 * Single participation gate for persisted Policy rule-graph evaluation
 * ({@code PolicyGraphPolicyTestService} and CanonicalPolicyRuntime shadow).
 *
 * <p>Soft-deleted / excluded graph nodes must not affect Policy Test or shadow
 * decisions. This is platform-generic: no policy-name or customer branching.
 */
public final class PolicyGraphParticipation {

    private PolicyGraphParticipation() {}

    public static boolean participates(CiPolicyRuleGraphNode node) {
        if (node == null) {
            return false;
        }
        return participates(node.getMetadata());
    }

    public static boolean participates(Map<String, Object> metadata) {
        Map<String, Object> meta = metadata == null ? Map.of() : metadata;
        String disposition = String.valueOf(meta.getOrDefault("disposition", ""));
        if (Boolean.TRUE.equals(meta.get("deleted")) || "DELETED".equalsIgnoreCase(disposition)) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.get("excludedFromActivation"))) {
            return false;
        }
        if ("IGNORED".equalsIgnoreCase(disposition)
                || "IGNORE_FOR_AUTOMATION".equalsIgnoreCase(disposition)
                || "DEFERRED_SOURCE_NOT_PROVEN".equalsIgnoreCase(disposition)) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.get("dataRequirementOnly"))
                || Boolean.TRUE.equals(meta.get("metricAdjustment"))) {
            return false;
        }
        return true;
    }
}
