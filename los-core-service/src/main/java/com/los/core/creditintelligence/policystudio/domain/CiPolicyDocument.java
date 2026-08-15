package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_document",
        uniqueConstraints = @UniqueConstraint(name = "uq_ci_policy_doc_hash",
                columnNames = {"tenant_id", "content_hash", "document_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyDocument implements Persistable<UUID> {

    /**
     * Application always assigns IDs in PolicyDocumentService / orchestrator.
     * No {@code @GeneratedValue} — that made Spring Data treat assigned UUIDs as
     * not-new (merge/update) and Hibernate reject {@code persist()} as detached.
     */
    @Id
    private UUID id;

    /**
     * Spring Data {@code isNew()} — true until loaded/persisted via JPA.
     * Deep-copied in-memory sessions default to true so first durable write INSERTs.
     */
    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void markNew() {
        this.newEntity = true;
    }

    public void markNotNew() {
        this.newEntity = false;
    }

    @PostLoad
    @PostPersist
    void touchPersistenceState() {
        this.newEntity = false;
    }

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(name = "product_scope", length = 120)
    private String productScope;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "document_type", nullable = false, length = 40)
    @Builder.Default
    private String documentType = "TXT";

    @Column(name = "original_file_reference", length = 500)
    private String originalFileReference;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = DocumentStatus.UPLOADED.name();

    @Column(name = "document_version", nullable = false)
    @Builder.Default
    private Integer documentVersion = 1;

    @Column(name = "language", nullable = false, length = 20)
    @Builder.Default
    private String language = "en";

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /** DP-3 — optional Scorecard Version linked to this Policy Version (0..1). */
    @Column(name = "scorecard_id")
    private UUID scorecardId;

    /** DP-3 — when true, persisted rule graph must not be rewritten in place. */
    @Column(name = "rule_graph_immutable", nullable = false)
    @Builder.Default
    private Boolean ruleGraphImmutable = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
