package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_validation_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_code", nullable = false, length = 80)
    private String runCode;

    @Column(name = "case_code", length = 80)
    private String caseCode;

    @Column(name = "data_origin", nullable = false, length = 60)
    private String dataOrigin;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "config_freeze_id")
    private UUID configFreezeId;

    @Column(name = "metric_result_set_id")
    private UUID metricResultSetId;

    @Column(name = "reconciliation_result_set_id")
    private UUID reconciliationResultSetId;

    @Column(name = "deterministic_evaluation_hash", length = 128)
    private String deterministicEvaluationHash;

    @Column(name = "replay_hash", length = 128)
    private String replayHash;

    @Column(name = "replay_identical")
    private Boolean replayIdentical;

    @Column(name = "evidence_strength_score", precision = 8, scale = 2)
    private BigDecimal evidenceStrengthScore;

    @Column(name = "evidence_strength_grade", length = 40)
    private String evidenceStrengthGrade;

    @Column(name = "cutover_outcome", length = 40)
    private String cutoverOutcome;

    @Column(name = "duration_ms")
    private Long durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
