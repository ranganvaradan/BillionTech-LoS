package com.billiontech.bankstatement.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryResponse {
    private Long statementId;
    private String bankName;
    private String accountHolderName;
    private String accountNumberMasked;
    private String statementPeriod;

    // Key metrics
    private BigDecimal avgBankBalance;
    private BigDecimal detectedSalaryAmount;
    private BigDecimal totalIncome;
    private BigDecimal foir;
    private BigDecimal creditworthinessScore;
    private BigDecimal repaymentCapacityScore;

    // Quick indicators
    private Integer totalTransactions;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private Integer bounceCount;
    private Integer redFlagCount;

    // Monthly trend
    private List<MonthlySummaryResponse> monthlySummaries;

    // Category breakdown
    private Map<String, BigDecimal> categoryBreakdown;
}
