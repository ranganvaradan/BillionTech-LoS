package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-5 canonical rule evaluation result. Distinct from live UW MultiRuleEvalResult.
 */
public record CanonicalRuleResult(
        String ruleId,
        String ruleVersion,
        List<String> canonicalParameterIds,
        String operator,
        Object expectedOrThreshold,
        ExecutionResult actualExecution,
        RuleOutcome result,
        LocalDate evaluationAsOf,
        String reason,
        Map<String, Object> provenance,
        List<CanonicalRuleResult> children
) {
    public enum RuleOutcome {
        PASS,
        FAIL,
        DATA_INSUFFICIENT,
        ERROR
    }

    public CanonicalRuleResult {
        if (canonicalParameterIds == null) canonicalParameterIds = List.of();
        if (provenance == null) provenance = Map.of();
        if (children == null) children = List.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", ruleId);
        m.put("ruleVersion", ruleVersion);
        m.put("canonicalParameterIds", canonicalParameterIds);
        m.put("operator", operator);
        m.put("expectedOrThreshold", expectedOrThreshold);
        if (actualExecution != null) {
            m.put("actualExecution", actualExecution.toTraceMap());
            m.put("actualStatus", actualExecution.status() == null ? null : actualExecution.status().name());
            m.put("actualValue", actualExecution.value());
        }
        m.put("result", result == null ? null : result.name());
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        m.put("reason", reason);
        m.put("provenance", provenance);
        if (!children.isEmpty()) {
            List<Map<String, Object>> ch = new ArrayList<>();
            for (CanonicalRuleResult c : children) ch.add(c.toMap());
            m.put("children", ch);
        }
        return m;
    }

    public static RuleOutcome fromDslOutcome(String dslOutcome) {
        if (dslOutcome == null) return RuleOutcome.DATA_INSUFFICIENT;
        return switch (dslOutcome.trim().toUpperCase()) {
            case "PASS" -> RuleOutcome.PASS;
            case "FAIL" -> RuleOutcome.FAIL;
            case "ERROR" -> RuleOutcome.ERROR;
            case "DATA_INSUFFICIENT", "REFER", "NOT_APPLICABLE" -> RuleOutcome.DATA_INSUFFICIENT;
            default -> RuleOutcome.DATA_INSUFFICIENT;
        };
    }

    public static RuleOutcome fromExecutionStatus(ExecutionStatus status) {
        if (status == null) return RuleOutcome.DATA_INSUFFICIENT;
        return switch (status) {
            case VALUE_AVAILABLE -> RuleOutcome.PASS; // value present — comparison decides
            case ERROR -> RuleOutcome.ERROR;
            default -> RuleOutcome.DATA_INSUFFICIENT;
        };
    }
}
