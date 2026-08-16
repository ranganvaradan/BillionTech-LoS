package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects {@link ExecutionResult} into consumer maps without inventing capability from
 * catalogue flags. Used by D&amp;P / Studio / Inventory / W6 facades.
 */
public final class CanonicalExecutionContractProjection {

    private CanonicalExecutionContractProjection() {}

    public static Map<String, Object> project(ExecutionResult er) {
        if (er == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", ExecutionStatus.ERROR.name());
            m.put("capability", false);
            m.put("valueAvailable", false);
            m.put("executionAuthority", "CanonicalParameterExecutionService");
            m.put("certificationStatus", "NOT_ESTABLISHED");
            m.put("catalogueFlagsAreNotExecutionAuthority", true);
            return m;
        }
        Map<String, Object> m = er.toCanonicalContractMap();
        m.put("catalogueFlagsAreNotExecutionAuthority", true);
        return m;
    }

    /**
     * Merge execution contract onto an existing face map without letting catalogue
     * {@code implemented}/{@code production_ready} override capability/status.
     */
    public static void stampOnto(Map<String, Object> target, ExecutionResult er) {
        if (target == null || er == null) {
            return;
        }
        Map<String, Object> c = project(er);
        target.put("executionContract", c);
        target.put("executionStatus", c.get("status"));
        target.put("capability", c.get("capability"));
        target.put("valueAvailable", c.get("valueAvailable"));
        target.put("executionAuthority", "CanonicalParameterExecutionService");
        target.put("certificationStatus", "NOT_ESTABLISHED");
        // Never promote catalogue production_ready
        target.put("productionReady", false);
    }
}
