package com.los.core.creditintelligence.domain;

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
@Table(name = "ci_source_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "purpose", nullable = false, length = 100)
    @Builder.Default
    private String purpose = "UNDERWRITING";

    @Column(name = "consent_id")
    private UUID consentId;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "CAPTURED";

    @Column(name = "quality_score", precision = 8, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "as_of")
    private Instant asOf;

    @Column(name = "stale_after")
    private Instant staleAfter;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
