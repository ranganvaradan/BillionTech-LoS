package com.los.core.creditintelligence.evaluation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_config_freeze",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ci_config_freeze_hash",
                columnNames = {"tenant_id", "content_hash"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiConfigFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "config_version", nullable = false, length = 80)
    private String configVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "schema_version", nullable = false, length = 40)
    @Builder.Default
    private String schemaVersion = "CONFIG_FREEZE_V1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
