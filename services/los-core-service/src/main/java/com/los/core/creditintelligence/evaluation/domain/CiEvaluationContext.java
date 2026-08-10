package com.los.core.creditintelligence.evaluation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_evaluation_context",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ci_eval_ctx_hash",
                columnNames = {"tenant_id", "application_id", "content_hash"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiEvaluationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "fact_snapshot_id", nullable = false)
    private UUID factSnapshotId;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "config_freeze_id", nullable = false)
    private UUID configFreezeId;

    @Column(name = "evaluation_as_of", nullable = false)
    private LocalDate evaluationAsOf;

    @Column(name = "clock_instant", nullable = false)
    private Instant clockInstant;

    @Column(name = "clock_zone", nullable = false, length = 64)
    @Builder.Default
    private String clockZone = "Asia/Kolkata";

    @Column(name = "metric_result_set_id")
    private UUID metricResultSetId;

    @Column(name = "reconciliation_result_set_id")
    private UUID reconciliationResultSetId;

    @Column(name = "schema_version", nullable = false, length = 40)
    @Builder.Default
    private String schemaVersion = "EVALUATION_CONTEXT_V1";

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "deterministic_evaluation_hash", length = 128)
    private String deterministicEvaluationHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
