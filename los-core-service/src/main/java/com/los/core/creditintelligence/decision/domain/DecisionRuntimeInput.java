package com.los.core.creditintelligence.decision.domain;

import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime frozen decision input — maps built from EvaluationContext + CiPolicyEvaluation only.
 * Never reads live mutable state.
 */
public record DecisionRuntimeInput(
        UUID tenantId,
        UUID lenderId,
        String productCode,
        UUID applicationId,
        UUID evaluationContextId,
        UUID policyEvaluationId,
        UUID policyPackageId,
        UUID factSnapshotId,
        UUID metricResultSetId,
        UUID reconciliationResultSetId,
        List<UUID> scoreResultIds,
        UUID creditEvidenceSummaryId,
        Map<String, Object> facts,
        Map<String, Object> metrics,
        Map<String, Object> reconciliations,
        Map<String, Object> policyParameters,
        Map<String, Object> applicationFields,
        String policyOverallOutcome,
        List<Map<String, Object>> policyRuleResults,
        Map<String, Object> scoreResult,
        BigDecimal requestedAmount,
        Integer requestedTenureMonths,
        EvaluationClock clock,
        Map<String, Object> metadata
) {
    public DecisionRuntimeInput {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        reconciliations = reconciliations == null ? Map.of() : Map.copyOf(reconciliations);
        policyParameters = policyParameters == null ? Map.of() : Map.copyOf(policyParameters);
        applicationFields = applicationFields == null ? Map.of() : Map.copyOf(applicationFields);
        policyRuleResults = policyRuleResults == null ? List.of() : List.copyOf(policyRuleResults);
        scoreResult = scoreResult == null ? Map.of() : Map.copyOf(scoreResult);
        scoreResultIds = scoreResultIds == null ? List.of() : List.copyOf(scoreResultIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        clock = clock != null ? clock
                : new FixedEvaluationClock(Instant.parse("2024-06-15T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public DecisionRuntimeInput withMetadata(Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>(this.metadata);
        if (extra != null) {
            m.putAll(extra);
        }
        return new DecisionRuntimeInput(
                tenantId, lenderId, productCode, applicationId, evaluationContextId,
                policyEvaluationId, policyPackageId, factSnapshotId, metricResultSetId,
                reconciliationResultSetId, scoreResultIds, creditEvidenceSummaryId,
                facts, metrics, reconciliations, policyParameters, applicationFields,
                policyOverallOutcome, policyRuleResults, scoreResult,
                requestedAmount, requestedTenureMonths, clock, m);
    }

    public static final class Builder {
        private UUID tenantId;
        private UUID lenderId;
        private String productCode;
        private UUID applicationId;
        private UUID evaluationContextId;
        private UUID policyEvaluationId;
        private UUID policyPackageId;
        private UUID factSnapshotId;
        private UUID metricResultSetId;
        private UUID reconciliationResultSetId;
        private List<UUID> scoreResultIds = List.of();
        private UUID creditEvidenceSummaryId;
        private Map<String, Object> facts = Map.of();
        private Map<String, Object> metrics = Map.of();
        private Map<String, Object> reconciliations = Map.of();
        private Map<String, Object> policyParameters = Map.of();
        private Map<String, Object> applicationFields = Map.of();
        private String policyOverallOutcome = "PASS";
        private List<Map<String, Object>> policyRuleResults = List.of();
        private Map<String, Object> scoreResult = Map.of();
        private BigDecimal requestedAmount;
        private Integer requestedTenureMonths;
        private EvaluationClock clock;
        private Map<String, Object> metadata = Map.of();

        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder lenderId(UUID v) { this.lenderId = v; return this; }
        public Builder productCode(String v) { this.productCode = v; return this; }
        public Builder applicationId(UUID v) { this.applicationId = v; return this; }
        public Builder evaluationContextId(UUID v) { this.evaluationContextId = v; return this; }
        public Builder policyEvaluationId(UUID v) { this.policyEvaluationId = v; return this; }
        public Builder policyPackageId(UUID v) { this.policyPackageId = v; return this; }
        public Builder factSnapshotId(UUID v) { this.factSnapshotId = v; return this; }
        public Builder metricResultSetId(UUID v) { this.metricResultSetId = v; return this; }
        public Builder reconciliationResultSetId(UUID v) { this.reconciliationResultSetId = v; return this; }
        public Builder scoreResultIds(List<UUID> v) { this.scoreResultIds = v; return this; }
        public Builder creditEvidenceSummaryId(UUID v) { this.creditEvidenceSummaryId = v; return this; }
        public Builder facts(Map<String, Object> v) { this.facts = v; return this; }
        public Builder metrics(Map<String, Object> v) { this.metrics = v; return this; }
        public Builder reconciliations(Map<String, Object> v) { this.reconciliations = v; return this; }
        public Builder policyParameters(Map<String, Object> v) { this.policyParameters = v; return this; }
        public Builder applicationFields(Map<String, Object> v) { this.applicationFields = v; return this; }
        public Builder policyOverallOutcome(String v) { this.policyOverallOutcome = v; return this; }
        public Builder policyRuleResults(List<Map<String, Object>> v) { this.policyRuleResults = v; return this; }
        public Builder scoreResult(Map<String, Object> v) { this.scoreResult = v; return this; }
        public Builder requestedAmount(BigDecimal v) { this.requestedAmount = v; return this; }
        public Builder requestedTenureMonths(Integer v) { this.requestedTenureMonths = v; return this; }
        public Builder clock(EvaluationClock v) { this.clock = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public DecisionRuntimeInput build() {
            return new DecisionRuntimeInput(
                    tenantId, lenderId, productCode, applicationId, evaluationContextId,
                    policyEvaluationId, policyPackageId, factSnapshotId, metricResultSetId,
                    reconciliationResultSetId, scoreResultIds, creditEvidenceSummaryId,
                    facts, metrics, reconciliations, policyParameters, applicationFields,
                    policyOverallOutcome, policyRuleResults, scoreResult,
                    requestedAmount, requestedTenureMonths, clock, metadata);
        }
    }
}
