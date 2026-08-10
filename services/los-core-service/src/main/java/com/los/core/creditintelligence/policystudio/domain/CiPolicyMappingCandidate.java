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
@Table(name = "ci_policy_mapping_candidate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyMappingCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "interpretation_id", nullable = false)
    private UUID interpretationId;

    @Column(name = "source_phrase", nullable = false, length = 500)
    private String sourcePhrase;

    @Column(name = "candidate_type", nullable = false, length = 40)
    @Builder.Default
    private String candidateType = "UNKNOWN";

    @Column(name = "canonical_path", length = 300)
    private String canonicalPath;

    @Column(name = "canonical_object_type", nullable = false, length = 40)
    @Builder.Default
    private String canonicalObjectType = CanonicalObjectType.UNKNOWN.name();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "match_basis", length = 80)
    private String matchBasis;

    @Column(name = "rank", nullable = false)
    @Builder.Default
    private Integer rank = 1;

    @Column(name = "selected", nullable = false)
    @Builder.Default
    private Boolean selected = false;

    @Column(name = "selection_source", length = 40)
    private String selectionSource;

    @Column(name = "selected_by", length = 120)
    private String selectedBy;

    @Column(name = "selected_at")
    private Instant selectedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
