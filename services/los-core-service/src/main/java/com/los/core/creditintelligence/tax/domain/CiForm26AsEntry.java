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
@Table(name = "ci_form26as_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiForm26AsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "form26as_summary_id", nullable = false)
    private UUID form26asSummaryId;

    @Column(name = "section_code", length = 40)
    private String sectionCode;

    @Column(name = "deductor_name", length = 300)
    private String deductorName;

    @Column(name = "deductor_tan_hash", length = 128)
    private String deductorTanHash;

    @Column(name = "booking_date")
    private LocalDate bookingDate;

    @Column(name = "amount_paid_credited", precision = 18, scale = 2)
    private BigDecimal amountPaidCredited;

    @Column(name = "tax_deducted", precision = 18, scale = 2)
    private BigDecimal taxDeducted;

    @Column(name = "tax_deposited", precision = 18, scale = 2)
    private BigDecimal taxDeposited;

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
