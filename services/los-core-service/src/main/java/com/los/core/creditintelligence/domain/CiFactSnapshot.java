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
@Table(name = "ci_fact_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiFactSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "snapshot_version", nullable = false)
    private Integer snapshotVersion;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "created_reason", nullable = false, length = 100)
    private String createdReason;

    @Column(name = "previous_snapshot_id")
    private UUID previousSnapshotId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "schema_version", nullable = false, length = 20)
    @Builder.Default
    private String schemaVersion = "F1";

    @Column(name = "facts_hash", length = 128)
    private String factsHash;

    @Column(name = "source_context_hash", length = 128)
    private String sourceContextHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
