package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PolicySimulationService {

    private final ShadowPolicyEngine engine;

    public PolicySimulationService(ShadowPolicyEngine engine) {
        this.engine = engine != null ? engine : new ShadowPolicyEngine();
    }

    public PolicySimulationService() {
        this(new ShadowPolicyEngine());
    }

    public Map<String, Object> simulate(CiExecutablePolicyPackage pkg, PolicyEvaluationInput input) {
        CiPolicyEvaluation eval = engine.evaluate(pkg, input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evaluationId", eval.getId());
        out.put("overallOutcome", eval.getOverallOutcome());
        out.put("deterministicHash", eval.getDeterministicHash());
        out.put("passCount", eval.getPassCount());
        out.put("failCount", eval.getFailCount());
        out.put("referCount", eval.getReferCount());
        out.put("diCount", eval.getDiCount());
        out.put("stageCount", eval.getStageCount());
        out.put("scoreResult", eval.getScoreResult());
        out.put("explanation", eval.getExplanation());
        out.put("shadowOnly", true);
        out.put("productionActive", false);
        return out;
    }
}
