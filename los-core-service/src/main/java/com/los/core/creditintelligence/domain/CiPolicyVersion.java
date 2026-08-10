package com.los.core.creditintelligence.domain;

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
@Table(name = "ci_policy_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_package_id", nullable = false)
    private UUID policyPackageId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_content", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> policyContent;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_policy_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> sourcePolicyReferences = List.of();

    @Column(name = "orchestration_version", nullable = false, length = 80)
    @Builder.Default
    private String orchestrationVersion = "LEGACY_UNDERWRITE_APPLICATION_V1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 64)
    private String publishedBy;
}
