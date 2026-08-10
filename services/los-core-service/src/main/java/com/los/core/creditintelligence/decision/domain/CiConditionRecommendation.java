package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ci_condition_recommendation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiConditionRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "condition_type", nullable = false, length = 40)
    private String conditionType;

    @Column(name = "code", length = 120)
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "source", length = 120)
    private String source;

    @Column(name = "causing_rule_or_recon", length = 200)
    private String causingRuleOrRecon;

    @Column(name = "mandatory_before", length = 80)
    private String mandatoryBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = new ArrayList<>();
}
