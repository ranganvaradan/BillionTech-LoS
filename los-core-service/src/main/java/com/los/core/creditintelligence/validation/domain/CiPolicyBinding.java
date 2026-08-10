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
@Table(name = "ci_policy_binding",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ci_policy_binding",
                columnNames = {"tenant_id", "legacy_parameter"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legacy_parameter", nullable = false, length = 120)
    private String legacyParameter;

    @Column(name = "canonical_type", nullable = false, length = 40)
    private String canonicalType;

    @Column(name = "canonical_path", length = 200)
    private String canonicalPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_classifications", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> allowedClassifications = List.of();

    @Column(name = "missing_data_policy", length = 40)
    private String missingDataPolicy;

    @Column(name = "current_default_origin", length = 80)
    private String currentDefaultOrigin;

    @Column(name = "current_default_value", length = 120)
    private String currentDefaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "production_rules", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> productionRules = List.of();

    @Column(name = "ready", nullable = false)
    @Builder.Default
    private boolean ready = false;

    @Column(name = "critical", nullable = false)
    @Builder.Default
    private boolean critical = false;

    /** G0 binding certification — UNMAPPED|MAPPED|TESTED|CERTIFIED|BLOCKED */
    @Column(name = "certification_status", length = 40)
    @Builder.Default
    private String certificationStatus = "UNMAPPED";

    @Column(name = "certified_by", length = 120)
    private String certifiedBy;

    @Column(name = "certified_at")
    private Instant certifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
