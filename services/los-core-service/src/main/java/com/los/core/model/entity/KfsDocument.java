package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kfs_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KfsDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(nullable = false)
    private Integer tenureMonths;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal emiAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInterest;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRepayment;

    @Column(precision = 15, scale = 2)
    private BigDecimal processingFee;

    @Column(precision = 15, scale = 2)
    private BigDecimal stampDuty;

    @Column(precision = 15, scale = 2)
    private BigDecimal insurancePremium;

    @Column(precision = 15, scale = 2)
    private BigDecimal otherCharges;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalCostOfCredit;

    @Column(nullable = false)
    @Builder.Default
    private Integer coolingOffHours = 72;

    @Column(length = 500)
    private String grievanceMechanism;

    @Column(length = 500)
    private String lspDisclosure;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> additionalTerms;

    @Column(length = 500)
    private String documentStoragePath;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "GENERATED";

    private Instant acknowledgedAt;

    private UUID acknowledgedBy;

    private Instant coolingOffExpiresAt;

    @Builder.Default
    private boolean coolingOffCompleted = false;

    private Instant esignedAt;

    private String esignTransactionId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
