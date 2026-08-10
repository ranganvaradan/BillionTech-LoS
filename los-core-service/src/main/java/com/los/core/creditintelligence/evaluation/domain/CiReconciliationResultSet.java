package com.los.core.creditintelligence.evaluation.domain;

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
@Table(name = "ci_reconciliation_result_set")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiReconciliationResultSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reconciliation_result_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> reconciliationResultIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reconciliation_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> reconciliationCodes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_versions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> definitionVersions = Map.of();

    @Column(name = "metric_result_set_id")
    private UUID metricResultSetId;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "schema_version", nullable = false, length = 40)
    @Builder.Default
    private String schemaVersion = "RECON_RESULT_SET_V1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
