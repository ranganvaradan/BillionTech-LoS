package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DP-3 Part M — Policy Test against persisted Policy rule graph (not snapshot-only).
 */
@Service
@RequiredArgsConstructor
public class PolicyGraphPolicyTestService {

    private final PolicyRuleGraphService graphService;
    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();

    @Transactional(readOnly = true)
    public Map<String, Object> run(UUID policyDocumentId, Map<String, Object> metrics, Map<String, Object> facts) {
        Map<String, Object> gate = graphService.policyTestGate(policyDocumentId);
        Map<String, Object> out = new LinkedHashMap<>(gate);
        out.put("engine", "PolicyDslInterpreterV1");
        out.put("source", "PERSISTED_POLICY_RULE_GRAPH");
        if (!Boolean.TRUE.equals(gate.get("allowed"))) {
            out.put("failClosed", true);
            out.put("ruleResults", List.of());
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) gate.get("rules");
        List<Map<String, Object>> results = new ArrayList<>();
        var clock = FixedEvaluationClock.atLocalNoon(LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        for (Map<String, Object> rule : rules) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expr = (Map<String, Object>) rule.get("expression");
            var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                    metrics != null ? metrics : Map.of(),
                    facts != null ? facts : Map.of(),
                    clock);
            String outcome = interpreter.evaluate(expr, ctx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleKey", rule.get("ruleKey"));
            row.put("expression", expr);
            row.put("outcome", outcome);
            row.put("onTrue", rule.get("onTrue"));
            row.put("onFalse", rule.get("onFalse"));
            row.put("onMissing", rule.get("onMissing"));
            results.add(row);
        }
        out.put("ruleResults", results);
        out.put("authoringPersistedTestParity", true);
        return out;
    }

    /** Prove authoring AST == persisted graph AST (structural). */
    public static boolean astEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
