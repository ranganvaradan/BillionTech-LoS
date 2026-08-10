package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_rule_candidate",
        uniqueConstraints = @UniqueConstraint(name = "uq_ci_policy_rule_sys",
                columnNames = {"system_rule_id", "rule_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyRuleCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clause_id", nullable = false)
    private UUID clauseId;

    @Column(name = "system_rule_id", nullable = false, length = 120)
    private String systemRuleId;

    @Column(name = "rule_version", nullable = false, length = 40)
    @Builder.Default
    private String ruleVersion = "DRAFT";

    @Column(name = "rule_type", nullable = false, length = 40)
    @Builder.Default
    private String ruleType = "HARD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> scope = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expression", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> expression = Map.of();

    @Column(name = "on_true", nullable = false, length = 40)
    @Builder.Default
    private String onTrue = "FAIL";

    @Column(name = "on_false", nullable = false, length = 40)
    @Builder.Default
    private String onFalse = "PASS";

    @Column(name = "on_missing", nullable = false, length = 40)
    @Builder.Default
    private String onMissing = "DATA_INSUFFICIENT";

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "review_status", nullable = false, length = 40)
    @Builder.Default
    private String reviewStatus = ReviewState.AI_DRAFTED.name();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lineage", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> lineage = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "unit_left", length = 40)
    private String unitLeft;

    @Column(name = "unit_right", length = 40)
    private String unitRight;

    @Column(name = "period_semantics", length = 80)
    private String periodSemantics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> validationErrors = List.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
