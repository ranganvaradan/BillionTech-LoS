package com.los.core.creditintelligence.policy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "policy_package_id")
    private UUID policyPackageId;

    @Column(name = "policy_version", columnDefinition = "text")
    private String policyVersion;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "overall_outcome", length = 40)
    private String overallOutcome;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deterministic_hash", length = 128)
    private String deterministicHash;

    @Column(name = "stage_count", nullable = false)
    @Builder.Default
    private Integer stageCount = 0;

    @Column(name = "rule_count", nullable = false)
    @Builder.Default
    private Integer ruleCount = 0;

    @Column(name = "pass_count", nullable = false)
    @Builder.Default
    private Integer passCount = 0;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private Integer failCount = 0;

    @Column(name = "refer_count", nullable = false)
    @Builder.Default
    private Integer referCount = 0;

    @Column(name = "di_count", nullable = false)
    @Builder.Default
    private Integer diCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> explanation = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_result", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> scoreResult = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison_summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> comparisonSummary = Map.of();

    /** In-memory rule results (not persisted via FK to ci_credit_evaluation). */
    @Transient
    @Builder.Default
    private List<com.los.core.creditintelligence.domain.CiStandardRuleResult> ruleResults = List.of();

    @Transient
    @Builder.Default
    private List<CiPolicyStageResult> stageResults = List.of();
}
