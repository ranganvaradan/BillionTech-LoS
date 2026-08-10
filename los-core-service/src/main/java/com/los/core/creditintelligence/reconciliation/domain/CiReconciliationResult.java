package com.los.core.creditintelligence.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_reconciliation_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @Column(name = "reconciliation_definition_id")
    private UUID reconciliationDefinitionId;

    @Column(name = "reconciliation_code", nullable = false, length = 80)
    private String reconciliationCode;

    @Column(name = "definition_version", nullable = false, length = 40)
    private String definitionVersion;

    @Column(name = "left_metric_code", length = 120)
    private String leftMetricCode;

    @Column(name = "left_metric_version", length = 40)
    private String leftMetricVersion;

    @Column(name = "left_value", precision = 18, scale = 2)
    private BigDecimal leftValue;

    @Column(name = "left_period_from")
    private LocalDate leftPeriodFrom;

    @Column(name = "left_period_to")
    private LocalDate leftPeriodTo;

    @Column(name = "left_completeness", precision = 8, scale = 4)
    private BigDecimal leftCompleteness;

    @Column(name = "left_confidence", precision = 8, scale = 4)
    private BigDecimal leftConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "left_source_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> leftSourceRefs = List.of();

    @Column(name = "right_metric_code", length = 120)
    private String rightMetricCode;

    @Column(name = "right_metric_version", length = 40)
    private String rightMetricVersion;

    @Column(name = "right_value", precision = 18, scale = 2)
    private BigDecimal rightValue;

    @Column(name = "right_period_from")
    private LocalDate rightPeriodFrom;

    @Column(name = "right_period_to")
    private LocalDate rightPeriodTo;

    @Column(name = "right_completeness", precision = 8, scale = 4)
    private BigDecimal rightCompleteness;

    @Column(name = "right_confidence", precision = 8, scale = 4)
    private BigDecimal rightConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "right_source_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> rightSourceRefs = List.of();

    @Column(name = "normalized_left_value", precision = 18, scale = 2)
    private BigDecimal normalizedLeftValue;

    @Column(name = "normalized_right_value", precision = 18, scale = 2)
    private BigDecimal normalizedRightValue;

    @Column(name = "absolute_variance", precision = 18, scale = 2)
    private BigDecimal absoluteVariance;

    @Column(name = "percentage_variance", precision = 12, scale = 4)
    private BigDecimal percentageVariance;

    @Column(name = "variance_denominator_method", length = 80)
    private String varianceDenominatorMethod;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "severity", length = 40)
    private String severity;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> explanationCodes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "probable_causes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> probableCauses = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_group_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceGroupIds = List.of();

    @Column(name = "human_review_required", nullable = false)
    @Builder.Default
    private boolean humanReviewRequired = false;

    @Column(name = "data_status", nullable = false, length = 40)
    @Builder.Default
    private String dataStatus = ReconciliationDataStatus.COMPLETE.name();

    @Column(name = "subject_match_status", nullable = false, length = 40)
    @Builder.Default
    private String subjectMatchStatus = SubjectMatchStatus.UNKNOWN.name();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "tolerance_version", length = 80)
    private String toleranceVersion;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> trace = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
