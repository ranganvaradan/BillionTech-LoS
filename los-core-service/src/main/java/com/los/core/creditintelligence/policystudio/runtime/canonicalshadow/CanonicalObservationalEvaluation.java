package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory canonical evaluation of an exact frozen application package.
 * Presentation surfaces (Policy Studio Application Test, shadow persist) must not
 * re-evaluate with a second engine.
 */
public record CanonicalObservationalEvaluation(
        String status,
        List<String> reasonCodes,
        CanonicalApplicationConfiguration freeze,
        String identityHash,
        UUID freezeRowId,
        EvaluationContext spine,
        CanonicalPolicyResult policy,
        List<Map<String, Object>> parameterEvidence,
        List<Map<String, Object>> ruleEvidence,
        Map<String, Object> scorecardEvidence,
        PolicyScorecardPrecedence.PrecedenceResult aggregation,
        String canonicalDecision,
        Map<String, Integer> lookupCounts
) {
    public CanonicalObservationalEvaluation {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        parameterEvidence = parameterEvidence == null ? List.of() : List.copyOf(parameterEvidence);
        ruleEvidence = ruleEvidence == null ? List.of() : List.copyOf(ruleEvidence);
        scorecardEvidence = scorecardEvidence == null
                ? Map.of() : new LinkedHashMap<>(scorecardEvidence);
        lookupCounts = lookupCounts == null
                ? zeroLookups() : new LinkedHashMap<>(lookupCounts);
    }

    public static Map<String, Integer> zeroLookups() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("DEFAULT_WORKFLOW_LOOKUP_COUNT", 0);
        m.put("LATEST_WORKFLOW_LOOKUP_COUNT", 0);
        m.put("LATEST_POLICY_LOOKUP_COUNT", 0);
        m.put("LATEST_SCORECARD_LOOKUP_COUNT", 0);
        m.put("LATEST_CALCULATION_LOOKUP_COUNT", 0);
        m.put("PRODUCT_OVERRIDE_LOOKUP_COUNT", 0);
        return m;
    }

    public static CanonicalObservationalEvaluation notExecutable(
            CanonicalApplicationConfiguration freeze,
            String identityHash,
            UUID freezeRowId,
            List<String> reasons,
            List<Map<String, Object>> parameters,
            List<Map<String, Object>> rules,
            Map<String, Object> scorecard,
            CanonicalPolicyResult policy) {
        return new CanonicalObservationalEvaluation(
                "NOT_EXECUTABLE",
                reasons,
                freeze,
                identityHash,
                freezeRowId,
                null,
                policy,
                parameters,
                rules,
                scorecard,
                null,
                CanonicalShadowDecision.NOT_EXECUTABLE.name(),
                zeroLookups());
    }

    public int participatingRuleCount() {
        int n = 0;
        for (Map<String, Object> row : ruleEvidence) {
            if (Boolean.TRUE.equals(row.get("participates"))) {
                n++;
            }
        }
        return n;
    }

    public int deferredRuleCount() {
        int n = 0;
        for (Map<String, Object> row : ruleEvidence) {
            if (Boolean.FALSE.equals(row.get("participates"))) {
                n++;
            }
        }
        return n;
    }

    public Map<String, Object> aggregationMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authority", CanonicalShadowUnderwritingService.AGGREGATION_AUTHORITY);
        out.put("service", CanonicalObservationalEvaluationService.SERVICE);
        if (this.aggregation != null) {
            out.put("precedence", this.aggregation.ruleApplied());
            out.put("reasonCodes", new ArrayList<>(this.aggregation.reasonCodes()));
        }
        out.put("canonicalRuntimeUsedForLiveDecision", false);
        out.put("liveDecisionAuthorityUnchanged", true);
        return out;
    }
}
