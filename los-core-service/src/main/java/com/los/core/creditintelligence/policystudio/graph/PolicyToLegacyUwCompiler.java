package com.los.core.creditintelligence.policystudio.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DP-3 Part L — design-only bridge: how a future published Policy graph
 * can be compiled into legacy underwriting_rule_sets representation.
 * Does NOT make Policy live authority. Does NOT mutate live UW.
 */
public final class PolicyToLegacyUwCompiler {

    private PolicyToLegacyUwCompiler() {}

    /**
     * Dry-run compile plan from persisted PolicyDsl AST nodes.
     * Output is diagnostic only — never written to underwriting_rule_sets here.
     */
    public static Map<String, Object> compilePlan(List<Map<String, Object>> persistedRules) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetRuntime", "underwriting_rule_sets");
        out.put("liveAuthority", false);
        out.put("dp3CompileOnly", true);
        out.put("ruleCount", persistedRules == null ? 0 : persistedRules.size());
        out.put("notes", List.of(
                "Map PolicyDsl COMPARE nodes to legacy parameter/operator/value rows where legacy key exists",
                "Unresolved GACAT operands block compile",
                "Compound AND/OR must preserve boolean structure (may require nested legacy packages later)",
                "Do not activate Policy as live UW in DP-3"
        ));
        out.put("status", "DESIGNED_NOT_ACTIVATED");
        return out;
    }
}
