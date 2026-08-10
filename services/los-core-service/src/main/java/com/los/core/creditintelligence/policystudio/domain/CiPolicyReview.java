package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_document_id", nullable = false)
    private UUID policyDocumentId;

    @Column(name = "subject_type", nullable = false, length = 40)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "review_state", nullable = false, length = 40)
    private String reviewState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_proposal", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> originalProposal = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "human_changes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> humanChanges = Map.of();

    @Column(name = "reviewer", nullable = false, length = 120)
    private String reviewer;

    @Column(name = "reviewer_role", length = 40)
    private String reviewerRole;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
