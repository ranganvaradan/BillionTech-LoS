package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frozen replay — same package + same input → same deterministic hash.
 */
@Service
public class PolicyReplayService {

    private final ShadowPolicyEngine engine;

    public PolicyReplayService(ShadowPolicyEngine engine) {
        this.engine = engine != null ? engine : new ShadowPolicyEngine();
    }

    public PolicyReplayService() {
        this(new ShadowPolicyEngine());
    }

    public Map<String, Object> replay(CiExecutablePolicyPackage pkg, PolicyEvaluationInput input) {
        CiPolicyEvaluation first = engine.evaluate(pkg, input);
        CiPolicyEvaluation second = engine.evaluate(pkg, input);
        boolean identical = first.getDeterministicHash() != null
                && first.getDeterministicHash().equals(second.getDeterministicHash());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evaluationContextId", input.evaluationContextId());
        out.put("firstHash", first.getDeterministicHash());
        out.put("secondHash", second.getDeterministicHash());
        out.put("replayIdentical", identical);
        out.put("overallOutcome", first.getOverallOutcome());
        out.put("shadowOnly", true);
        return out;
    }

    public Map<String, Object> replay(CiExecutablePolicyPackage pkg, PolicyEvaluationInput input,
                                      UUID evaluationContextId) {
        PolicyEvaluationInput pinned = new PolicyEvaluationInput(
                input.tenantId(),
                evaluationContextId != null ? evaluationContextId : input.evaluationContextId(),
                input.facts(), input.metrics(), input.reconciliations(),
                input.policyParameters(), input.applicationFields(),
                input.clock(), input.metadata());
        return replay(pkg, pinned);
    }
}
