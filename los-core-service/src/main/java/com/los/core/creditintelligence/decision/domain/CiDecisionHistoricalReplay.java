package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_decision_historical_replay")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDecisionHistoricalReplay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "strategy_id")
    private UUID strategyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> contextIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;
}
