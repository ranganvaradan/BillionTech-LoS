package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_decision_input")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDecisionInput {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "policy_evaluation_id")
    private UUID policyEvaluationId;

    @Column(name = "policy_package_id")
    private UUID policyPackageId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @Column(name = "metric_result_set_id")
    private UUID metricResultSetId;

    @Column(name = "reconciliation_result_set_id")
    private UUID reconciliationResultSetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_result_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> scoreResultIds = List.of();

    @Column(name = "credit_evidence_summary_id")
    private UUID creditEvidenceSummaryId;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "decision_as_of")
    private LocalDate decisionAsOf;

    @Column(name = "decision_engine_version", length = 80)
    private String decisionEngineVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frozen_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> frozenPayload = Map.of();

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
