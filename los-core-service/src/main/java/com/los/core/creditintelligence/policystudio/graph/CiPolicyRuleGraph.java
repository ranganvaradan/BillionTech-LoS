package com.los.core.creditintelligence.policystudio.graph;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_rule_graph",
        uniqueConstraints = @UniqueConstraint(name = "uq_ci_policy_rule_graph_doc",
                columnNames = {"policy_document_id", "document_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyRuleGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_document_id", nullable = false)
    private UUID policyDocumentId;

    @Column(name = "policy_version_label", nullable = false, length = 80)
    @Builder.Default
    private String policyVersionLabel = "DRAFT";

    @Column(name = "document_version", nullable = false)
    @Builder.Default
    private int documentVersion = 1;

    @Column(name = "graph_hash", nullable = false, length = 128)
    private String graphHash;

    @Column(name = "source_snapshot_hash", length = 128)
    private String sourceSnapshotHash;

    @Column(name = "immutable", nullable = false)
    @Builder.Default
    private boolean immutable = false;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "MATERIALIZED";

    @Column(name = "unresolved_operand_count", nullable = false)
    @Builder.Default
    private int unresolvedOperandCount = 0;

    @Column(name = "rule_count", nullable = false)
    @Builder.Default
    private int ruleCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
