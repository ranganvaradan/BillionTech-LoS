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
@Table(name = "ci_policy_test_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clause_id")
    private UUID clauseId;

    @Column(name = "rule_candidate_id")
    private UUID ruleCandidateId;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_facts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> inputFacts = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_metrics", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> inputMetrics = Map.of();

    @Column(name = "expected_outcome", nullable = false, length = 40)
    private String expectedOutcome;

    @Column(name = "boundary_case", nullable = false)
    @Builder.Default
    private Boolean boundaryCase = false;

    @Column(name = "generated_by", nullable = false, length = 80)
    @Builder.Default
    private String generatedBy = "SYSTEM";

    @Column(name = "generation_confidence", precision = 8, scale = 4)
    private BigDecimal generationConfidence;

    @Column(name = "review_status", nullable = false, length = 40)
    @Builder.Default
    private String reviewStatus = ReviewState.AI_DRAFTED.name();

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "ai_expected_outcome", length = 40)
    private String aiExpectedOutcome;

    @Column(name = "approved_at")
    private Instant approvedAt;

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
