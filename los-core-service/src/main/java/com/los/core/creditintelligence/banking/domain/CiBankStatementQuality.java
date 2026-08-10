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
@Table(name = "ci_bank_statement_quality")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankStatementQuality {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    @Column(name = "source_record_id")
    private UUID sourceRecordId;

    @Column(name = "statement_from")
    private LocalDate statementFrom;

    @Column(name = "statement_to")
    private LocalDate statementTo;

    @Column(name = "periods_expected")
    private Integer periodsExpected;

    @Column(name = "periods_available")
    private Integer periodsAvailable;

    @Column(name = "calendar_days_expected")
    private Integer calendarDaysExpected;

    @Column(name = "calendar_days_covered")
    private Integer calendarDaysCovered;

    @Column(name = "completeness_ratio", precision = 8, scale = 4)
    private BigDecimal completenessRatio;

    @Column(name = "integrity_status", nullable = false, length = 40)
    @Builder.Default
    private String integrityStatus = "UNKNOWN";

    @Column(name = "balance_breaks", nullable = false)
    @Builder.Default
    private int balanceBreaks = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private int duplicateCount = 0;

    @Column(name = "classification_coverage", precision = 8, scale = 4)
    private BigDecimal classificationCoverage;

    @Column(name = "quality_score", precision = 8, scale = 4)
    private BigDecimal qualityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> findings = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
