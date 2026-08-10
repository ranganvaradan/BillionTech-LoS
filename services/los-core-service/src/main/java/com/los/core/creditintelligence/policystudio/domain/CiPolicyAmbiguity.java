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
@Table(name = "ci_policy_ambiguity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyAmbiguity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clause_id", nullable = false)
    private UUID clauseId;

    @Column(name = "ambiguity_type", nullable = false, length = 60)
    private String ambiguityType;

    @Column(name = "phrase", length = 500)
    private String phrase;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_options", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> candidateOptions = List.of();

    @Column(name = "recommended_option", length = 500)
    private String recommendedOption;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "severity", nullable = false, length = 40)
    @Builder.Default
    private String severity = "MATERIAL";

    @Column(name = "resolution_status", nullable = false, length = 40)
    @Builder.Default
    private String resolutionStatus = "OPEN";

    @Column(name = "resolved_option", length = 500)
    private String resolvedOption;

    @Column(name = "resolved_by", length = 120)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_resolution", columnDefinition = "jsonb")
    private List<Object> previousResolution;

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
