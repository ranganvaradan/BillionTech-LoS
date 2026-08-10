package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiDecisionHistoricalReplay;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DecisionHistoricalReplayService {

    private final ShadowDecisionEngine engine;

    public DecisionHistoricalReplayService(ShadowDecisionEngine engine) {
        this.engine = engine != null ? engine : new ShadowDecisionEngine();
    }

    public DecisionHistoricalReplayService() {
        this(new ShadowDecisionEngine());
    }

    public CiDecisionHistoricalReplay replay(
            CiDecisionStrategy strategy,
            List<DecisionRuntimeInput> inputs,
            String createdBy) {
        Instant started = Instant.now();
        List<Object> contextIds = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Integer> outcomeCounts = new LinkedHashMap<>();

        for (DecisionRuntimeInput input : inputs == null ? List.<DecisionRuntimeInput>of() : inputs) {
            CiCreditRecommendation rec = engine.recommend(strategy, input);
            contextIds.add(input.evaluationContextId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("evaluationContextId", input.evaluationContextId());
            row.put("outcome", rec.getRecommendationOutcome());
            row.put("deterministicDecisionHash", rec.getDeterministicDecisionHash());
            row.put("amount", rec.getRecommendedAmount());
            row.put("authoritative", false);
            results.add(row);
            String o = rec.getRecommendationOutcome() == null ? "UNKNOWN" : rec.getRecommendationOutcome();
            outcomeCounts.merge(o, 1, Integer::sum);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "DECISION_HISTORICAL_REPLAY");
        summary.put("count", results.size());
        summary.put("outcomeCounts", outcomeCounts);
        summary.put("results", results);
        summary.put("shadowOnly", true);
        summary.put("notCreditPerformanceValidation", true);

        return CiDecisionHistoricalReplay.builder()
                .id(UUID.randomUUID())
                .strategyId(strategy.getId())
                .contextIds(contextIds)
                .summary(summary)
                .startedAt(started)
                .completedAt(Instant.now())
                .createdBy(createdBy)
                .build();
    }
}
