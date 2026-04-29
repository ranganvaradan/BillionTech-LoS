package com.billiontech.bankstatement.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "statement_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", nullable = false, unique = true)
    private BankStatement statement;

    @Column(name = "avg_bank_balance")
    private BigDecimal avgBankBalance;

    @Column(name = "min_balance")
    private BigDecimal minBalance;

    @Column(name = "max_balance")
    private BigDecimal maxBalance;

    @Column(name = "balance_volatility")
    private BigDecimal balanceVolatility;

    @Column(name = "detected_salary_amount")
    private BigDecimal detectedSalaryAmount;

    @Column(name = "salary_frequency")
    private String salaryFrequency;

    @Column(name = "salary_day_of_month")
    private Integer salaryDayOfMonth;

    @Column(name = "salary_confidence")
    private BigDecimal salaryConfidence;

    @Column(name = "total_income")
    private BigDecimal totalIncome;

    @Column(name = "non_salary_income")
    private BigDecimal nonSalaryIncome;

    @Column(name = "imputed_income")
    private BigDecimal imputedIncome;

    @Column(name = "income_stability_score")
    private BigDecimal incomeStabilityScore;

    @Column(name = "emi_count")
    @Builder.Default
    private Integer emiCount = 0;

    @Column(name = "total_emi_amount")
    @Builder.Default
    private BigDecimal totalEmiAmount = BigDecimal.ZERO;

    @Column(name = "foir")
    private BigDecimal foir;

    @Column(name = "total_obligations")
    @Builder.Default
    private BigDecimal totalObligations = BigDecimal.ZERO;

    @Column(name = "rent_amount")
    @Builder.Default
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(name = "insurance_amount")
    @Builder.Default
    private BigDecimal insuranceAmount = BigDecimal.ZERO;

    @Column(name = "total_credits")
    @Builder.Default
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "total_debits")
    @Builder.Default
    private BigDecimal totalDebits = BigDecimal.ZERO;

    @Column(name = "net_cash_flow")
    private BigDecimal netCashFlow;

    @Column(name = "credit_debit_ratio")
    private BigDecimal creditDebitRatio;

    @Column(name = "cash_flow_stability")
    private BigDecimal cashFlowStability;

    @Column(name = "bounce_count")
    @Builder.Default
    private Integer bounceCount = 0;

    @Column(name = "bounce_amount")
    @Builder.Default
    private BigDecimal bounceAmount = BigDecimal.ZERO;

    @Column(name = "circular_txn_count")
    @Builder.Default
    private Integer circularTxnCount = 0;

    @Column(name = "cash_deposit_ratio")
    private BigDecimal cashDepositRatio;

    @Column(name = "cash_withdrawal_ratio")
    private BigDecimal cashWithdrawalRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "red_flags", columnDefinition = "jsonb")
    private List<Map<String, Object>> redFlags;

    @Column(name = "creditworthiness_score")
    private BigDecimal creditworthinessScore;

    @Column(name = "income_confidence_score")
    private BigDecimal incomeConfidenceScore;

    @Column(name = "repayment_capacity_score")
    private BigDecimal repaymentCapacityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_credit_sources", columnDefinition = "jsonb")
    private List<Map<String, Object>> topCreditSources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_debit_destinations", columnDefinition = "jsonb")
    private List<Map<String, Object>> topDebitDestinations;

    @Column(name = "analysis_completed_at")
    private LocalDateTime analysisCompletedAt;

    @Column(name = "analysis_version")
    @Builder.Default
    private String analysisVersion = "1.0";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
