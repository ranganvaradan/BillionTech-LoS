package com.los.core.creditintelligence.policystudio.graph;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_rule_graph_operand")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyRuleGraphOperand {

    public static final String RESOLVED = "RESOLVED";
    public static final String UNRESOLVED_CANONICAL_PARAMETER = "UNRESOLVED_CANONICAL_PARAMETER";

    public static final String USAGE_HARD_RULE = "HARD_RULE";
    public static final String USAGE_DECISION_RULE = "DECISION_RULE";
    public static final String USAGE_SCORING_FACTOR = "SCORING_FACTOR";
    public static final String USAGE_CALCULATION_INPUT = "CALCULATION_INPUT";
    public static final String USAGE_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "graph_id", nullable = false)
    private UUID graphId;

    @Column(name = "operand_path", nullable = false, length = 300)
    private String operandPath;

    @Column(name = "original_token", nullable = false, length = 300)
    private String originalToken;

    @Column(name = "canonical_parameter_id", length = 200)
    private String canonicalParameterId;

    @Column(name = "resolution_status", nullable = false, length = 60)
    private String resolutionStatus;

    @Column(name = "usage_type", nullable = false, length = 40)
    @Builder.Default
    private String usageType = USAGE_HARD_RULE;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private boolean required = true;

    @Column(name = "ref_kind", nullable = false, length = 40)
    @Builder.Default
    private String refKind = "METRIC";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
