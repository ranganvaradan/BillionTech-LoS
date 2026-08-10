package com.los.core.creditintelligence.policy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_stage_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyStageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "stage_code", nullable = false, length = 80)
    private String stageCode;

    @Column(name = "sequence", nullable = false)
    @Builder.Default
    private Integer sequence = 0;

    @Column(name = "outcome", length = 40)
    private String outcome;

    @Column(name = "continue_flag", nullable = false)
    @Builder.Default
    private Boolean continueFlag = true;

    @Column(name = "rule_count", nullable = false)
    @Builder.Default
    private Integer ruleCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> detail = Map.of();
}
