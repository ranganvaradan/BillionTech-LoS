package com.los.core.creditintelligence.validation.domain;

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
@Table(name = "ci_obligation_match")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiObligationMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "reconciliation_result_id")
    private UUID reconciliationResultId;

    @Column(name = "match_status", nullable = false, length = 40)
    private String matchStatus;

    @Column(name = "bureau_lender", length = 300)
    private String bureauLender;

    @Column(name = "bank_detected_lender", length = 300)
    private String bankDetectedLender;

    @Column(name = "lender_identity_id")
    private UUID lenderIdentityId;

    @Column(name = "bureau_emi", precision = 18, scale = 2)
    private BigDecimal bureauEmi;

    @Column(name = "bank_observed_emi", precision = 18, scale = 2)
    private BigDecimal bankObservedEmi;

    @Column(name = "absolute_variance", precision = 18, scale = 2)
    private BigDecimal absoluteVariance;

    @Column(name = "percentage_variance", precision = 12, scale = 4)
    private BigDecimal percentageVariance;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signals", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> signals = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
