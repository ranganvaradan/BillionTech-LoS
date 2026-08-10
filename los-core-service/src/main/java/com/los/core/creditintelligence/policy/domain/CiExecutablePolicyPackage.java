package com.los.core.creditintelligence.policy.domain;

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
@Table(name = "ci_executable_policy_package")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiExecutablePolicyPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "policy_code", nullable = false, length = 120)
    private String policyCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = ExecutablePackageStatus.SHADOW.name();

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "dsl_version", nullable = false, length = 40)
    @Builder.Default
    private String dslVersion = "POLICY_DSL_V1";

    @Column(name = "evaluation_semantics_version", nullable = false, length = 80)
    @Builder.Default
    private String evaluationSemanticsVersion = "POLICY_DSL_EVALUATION_SEMANTICS_V1";

    @Column(name = "orchestration_version", nullable = false, length = 40)
    @Builder.Default
    private String orchestrationVersion = "ORCHESTRATION_V1";

    @Column(name = "fact_registry_version", length = 40)
    private String factRegistryVersion;

    @Column(name = "metric_registry_version", length = 40)
    private String metricRegistryVersion;

    @Column(name = "reconciliation_registry_version", length = 40)
    private String reconciliationRegistryVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> content = Map.of();

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "test_suite_hash", length = 128)
    private String testSuiteHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_document_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceDocumentRefs = List.of();

    @Column(name = "policy_studio_draft_package_id")
    private UUID policyStudioDraftPackageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> approvalMetadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 120)
    private String publishedBy;
}
