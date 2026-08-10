package com.los.core.creditintelligence.tax.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_itr_tax_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiItrTaxSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "itr_return_id", nullable = false)
    private UUID itrReturnId;

    @Column(name = "tax_liability", precision = 18, scale = 2)
    private BigDecimal taxLiability;

    @Column(name = "tax_payable", precision = 18, scale = 2)
    private BigDecimal taxPayable;

    @Column(name = "tax_paid", precision = 18, scale = 2)
    private BigDecimal taxPaid;

    @Column(name = "tds", precision = 18, scale = 2)
    private BigDecimal tds;

    @Column(name = "tcs", precision = 18, scale = 2)
    private BigDecimal tcs;

    @Column(name = "advance_tax", precision = 18, scale = 2)
    private BigDecimal advanceTax;

    @Column(name = "self_assessment_tax", precision = 18, scale = 2)
    private BigDecimal selfAssessmentTax;

    @Column(name = "mat_amt", precision = 18, scale = 2)
    private BigDecimal matAmt;

    @Column(name = "refund_claimed", precision = 18, scale = 2)
    private BigDecimal refundClaimed;

    @Column(name = "refund_received", precision = 18, scale = 2)
    private BigDecimal refundReceived;

    @Column(name = "outstanding_demand", precision = 18, scale = 2)
    private BigDecimal outstandingDemand;

    @Column(name = "interest_penalty", precision = 18, scale = 2)
    private BigDecimal interestPenalty;

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
