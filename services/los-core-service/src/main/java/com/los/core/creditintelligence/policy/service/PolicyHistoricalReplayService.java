package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.CiPolicyHistoricalReplay;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyHistoricalReplayService {

    private final ShadowPolicyEngine engine;

    public PolicyHistoricalReplayService(ShadowPolicyEngine engine) {
        this.engine = engine != null ? engine : new ShadowPolicyEngine();
    }

    public PolicyHistoricalReplayService() {
        this(new ShadowPolicyEngine());
    }

    public CiPolicyHistoricalReplay replay(
            CiExecutablePolicyPackage pkg,
            List<PolicyEvaluationInput> inputs,
            String createdBy) {
        Instant started = Instant.now();
        List<Object> contextIds = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        int pass = 0, fail = 0, refer = 0, di = 0;
        for (PolicyEvaluationInput input : inputs == null ? List.<PolicyEvaluationInput>of() : inputs) {
            CiPolicyEvaluation eval = engine.evaluate(pkg, input);
            contextIds.add(input.evaluationContextId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("evaluationContextId", input.evaluationContextId());
            row.put("overallOutcome", eval.getOverallOutcome());
            row.put("deterministicHash", eval.getDeterministicHash());
            results.add(row);
            switch (eval.getOverallOutcome() == null ? "" : eval.getOverallOutcome()) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                case "REFER" -> refer++;
                default -> di++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", results.size());
        summary.put("pass", pass);
        summary.put("fail", fail);
        summary.put("refer", refer);
        summary.put("dataInsufficient", di);
        summary.put("results", results);
        summary.put("shadowOnly", true);
        return CiPolicyHistoricalReplay.builder()
                .id(UUID.randomUUID())
                .policyPackageId(pkg.getId())
                .contextIds(contextIds)
                .summary(summary)
                .startedAt(started)
                .completedAt(Instant.now())
                .createdBy(createdBy)
                .build();
    }
}
