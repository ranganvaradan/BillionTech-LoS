package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replays a recommendation from frozen strategy + input; hash must be stable.
 */
@Service
public class DecisionReplayService {

    private final ShadowDecisionEngine engine;

    public DecisionReplayService(ShadowDecisionEngine engine) {
        this.engine = engine != null ? engine : new ShadowDecisionEngine();
    }

    public DecisionReplayService() {
        this(new ShadowDecisionEngine());
    }

    public Map<String, Object> replay(CiDecisionStrategy strategy, DecisionRuntimeInput input) {
        CiCreditRecommendation first = engine.recommend(strategy, input);
        CiCreditRecommendation second = engine.recommend(strategy, input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("firstHash", first.getDeterministicDecisionHash());
        out.put("secondHash", second.getDeterministicDecisionHash());
        out.put("hashMatch", first.getDeterministicDecisionHash() != null
                && first.getDeterministicDecisionHash().equals(second.getDeterministicDecisionHash()));
        out.put("outcome", first.getRecommendationOutcome());
        out.put("amount", first.getRecommendedAmount());
        out.put("shadowOnly", true);
        out.put("authoritative", false);
        out.put("recommendation", first);
        return out;
    }
}
