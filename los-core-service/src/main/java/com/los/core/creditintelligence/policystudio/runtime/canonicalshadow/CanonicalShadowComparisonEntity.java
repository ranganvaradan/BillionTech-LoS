package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_canonical_shadow_comparison")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanonicalShadowComparisonEntity {

    @Id
    private UUID id;

    @Column(name = "shadow_evaluation_id", nullable = false)
    private UUID shadowEvaluationId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "underwriting_evaluation_id")
    private UUID underwritingEvaluationId;

    @Column(name = "identity_hash", nullable = false, length = 64)
    private String identityHash;

    @Column(name = "comparison_status", nullable = false, length = 40)
    private String comparisonStatus;

    @Column(name = "legacy_decision_at")
    private Instant legacyDecisionAt;

    @Column(name = "canonical_shadow_at", nullable = false)
    @Builder.Default
    private Instant canonicalShadowAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mismatch_counts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> mismatchCounts = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> mismatches = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_test_equivalence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> policyTestEquivalence = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legacy_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> legacyEvidence = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
