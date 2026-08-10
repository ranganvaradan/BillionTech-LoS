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
@Table(name = "ci_policy_interpretation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyInterpretation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clause_id", nullable = false)
    private UUID clauseId;

    @Column(name = "interpretation_version", nullable = false)
    @Builder.Default
    private Integer interpretationVersion = 1;

    @Column(name = "interpreted_clause_type", length = 40)
    private String interpretedClauseType;

    @Column(name = "natural_language_meaning", columnDefinition = "TEXT")
    private String naturalLanguageMeaning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_expression", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> candidateExpression = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_inputs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> candidateInputs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_outputs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> candidateOutputs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_product_scope", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> candidateProductScope = List.of();

    @Column(name = "candidate_period", length = 80)
    private String candidatePeriod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_thresholds", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> candidateThresholds = Map.of();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_breakdown", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> confidenceBreakdown = Map.of();

    @Column(name = "limitations", columnDefinition = "TEXT")
    private String limitations;

    @Column(name = "ai_model", length = 120)
    private String aiModel;

    @Column(name = "ai_model_version", length = 80)
    private String aiModelVersion;

    @Column(name = "prompt_version", length = 80)
    private String promptVersion;

    @Column(name = "provider_code", nullable = false, length = 80)
    @Builder.Default
    private String providerCode = "DETERMINISTIC_GOLDEN_V1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
