package com.billiontech.bankstatement.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResponse {
    private Long id;
    private Long statementId;

    // Balance metrics
    private BigDecimal avgBankBalance;
    private BigDecimal minBalance;
    private BigDecimal maxBalance;
    private BigDecimal balanceVolatility;

    // Income
    private BigDecimal detectedSalaryAmount;
    private String salaryFrequency;
    private Integer salaryDayOfMonth;
    private BigDecimal salaryConfidence;
    private BigDecimal totalIncome;
    private BigDecimal nonSalaryIncome;
    private BigDecimal imputedIncome;
    private BigDecimal incomeStabilityScore;

    // Obligations
    private Integer emiCount;
    private BigDecimal totalEmiAmount;
    private BigDecimal foir;
    private BigDecimal totalObligations;
    private BigDecimal rentAmount;
    private BigDecimal insuranceAmount;

    // Cash flow
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netCashFlow;
    private BigDecimal creditDebitRatio;
    private BigDecimal cashFlowStability;

    // Risk
    private Integer bounceCount;
    private BigDecimal bounceAmount;
    private Integer circularTxnCount;
    private BigDecimal cashDepositRatio;
    private BigDecimal cashWithdrawalRatio;
    private List<Map<String, Object>> redFlags;

    // Scores
    private BigDecimal creditworthinessScore;
    private BigDecimal incomeConfidenceScore;
    private BigDecimal repaymentCapacityScore;

    // Top sources
    private List<Map<String, Object>> topCreditSources;
    private List<Map<String, Object>> topDebitDestinations;

    private LocalDateTime analysisCompletedAt;
    private String analysisVersion;
}
