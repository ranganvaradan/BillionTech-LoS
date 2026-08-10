package com.los.core.creditintelligence.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_credit_evidence_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiCreditEvidenceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "turnover_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> turnoverEvidence = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "obligation_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> obligationEvidence = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "income_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> incomeEvidence = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tax_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> taxEvidence = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_quality", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> sourceQuality = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reconciliation_quality", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> reconciliationQuality = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_conflicts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> materialConflicts = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_gaps", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> dataGaps = List.of();

    @Column(name = "evidence_strength_score", precision = 8, scale = 2)
    private BigDecimal evidenceStrengthScore;

    @Column(name = "evidence_strength_grade", length = 40)
    private String evidenceStrengthGrade;

    @Column(name = "method_version", nullable = false, length = 40)
    @Builder.Default
    private String methodVersion = ReconciliationConstants.CREDIT_EVIDENCE_SUMMARY_V1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
