package com.los.core.service.underwriting;

import java.util.List;
import java.util.Map;

/**
 * Outcome of evaluating every matching active rule for a borrower/product (plus filters).
 */
public record MultiRuleEvalResult(
        List<PerRuleEval> perRule,
        String aggregatePolicyRecommendation,
        String aggregateCreditDecision,
        int aggregateRiskScore,
        List<String> aggregateReasons) {

    public boolean hasAnyRule() {
        return perRule != null && !perRule.isEmpty();
    }

    public record PerRuleEval(
            String ruleId,
            String ruleName,
            String policyDecision,
            String creditDecision,
            int riskScore,
            List<String> reasons,
            String kind,
            Map<String, Object> matchedConditions,
            Map<String, Object> sourceValuesUsed) {
    }
}
