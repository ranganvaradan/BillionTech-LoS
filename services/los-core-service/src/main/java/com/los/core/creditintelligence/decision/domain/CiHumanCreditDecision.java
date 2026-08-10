package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Future human decision handoff. P2 never writes automatically.
 */
@Entity
@Table(name = "ci_human_credit_decision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiHumanCreditDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "human_decision", length = 40)
    private String humanDecision;

    @Column(name = "final_amount", precision = 18, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "final_tenure")
    private Integer finalTenure;

    @Column(name = "final_rate", precision = 12, scale = 6)
    private BigDecimal finalRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> conditions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deviations", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> deviations = new ArrayList<>();

    @Column(name = "decision_maker", length = 120)
    private String decisionMaker;

    @Column(name = "authority", length = 80)
    private String authority;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
