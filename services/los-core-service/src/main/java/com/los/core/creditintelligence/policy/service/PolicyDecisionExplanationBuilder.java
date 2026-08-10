package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.CiPolicyStageResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicyDecisionExplanationBuilder {

    public Map<String, Object> build(CiPolicyEvaluation evaluation) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (evaluation == null) {
            return out;
        }
        out.put("evaluationId", evaluation.getId());
        out.put("overallOutcome", evaluation.getOverallOutcome());
        out.put("deterministicHash", evaluation.getDeterministicHash());
        out.put("shadowOnly", true);
        out.put("productionActive", false);
        out.put("summary", evaluation.getExplanation());

        List<Map<String, Object>> stages = new ArrayList<>();
        if (evaluation.getStageResults() != null) {
            for (CiPolicyStageResult s : evaluation.getStageResults()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stageCode", s.getStageCode());
                row.put("sequence", s.getSequence());
                row.put("outcome", s.getOutcome());
                row.put("continueFlag", s.getContinueFlag());
                row.put("ruleCount", s.getRuleCount());
                row.put("detail", s.getDetail());
                stages.add(row);
            }
        }
        out.put("stages", stages);

        List<Map<String, Object>> rules = new ArrayList<>();
        if (evaluation.getRuleResults() != null) {
            for (CiStandardRuleResult r : evaluation.getRuleResults()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleId", r.getRuleId());
                row.put("ruleType", r.getRuleType());
                row.put("outcome", r.getOutcome());
                row.put("engineName", r.getEngineName());
                row.put("reasonCode", r.getReasonCode());
                row.put("explanation", r.getExplanation());
                rules.add(row);
            }
        }
        out.put("rules", rules);
        out.put("scoreResult", evaluation.getScoreResult());
        out.put("comparisonSummary", evaluation.getComparisonSummary());

        List<String> narrative = new ArrayList<>();
        narrative.add("Policy outcome: " + evaluation.getOverallOutcome());
        narrative.add("");
        narrative.add("Reasons:");
        int i = 1;
        if (evaluation.getRuleResults() != null) {
            for (CiStandardRuleResult r : evaluation.getRuleResults()) {
                if ("PASS".equals(r.getOutcome()) || "NOT_APPLICABLE".equals(r.getOutcome())) {
                    continue;
                }
                String reason = r.getReasonCode() != null ? r.getReasonCode() : r.getRuleId();
                String text = r.getExplanation() != null ? r.getExplanation() : r.getRuleId();
                narrative.add(i + ". " + text);
                narrative.add("   Rule: " + reason);
                narrative.add("   Outcome: " + r.getOutcome());
                i++;
            }
        }
        if (i == 1) {
            narrative.add("(no blocking reasons)");
        }
        out.put("humanReadable", String.join("\n", narrative));
        out.put("reasons", narrative);
        return out;
    }
}
