package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_credit_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiCreditEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "fact_snapshot_id", nullable = false)
    private UUID factSnapshotId;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Column(name = "evaluation_type", nullable = false, length = 40)
    private String evaluationType;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "authoritative", nullable = false)
    @Builder.Default
    private boolean authoritative = false;

    @Column(name = "production_evaluation_id")
    private UUID productionEvaluationId;

    @Column(name = "orchestration_version", nullable = false, length = 80)
    private String orchestrationVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "overall_outcome", length = 40)
    private String overallOutcome;

    @Column(name = "comparison_status", length = 40)
    private String comparisonStatus;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    private Map<String, Object> errorDetails;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
