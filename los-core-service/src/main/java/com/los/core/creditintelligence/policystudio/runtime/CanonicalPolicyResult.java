package com.los.core.creditintelligence.policystudio.runtime;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-5 canonical policy evaluation result (observational / Policy Test target semantics).
 * Does not redesign live underwriting decision packaging.
 */
public record CanonicalPolicyResult(
        String policyId,
        String policyVersion,
        String semanticCatalogueVersion,
        LocalDate evaluationAsOf,
        OverallOutcome overall,
        List<CanonicalRuleResult> ruleResults,
        List<String> failedRuleIds,
        List<String> insufficientRuleIds,
        List<String> errorRuleIds,
        Map<String, Object> parameterExecutionProvenance,
        Map<String, Object> meta
) {
    public enum OverallOutcome {
        PASS,
        FAIL,
        DATA_INSUFFICIENT,
        ERROR
    }

    public CanonicalPolicyResult {
        if (ruleResults == null) ruleResults = List.of();
        if (failedRuleIds == null) failedRuleIds = List.of();
        if (insufficientRuleIds == null) insufficientRuleIds = List.of();
        if (errorRuleIds == null) errorRuleIds = List.of();
        if (parameterExecutionProvenance == null) parameterExecutionProvenance = Map.of();
        if (meta == null) meta = Map.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyId", policyId);
        m.put("policyVersion", policyVersion);
        m.put("semanticCatalogueVersion", semanticCatalogueVersion);
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        m.put("overall", overall == null ? null : overall.name());
        List<Map<String, Object>> rules = new ArrayList<>();
        for (CanonicalRuleResult r : ruleResults) rules.add(r.toMap());
        m.put("ruleResults", rules);
        m.put("failedRuleIds", failedRuleIds);
        m.put("insufficientRuleIds", insufficientRuleIds);
        m.put("errorRuleIds", errorRuleIds);
        m.put("parameterExecutionProvenance", parameterExecutionProvenance);
        m.put("meta", meta);
        m.put("canonicalPolicyRuntime", CanonicalPolicyRuntime.RUNTIME_CLASS);
        m.put("liveDecisionAuthorityChanged", false);
        return m;
    }

    public static OverallOutcome aggregate(List<CanonicalRuleResult> rules) {
        if (rules == null || rules.isEmpty()) {
            return OverallOutcome.DATA_INSUFFICIENT;
        }
        boolean anyFail = false;
        boolean anyError = false;
        boolean anyDi = false;
        boolean anyPass = false;
        for (CanonicalRuleResult r : rules) {
            if (r.result() == CanonicalRuleResult.RuleOutcome.FAIL) anyFail = true;
            else if (r.result() == CanonicalRuleResult.RuleOutcome.ERROR) anyError = true;
            else if (r.result() == CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT) anyDi = true;
            else if (r.result() == CanonicalRuleResult.RuleOutcome.PASS) anyPass = true;
        }
        if (anyFail) return OverallOutcome.FAIL;
        if (anyError) return OverallOutcome.ERROR;
        if (anyDi) return OverallOutcome.DATA_INSUFFICIENT;
        if (anyPass) return OverallOutcome.PASS;
        return OverallOutcome.DATA_INSUFFICIENT;
    }
}
