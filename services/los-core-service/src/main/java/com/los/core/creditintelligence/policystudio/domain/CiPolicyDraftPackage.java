package com.los.core.creditintelligence.policystudio.domain;

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
@Table(name = "ci_policy_draft_package")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyDraftPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_document_id", nullable = false)
    private UUID policyDocumentId;

    @Column(name = "package_status", nullable = false, length = 40)
    @Builder.Default
    private String packageStatus = DraftPackageStatus.DRAFT_ONLY.name();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> content = Map.of();

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "completeness_status", length = 40)
    private String completenessStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completeness_summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> completenessSummary = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_history", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> approvalHistory = List.of();

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "package_version", nullable = false)
    @Builder.Default
    private Integer packageVersion = 1;

    @Column(name = "dsl_version", length = 40)
    @Builder.Default
    private String dslVersion = "POLICY_DSL_V1";

    @Column(name = "dependency_graph_hash", length = 128)
    private String dependencyGraphHash;

    @Column(name = "checker_approved_at")
    private Instant checkerApprovedAt;

    @Column(name = "checker_approved_by", length = 120)
    private String checkerApprovedBy;

    @Column(name = "invalidated_by_edit", nullable = false)
    @Builder.Default
    private Boolean invalidatedByEdit = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
