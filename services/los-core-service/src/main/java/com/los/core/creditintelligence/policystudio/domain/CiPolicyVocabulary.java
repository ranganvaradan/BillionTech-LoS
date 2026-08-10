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
@Table(name = "ci_policy_vocabulary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyVocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "scope_level", nullable = false, length = 20)
    @Builder.Default
    private String scopeLevel = "GLOBAL";

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "term", nullable = false, length = 200)
    private String term;

    @Column(name = "canonical_meaning", columnDefinition = "TEXT")
    private String canonicalMeaning;

    @Column(name = "canonical_path", length = 300)
    private String canonicalPath;

    @Column(name = "object_type", length = 40)
    private String objectType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "synonyms", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> synonyms = List.of();

    @Column(name = "context", length = 200)
    private String context;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private Instant effectiveFrom = Instant.now();

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "previously_approved_note", columnDefinition = "TEXT")
    private String previouslyApprovedNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
