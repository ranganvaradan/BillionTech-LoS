package com.los.core.creditintelligence.policystudio.domain;

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
@Table(name = "ci_policy_authoring_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyAuthoringSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_document_id", nullable = false)
    private UUID policyDocumentId;

    @Column(name = "document_version", nullable = false)
    @Builder.Default
    private Integer documentVersion = 1;

    @Column(name = "product_scope", length = 120)
    private String productScope;

    @Column(name = "author", length = 120)
    private String author;

    @Column(name = "assigned_credit_manager", length = 120)
    private String assignedCreditManager;

    @Column(name = "assigned_checker", length = 120)
    private String assignedChecker;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "last_updated_at", nullable = false)
    @Builder.Default
    private Instant lastUpdatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "registry_version", nullable = false, length = 40)
    @Builder.Default
    private String registryVersion = "POLICY_AUTHORING_REGISTRY_V2";

    @Column(name = "interpretation_provider_version", length = 80)
    private String interpretationProviderVersion;

    @Column(name = "dsl_version", nullable = false, length = 40)
    @Builder.Default
    private String dslVersion = "POLICY_DSL_V1";

    @Column(name = "readiness_score", precision = 8, scale = 2)
    private BigDecimal readinessScore;

    @Column(name = "readiness_grade", length = 40)
    private String readinessGrade;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
