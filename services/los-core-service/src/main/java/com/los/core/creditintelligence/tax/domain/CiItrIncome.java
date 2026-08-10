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
@Table(name = "ci_itr_income")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiItrIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "itr_return_id", nullable = false)
    private UUID itrReturnId;

    @Column(name = "salary_income", precision = 18, scale = 2)
    private BigDecimal salaryIncome;

    @Column(name = "house_property_income", precision = 18, scale = 2)
    private BigDecimal housePropertyIncome;

    @Column(name = "business_profession_income", precision = 18, scale = 2)
    private BigDecimal businessProfessionIncome;

    @Column(name = "capital_gains", precision = 18, scale = 2)
    private BigDecimal capitalGains;

    @Column(name = "other_sources", precision = 18, scale = 2)
    private BigDecimal otherSources;

    @Column(name = "gross_total_income", precision = 18, scale = 2)
    private BigDecimal grossTotalIncome;

    @Column(name = "chapter_via_deductions", precision = 18, scale = 2)
    private BigDecimal chapterViaDeductions;

    @Column(name = "total_income", precision = 18, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "exempt_income", precision = 18, scale = 2)
    private BigDecimal exemptIncome;

    @Column(name = "agricultural_income", precision = 18, scale = 2)
    private BigDecimal agriculturalIncome;

    @Column(name = "foreign_income", precision = 18, scale = 2)
    private BigDecimal foreignIncome;

    @Column(name = "extraction_quality", nullable = false, length = 40)
    @Builder.Default
    private String extractionQuality = "OK";

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
