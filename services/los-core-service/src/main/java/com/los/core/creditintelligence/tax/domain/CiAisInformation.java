package com.los.core.creditintelligence.tax.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_ais_information")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAisInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ais_summary_id", nullable = false)
    private UUID aisSummaryId;

    @Column(name = "category", nullable = false, length = 64)
    @Builder.Default
    private String category = AisCategory.OTHER.name();

    @Column(name = "information_code", length = 80)
    private String informationCode;

    @Column(name = "reporting_entity", length = 300)
    private String reportingEntity;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "accepted_value", precision = 18, scale = 2)
    private BigDecimal acceptedValue;

    @Column(name = "modified_value", precision = 18, scale = 2)
    private BigDecimal modifiedValue;

    @Column(name = "status", length = 40)
    private String status;

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
