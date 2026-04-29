package com.billiontech.bankstatement.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "monthly_summaries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"statement_id", "year", "month"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", nullable = false)
    private BankStatement statement;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "opening_balance")
    private BigDecimal openingBalance;

    @Column(name = "closing_balance")
    private BigDecimal closingBalance;

    @Column(name = "avg_eod_balance")
    private BigDecimal avgEodBalance;

    @Column(name = "min_eod_balance")
    private BigDecimal minEodBalance;

    @Column(name = "max_eod_balance")
    private BigDecimal maxEodBalance;

    @Column(name = "total_credits")
    @Builder.Default
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "total_debits")
    @Builder.Default
    private BigDecimal totalDebits = BigDecimal.ZERO;

    @Column(name = "net_cash_flow")
    private BigDecimal netCashFlow;

    @Column(name = "credit_count")
    @Builder.Default
    private Integer creditCount = 0;

    @Column(name = "debit_count")
    @Builder.Default
    private Integer debitCount = 0;

    @Column(name = "salary_amount")
    private BigDecimal salaryAmount;

    @Column(name = "emi_amount")
    private BigDecimal emiAmount;

    @Column(name = "bounce_count")
    @Builder.Default
    private Integer bounceCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_summary", columnDefinition = "jsonb")
    private Map<String, Object> categorySummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
