package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges shadow Policy Engine outcomes into CreditEvidenceView-compatible sections.
 */
@Service
public class PolicyEngineEvidenceIntegrator {

    public Map<String, Object> attach(Map<String, Object> evidenceView, CiPolicyEvaluation evaluation) {
        Map<String, Object> view = evidenceView == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(evidenceView);
        if (evaluation == null) {
            return view;
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("policyPackageId", evaluation.getPolicyPackageId());
        section.put("policyVersion", evaluation.getPolicyVersion());
        section.put("overallOutcome", evaluation.getOverallOutcome());
        section.put("deterministicHash", evaluation.getDeterministicHash());
        section.put("passCount", evaluation.getPassCount());
        section.put("failCount", evaluation.getFailCount());
        section.put("referCount", evaluation.getReferCount());
        section.put("dataInsufficientCount", evaluation.getDiCount());
        section.put("shadowOnly", true);
        section.put("productionActive", false);

        List<Map<String, Object>> rules = new ArrayList<>();
        if (evaluation.getRuleResults() != null) {
            for (CiStandardRuleResult r : evaluation.getRuleResults()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleId", r.getRuleId());
                row.put("stage", r.getCategory());
                row.put("type", r.getRuleType());
                row.put("outcome", r.getOutcome());
                row.put("reasonCode", r.getReasonCode());
                row.put("explanation", r.getExplanation());
                rules.add(row);
            }
        }
        section.put("rules", rules);
        section.put("scoreResult", evaluation.getScoreResult());
        view.put("PolicyEngineShadow", section);
        return view;
    }
}
