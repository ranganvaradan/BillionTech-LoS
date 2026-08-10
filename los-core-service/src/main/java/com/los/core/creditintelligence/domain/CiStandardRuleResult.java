package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_standard_rule_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiStandardRuleResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "evaluation_stage_id")
    private UUID evaluationStageId;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, length = 40)
    @Builder.Default
    private String ruleVersion = "1";

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "rule_type", length = 40)
    private String ruleType;

    @Column(name = "engine_name", nullable = false, length = 80)
    private String engineName;

    @Column(name = "engine_version", nullable = false, length = 40)
    @Builder.Default
    private String engineVersion = "LEGACY";

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "severity", length = 40)
    private String severity;

    /**
     * Structured JSON (map envelope or source-values map). Must not be typed as {@code Object}:
     * Hibernate 6.5 JSON binder casts Object-typed attributes to String (HHH-19964).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actual_value", columnDefinition = "jsonb")
    private Map<String, Object> actualValue;

    @Column(name = "operator", length = 40)
    private String operator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "threshold_value", columnDefinition = "jsonb")
    private Map<String, Object> thresholdValue;

    @Column(name = "reason_code", length = 80)
    private String reasonCode;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fact_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> factReferences = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceReferences = List.of();

    @Column(name = "data_status", nullable = false, length = 40)
    @Builder.Default
    private String dataStatus = "AVAILABLE";

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "override_allowed", nullable = false)
    @Builder.Default
    private boolean overrideAllowed = false;

    @Column(name = "human_review_required", nullable = false)
    @Builder.Default
    private boolean humanReviewRequired = false;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> trace = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metric_refs", columnDefinition = "jsonb")
    @Builder.Default
    private List<Object> metricRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reconciliation_refs", columnDefinition = "jsonb")
    @Builder.Default
    private List<Object> reconciliationRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    @Builder.Default
    private List<Object> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_refs", columnDefinition = "jsonb")
    @Builder.Default
    private List<Object> inputRefs = List.of();
}
