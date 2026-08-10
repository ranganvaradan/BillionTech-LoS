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
@Table(name = "ci_itr_presumptive_income")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiItrPresumptiveIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "itr_return_id", nullable = false)
    private UUID itrReturnId;

    @Column(name = "applicable_section", nullable = false, length = 40)
    private String applicableSection;

    @Column(name = "gross_receipts", precision = 18, scale = 2)
    private BigDecimal grossReceipts;

    @Column(name = "digital_receipts", precision = 18, scale = 2)
    private BigDecimal digitalReceipts;

    @Column(name = "cash_receipts", precision = 18, scale = 2)
    private BigDecimal cashReceipts;

    @Column(name = "declared_presumptive_income", precision = 18, scale = 2)
    private BigDecimal declaredPresumptiveIncome;

    @Column(name = "declared_margin", precision = 8, scale = 4)
    private BigDecimal declaredMargin;

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
