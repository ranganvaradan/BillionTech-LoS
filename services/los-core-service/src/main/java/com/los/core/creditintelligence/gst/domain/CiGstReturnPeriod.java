package com.los.core.creditintelligence.gst.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_gst_return_period")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGstReturnPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "gst_registration_id", nullable = false)
    private UUID gstRegistrationId;

    @Column(name = "return_type", nullable = false, length = 40)
    private String returnType;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "period_yyyy_mm", nullable = false, length = 7)
    private String periodYyyyMm;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "filed_date")
    private LocalDate filedDate;

    @Column(name = "filing_status", nullable = false, length = 40)
    private String filingStatus;

    @Column(name = "filing_delay_days")
    private Integer filingDelayDays;

    @Column(name = "arn_reference", length = 120)
    private String arnReference;

    @Column(name = "is_effective", nullable = false)
    @Builder.Default
    private boolean effective = true;

    @Column(name = "superseded_by_period_id")
    private UUID supersededByPeriodId;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

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
