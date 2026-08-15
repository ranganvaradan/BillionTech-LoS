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
@Table(name = "ci_policy_rule_graph_node",
        uniqueConstraints = @UniqueConstraint(name = "uq_ci_policy_rule_graph_node",
                columnNames = {"graph_id", "rule_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyRuleGraphNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "graph_id", nullable = false)
    private UUID graphId;

    @Column(name = "rule_key", nullable = false, length = 160)
    private String ruleKey;

    @Column(name = "system_rule_id", length = 160)
    private String systemRuleId;

    @Column(name = "rule_type", nullable = false, length = 40)
    @Builder.Default
    private String ruleType = "HARD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expression", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> expression = Map.of();

    @Column(name = "on_true", nullable = false, length = 40)
    @Builder.Default
    private String onTrue = "FAIL";

    @Column(name = "on_false", nullable = false, length = 40)
    @Builder.Default
    private String onFalse = "PASS";

    @Column(name = "on_missing", nullable = false, length = 40)
    @Builder.Default
    private String onMissing = "DATA_INSUFFICIENT";

    @Column(name = "human_wording", columnDefinition = "text")
    private String humanWording;

    @Column(name = "source_snapshot_ref", length = 200)
    private String sourceSnapshotRef;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
