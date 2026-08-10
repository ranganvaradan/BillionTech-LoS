package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_comparison")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "validation_run_id")
    private UUID validationRunId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "rule_id", nullable = false, length = 120)
    private String ruleId;

    @Column(name = "legacy_outcome", length = 40)
    private String legacyOutcome;

    @Column(name = "canonical_outcome", length = 40)
    private String canonicalOutcome;

    @Column(name = "difference_class", nullable = false, length = 60)
    private String differenceClass;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legacy_input", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> legacyInput = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_input", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> canonicalInput = Map.of();

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
