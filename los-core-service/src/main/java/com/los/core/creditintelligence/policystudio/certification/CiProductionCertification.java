package com.los.core.creditintelligence.policystudio.certification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA mapping for {@code ci_production_certification} (V142).
 * Primary Wave-8 unit tests use {@link InMemoryProductionCertificationLedger}.
 */
@Entity
@Table(name = "ci_production_certification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiProductionCertification {

    @Id
    private UUID id;

    @Column(name = "artifact_type", nullable = false, length = 64)
    private String artifactType;

    @Column(name = "artifact_id", nullable = false, length = 300)
    private String artifactId;

    @Column(name = "artifact_version", nullable = false, length = 120)
    private String artifactVersion;

    @Column(name = "scope_type", nullable = false, length = 40)
    private String scopeType;

    @Column(name = "scope_id", length = 120)
    private String scopeId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "certified_by", length = 200)
    private String certifiedBy;

    @Column(name = "certified_at")
    private Instant certifiedAt;

    @Column(name = "revoked_by", length = 200)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "evidence_summary")
    private String evidenceSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", columnDefinition = "jsonb")
    private Map<String, Object> evidenceJson;

    @Column(name = "semantic_catalogue_version", length = 80)
    private String semanticCatalogueVersion;

    @Column(name = "engine_version", length = 80)
    private String engineVersion;

    @Column(name = "producer_version", length = 80)
    private String producerVersion;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
