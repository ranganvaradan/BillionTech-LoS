package com.los.core.creditintelligence.policy.domain;

import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frozen evaluation input — maps built from EvaluationContext pinned sets.
 * P1 core path never reads live DB.
 */
public record PolicyEvaluationInput(
        UUID tenantId,
        UUID evaluationContextId,
        Map<String, Object> facts,
        Map<String, Object> metrics,
        Map<String, Object> reconciliations,
        Map<String, Object> policyParameters,
        Map<String, Object> applicationFields,
        EvaluationClock clock,
        Map<String, Object> metadata
) {
    public PolicyEvaluationInput {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        reconciliations = reconciliations == null ? Map.of() : Map.copyOf(reconciliations);
        policyParameters = policyParameters == null ? Map.of() : Map.copyOf(policyParameters);
        applicationFields = applicationFields == null ? Map.of() : Map.copyOf(applicationFields);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        clock = clock != null ? clock
                : new FixedEvaluationClock(Instant.parse("2024-06-15T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
    }

    public static PolicyEvaluationInput ofMaps(
            UUID tenantId,
            UUID evaluationContextId,
            Map<String, Object> facts,
            Map<String, Object> metrics,
            Map<String, Object> policyParameters) {
        return new PolicyEvaluationInput(
                tenantId,
                evaluationContextId,
                facts,
                metrics,
                Map.of(),
                policyParameters,
                facts,
                null,
                Map.of());
    }

    public PolicyEvaluationInput withMetadata(Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>(this.metadata);
        if (extra != null) {
            m.putAll(extra);
        }
        return new PolicyEvaluationInput(
                tenantId, evaluationContextId, facts, metrics, reconciliations,
                policyParameters, applicationFields, clock, m);
    }
}
