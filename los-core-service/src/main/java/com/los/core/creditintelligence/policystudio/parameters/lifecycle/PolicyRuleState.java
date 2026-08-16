package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FINAL-CANONICAL-PARAMETER-STATE-REFACTOR-1 — rule lifecycle only.
 * Must never mutate or reinterpret CanonicalParameterState.
 */
public final class PolicyRuleState {

    public static final String AUTHORITY = "PolicyRuleState";

    private PolicyRuleState() {}

    /**
     * Build from existing {@link PolicyRuleLifecycleProjection} output + rule card facts.
     * Parameter readiness must come from CanonicalParameterStateService separately.
     */
    public static Map<String, Object> fromLifecycle(
            Map<String, Object> lifecycle,
            String ruleId,
            List<String> canonicalParameterIds,
            Map<String, Object> card) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authority", AUTHORITY);
        out.put("ruleId", ruleId);
        out.put("canonicalParameterIds", canonicalParameterIds == null ? List.of() : canonicalParameterIds);
        if (lifecycle != null) {
            out.put("lenderState", lifecycle.get("lenderState"));
            out.put("lenderStateLabel", lifecycle.get("ruleLifecycleLabel") != null
                    ? lifecycle.get("ruleLifecycleLabel")
                    : lifecycle.get("lenderStateLabel"));
            out.put("statusChip", lifecycle.get("statusChip"));
            out.put("outstandingRuleAction", lifecycle.get("outstandingAction"));
            out.put("showAcceptRule", lifecycle.get("showAcceptRule"));
            out.put("ruleAccepted", lifecycle.get("ruleAccepted"));
            out.put("needsReview",
                    "READY_FOR_CONFIRMATION".equals(lifecycle.get("lenderState"))
                            || "NEEDS_INPUT".equals(lifecycle.get("lenderState"))
                            || Boolean.TRUE.equals(lifecycle.get("showAcceptRule")));
        }
        if (card != null) {
            out.put("operator", card.get("operator"));
            out.put("threshold", card.get("authoringThreshold") != null
                    ? card.get("authoringThreshold") : card.get("threshold"));
            Object status = card.get("status");
            out.put("disposition", card.get("disposition"));
            out.put("ignored", "Ignored".equals(String.valueOf(status)));
            out.put("deleted", "Deleted".equals(String.valueOf(status)));
            out.put("accepted", Boolean.TRUE.equals(lifecycle != null ? lifecycle.get("ruleAccepted") : null));
        }
        out.put("parameterStateIndependent", true);
        out.put("mustNotMutateCanonicalParameterState", true);
        return out;
    }
}
