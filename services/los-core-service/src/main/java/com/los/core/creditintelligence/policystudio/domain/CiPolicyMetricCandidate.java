package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_metric_candidate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyMetricCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clause_id", nullable = false)
    private UUID clauseId;

    @Column(name = "metric_name", nullable = false, length = 200)
    private String metricName;

    @Column(name = "base_metric", length = 200)
    private String baseMetric;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expression", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> expression = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exclusions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> exclusions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inclusions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> inclusions = List.of();

    @Column(name = "period", length = 80)
    private String period;

    @Column(name = "aggregation", length = 80)
    private String aggregation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependencies", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> dependencies = List.of();

    @Column(name = "missing_data_policy", nullable = false, length = 40)
    @Builder.Default
    private String missingDataPolicy = "DATA_INSUFFICIENT";

    @Column(name = "candidate_canonical_code", length = 200)
    private String candidateCanonicalCode;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "review_status", nullable = false, length = 40)
    @Builder.Default
    private String reviewStatus = ReviewState.AI_DRAFTED.name();

    @Column(name = "system_metric_id", length = 120)
    private String systemMetricId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
