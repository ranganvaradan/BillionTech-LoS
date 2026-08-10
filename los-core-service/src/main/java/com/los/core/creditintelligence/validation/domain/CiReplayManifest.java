package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_replay_manifest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiReplayManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "evaluation_context_id", nullable = false)
    private UUID evaluationContextId;

    @Column(name = "fact_snapshot_hash", length = 128)
    private String factSnapshotHash;

    @Column(name = "policy_version_hash", length = 128)
    private String policyVersionHash;

    @Column(name = "config_freeze_hash", length = 128)
    private String configFreezeHash;

    @Column(name = "metric_result_set_hash", length = 128)
    private String metricResultSetHash;

    @Column(name = "reconciliation_result_set_hash", length = 128)
    private String reconciliationResultSetHash;

    @Column(name = "deterministic_evaluation_hash", length = 128)
    private String deterministicEvaluationHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parser_versions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> parserVersions = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalizer_versions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> normalizerVersions = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> sourceReferences = List.of();

    @Column(name = "schema_version", nullable = false, length = 40)
    @Builder.Default
    private String schemaVersion = "CREDIT_DECISION_REPLAY_MANIFEST_V1";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
