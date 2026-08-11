package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "underwriting_evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnderwritingEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "aggregate_decision", nullable = false, length = 40)
    private String aggregateDecision;

    @Column(name = "aggregate_score")
    private Integer aggregateScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effective_values_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> effectiveValuesJson = Map.of();

    /** Stored as JSON array: one object per rule evaluated */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> ruleResultsJson = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_source_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> selectedSourceJson = Map.of();

    @Column(name = "scorecard_id")
    private UUID scorecardId;

    /** Immutable scorecard version at evaluation time (SCORECARD-SAFETY-FOUNDATION-1). */
    @Column(name = "scorecard_version")
    private Integer scorecardVersion;

    /**
     * Immutable scoring evidence snapshot: bands, provenance, thresholds, hard-rule outcome.
     * Do not reconstruct old decisions from today's mutable scorecard rows.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scorecard_evidence_json", columnDefinition = "jsonb")
    private Map<String, Object> scorecardEvidenceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_results_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> parameterResultsJson;

    /**
     * LOS-PRODUCTION-HARDENING-1 — full immutable decision/configuration snapshot.
     * Historical reads must use this blob; do not re-resolve current workflow/rules/scorecard.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_snapshot_json", columnDefinition = "jsonb")
    private Map<String, Object> decisionSnapshotJson;

    @Column(name = "evaluated_by", length = 64)
    private String evaluatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
