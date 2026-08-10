package com.los.core.creditintelligence.banking.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_bank_recurring_obligation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankRecurringObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    @Column(name = "lender_name", length = 200)
    private String lenderName;

    @Column(name = "detected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal detectedAmount;

    @Column(name = "frequency", nullable = false, length = 40)
    @Builder.Default
    private String frequency = "MONTHLY";

    @Column(name = "first_observed")
    private LocalDate firstObserved;

    @Column(name = "last_observed")
    private LocalDate lastObserved;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private int occurrenceCount = 0;

    @Column(name = "regularity_score", precision = 8, scale = 4)
    private BigDecimal regularityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_transaction_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceTransactionIds = List.of();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "method", nullable = false, length = 80)
    private String method;

    @Column(name = "method_version", nullable = false, length = 40)
    private String methodVersion;

    @Column(name = "estimated", nullable = false)
    @Builder.Default
    private boolean estimated = false;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
