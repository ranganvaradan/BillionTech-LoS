package com.los.core.creditintelligence.reconciliation.domain;

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
@Table(name = "ci_reconciliation_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiReconciliationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reconciliation_code", nullable = false, length = 80)
    private String reconciliationCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "left_operand_definition", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> leftOperandDefinition = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "right_operand_definition", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> rightOperandDefinition = Map.of();

    @Column(name = "period_alignment_strategy", nullable = false, length = 80)
    private String periodAlignmentStrategy;

    @Column(name = "normalization_strategy", nullable = false, length = 80)
    @Builder.Default
    private String normalizationStrategy = "NONE";

    @Column(name = "variance_method", nullable = false, length = 80)
    private String varianceMethod;

    @Column(name = "warning_tolerance", precision = 12, scale = 4)
    private BigDecimal warningTolerance;

    @Column(name = "material_tolerance", precision = 12, scale = 4)
    private BigDecimal materialTolerance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_data_statuses", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> allowedDataStatuses = List.of();

    @Column(name = "minimum_completeness", precision = 8, scale = 4)
    private BigDecimal minimumCompleteness;

    @Column(name = "minimum_confidence", precision = 8, scale = 4)
    private BigDecimal minimumConfidence;

    @Column(name = "missing_data_policy", nullable = false, length = 40)
    @Builder.Default
    private String missingDataPolicy = "DATA_INSUFFICIENT";

    @Column(name = "explanation_strategy_version", nullable = false, length = 40)
    @Builder.Default
    private String explanationStrategyVersion = ReconciliationConstants.RECON_EXPLANATION_V1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependency_metric_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> dependencyMetricCodes = List.of();

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = ReconciliationDefinitionStatus.ACTIVE.name();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
