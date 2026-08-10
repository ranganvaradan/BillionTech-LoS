package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.ComparisonClass;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates legacy vs DSL shadow outcomes for cutover analysis (non-authoritative).
 */
@Service
public class PolicyImpactAnalysisService {

    private final ShadowPolicyEngine engine;
    private final LegacyVsDslComparator comparator;

    public PolicyImpactAnalysisService() {
        this(new ShadowPolicyEngine(), new LegacyVsDslComparator());
    }

    public PolicyImpactAnalysisService(ShadowPolicyEngine engine, LegacyVsDslComparator comparator) {
        this.engine = engine != null ? engine : new ShadowPolicyEngine();
        this.comparator = comparator != null ? comparator : new LegacyVsDslComparator();
    }

    public Map<String, Object> analyze(
            CiExecutablePolicyPackage legacyPkg,
            CiExecutablePolicyPackage dslPkg,
            List<PolicyEvaluationInput> inputs) {
        int same = 0;
        int legacyApproveDslRefer = 0;
        int legacyApproveDslFail = 0;
        int legacyFailDslPass = 0;
        int legacyReferDslPass = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (PolicyEvaluationInput input : inputs == null ? List.<PolicyEvaluationInput>of() : inputs) {
            CiPolicyEvaluation legacyEval = engine.evaluate(legacyPkg, input);
            CiPolicyEvaluation dslEval = engine.evaluate(dslPkg, input);
            String lo = legacyEval.getOverallOutcome();
            String dso = dslEval.getOverallOutcome();
            ComparisonClass cls = comparator.classify(lo, dso, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("evaluationContextId", input.evaluationContextId());
            row.put("legacyOutcome", lo);
            row.put("shadowDslOutcome", dso);
            row.put("changed", lo == null || !lo.equals(dso));
            row.put("comparisonClass", cls.name());
            row.put("rulesResponsible", responsibleRules(legacyEval, dslEval));
            rows.add(row);

            if (lo != null && lo.equals(dso)) {
                same++;
            } else if ("PASS".equals(lo) && "REFER".equals(dso)) {
                legacyApproveDslRefer++;
            } else if ("PASS".equals(lo) && "FAIL".equals(dso)) {
                legacyApproveDslFail++;
            } else if ("FAIL".equals(lo) && "PASS".equals(dso)) {
                legacyFailDslPass++;
            } else if ("REFER".equals(lo) && "PASS".equals(dso)) {
                legacyReferDslPass++;
            }
        }

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("same", same);
        aggregate.put("legacyApproveToDslRefer", legacyApproveDslRefer);
        aggregate.put("legacyApproveToDslFail", legacyApproveDslFail);
        aggregate.put("legacyFailToDslPass", legacyFailDslPass);
        aggregate.put("legacyReferToDslPass", legacyReferDslPass);
        aggregate.put("total", rows.size());
        aggregate.put("shadowOnly", true);
        aggregate.put("note", "Do not use aggregates to auto-choose thresholds");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aggregate", aggregate);
        out.put("rows", rows);
        return out;
    }

    private List<String> responsibleRules(CiPolicyEvaluation a, CiPolicyEvaluation b) {
        List<String> ids = new ArrayList<>();
        if (b.getRuleResults() != null) {
            b.getRuleResults().stream()
                    .filter(r -> !"PASS".equals(r.getOutcome()) && !"NOT_APPLICABLE".equals(r.getOutcome()))
                    .forEach(r -> ids.add(r.getRuleId() + ":" + r.getOutcome()));
        }
        if (ids.isEmpty() && a.getRuleResults() != null) {
            a.getRuleResults().stream()
                    .filter(r -> !"PASS".equals(r.getOutcome()))
                    .forEach(r -> ids.add(r.getRuleId() + ":" + r.getOutcome()));
        }
        return ids;
    }
}
