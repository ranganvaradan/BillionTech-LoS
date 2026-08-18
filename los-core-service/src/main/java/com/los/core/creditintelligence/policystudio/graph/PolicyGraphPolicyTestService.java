package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.SharedCanonicalEvaluationSupport;
import com.los.core.creditintelligence.policystudio.runtime.SimulationOverride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DP-3 Policy Test against persisted Policy rule graph.
 * Wave-6: same {@link CanonicalPolicyRuntime} + CPES as Policy Test / target-live (not a second evaluator).
 * Marked for Wave-10 retirement if redundant with Policy Studio Test after full UI convergence.
 */
@Service
@RequiredArgsConstructor
public class PolicyGraphPolicyTestService {

    public static final String ENGINE = CanonicalPolicyRuntime.RUNTIME_CLASS;
    public static final String RETIREMENT_WAVE = "WAVE_10_IF_REDUNDANT";

    private final PolicyRuleGraphService graphService;
    private final CanonicalParameterExecutionService parameterExecution;

    @Transactional(readOnly = true)
    public Map<String, Object> run(UUID policyDocumentId, Map<String, Object> metrics, Map<String, Object> facts) {
        return run(policyDocumentId, metrics, facts, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(
            UUID policyDocumentId,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            LocalDate evaluationAsOf,
            List<SimulationOverride> simulationOverrides) {
        Map<String, Object> gate = graphService.policyTestGate(policyDocumentId);
        Map<String, Object> out = new LinkedHashMap<>(gate);
        out.put("engine", ENGINE);
        out.put("dslEngine", CanonicalPolicyRuntime.DSL_ENGINE);
        out.put("source", "PERSISTED_POLICY_RULE_GRAPH");
        out.put("spineBacked", true);
        out.put("retirementWave", RETIREMENT_WAVE);
        out.put("liveDecisionAuthorityChanged", false);
        if (!Boolean.TRUE.equals(gate.get("allowed"))) {
            out.put("failClosed", true);
            out.put("ruleResults", List.of());
            return out;
        }

        SharedCanonicalEvaluationSupport.AsOfResolution asOfRes =
                SharedCanonicalEvaluationSupport.resolvePolicyTestAsOf(evaluationAsOf);
        out.putAll(asOfRes.toMap());

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(asOfRes.asOf())
                .documentId(policyDocumentId);
        if (facts != null) {
            facts.forEach((k, v) -> {
                if (k != null && v != null) b.fact(k, v);
            });
        }
        if (metrics != null) {
            metrics.forEach((k, v) -> {
                if (k != null && v != null) {
                    // Prefer exact dotted GACAT ids as facts; also as inputs for simulation honesty
                    if (k.contains(".")) {
                        b.fact(k, v);
                        b.input(k, v);
                    } else {
                        b.input(k, v);
                    }
                }
            });
        }
        List<Map<String, Object>> overrideRows = new ArrayList<>();
        if (simulationOverrides != null) {
            for (SimulationOverride ov : simulationOverrides) {
                if (ov == null || ov.canonicalId() == null) continue;
                b.input(ov.canonicalId(), ov.value());
                overrideRows.add(ov.toMap());
            }
        }
        EvaluationContext spine = SharedCanonicalEvaluationSupport.normalize(
                b.build(), EvaluationMode.POLICY_TEST, asOfRes.asOf());

        CanonicalPolicyRuntime runtime = new CanonicalPolicyRuntime(parameterExecution);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) gate.get("rules");
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> honesty = new ArrayList<>();

        int skippedNonParticipating = 0;
        for (Map<String, Object> rule : rules) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = rule.get("metadata") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            if (!PolicyGraphParticipation.participates(meta)) {
                skippedNonParticipating++;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> expr = (Map<String, Object>) rule.get("expression");
            String onMissing = rule.get("onMissing") == null
                    ? null : String.valueOf(rule.get("onMissing"));
            String ruleKey = rule.get("ruleKey") == null ? "rule" : String.valueOf(rule.get("ruleKey"));
            CanonicalRuleResult rr = runtime.evaluateRule(
                    new CanonicalPolicyRuntime.RuleSpec(ruleKey, null, expr, onMissing),
                    spine,
                    asOfRes.asOf());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleKey", rule.get("ruleKey"));
            row.put("systemRuleId", rule.get("systemRuleId"));
            row.put("expression", expr);
            row.put("outcome", mapOutcome(rr));
            row.put("canonicalResult", rr.result().name());
            row.put("onTrue", rule.get("onTrue"));
            row.put("onFalse", rule.get("onFalse"));
            row.put("onMissing", rule.get("onMissing"));
            row.put("evaluationAsOf", asOfRes.asOf().toString());
            row.put("runtime", ENGINE);
            results.add(row);
            if (rr.actualExecution() != null) {
                honesty.add(SharedCanonicalEvaluationSupport.honestyProjection(rr.actualExecution()));
            }
            for (String id : rr.canonicalParameterIds()) {
                ExecutionResult er = parameterExecution.resolveAndExecute(id, spine);
                honesty.add(SharedCanonicalEvaluationSupport.honestyProjection(er));
            }
        }
        out.put("ruleResults", results);
        out.put("participatingRuleCount", results.size());
        out.put("skippedNonParticipatingRuleCount", skippedNonParticipating);
        out.put("valueHonesty", honesty);
        out.put("simulationOverrides", overrideRows);
        out.put("authoringPersistedTestParity", true);
        out.put("usesCanonicalPolicyRuntime", true);
        out.put("usesCpes", true);
        return out;
    }

    private static String mapOutcome(CanonicalRuleResult rr) {
        return switch (rr.result()) {
            case PASS -> "PASS";
            case FAIL -> "FAIL";
            case ERROR -> "ERROR";
            case DATA_INSUFFICIENT -> "DATA_INSUFFICIENT";
        };
    }

    /** Prove authoring AST == persisted graph AST (structural). */
    public static boolean astEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
