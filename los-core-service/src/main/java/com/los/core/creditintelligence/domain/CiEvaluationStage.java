package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_evaluation_stage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiEvaluationStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "stage_code", nullable = false, length = 80)
    private String stageCode;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequence;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "outcome", length = 40)
    private String outcome;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> trace = Map.of();
}
