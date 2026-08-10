package com.los.core.creditintelligence.policy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ci_score_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiScoreResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "scorecard_code", length = 120)
    private String scorecardCode;

    @Column(name = "score", precision = 18, scale = 6)
    private BigDecimal score;

    @Column(name = "grade", length = 40)
    private String grade;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_results", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> componentResults = List.of();

    @Column(name = "weight_used", precision = 18, scale = 6)
    private BigDecimal weightUsed;

    @Column(name = "weight_unavailable", precision = 18, scale = 6)
    private BigDecimal weightUnavailable;

    @Column(name = "data_completeness", precision = 8, scale = 4)
    private BigDecimal dataCompleteness;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> reasonCodes = List.of();
}
