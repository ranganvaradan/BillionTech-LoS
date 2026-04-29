package com.billiontech.bankstatement.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummaryResponse {
    private Long id;
    private Integer year;
    private Integer month;
    private String monthLabel;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal avgEodBalance;
    private BigDecimal minEodBalance;
    private BigDecimal maxEodBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netCashFlow;
    private Integer creditCount;
    private Integer debitCount;
    private BigDecimal salaryAmount;
    private BigDecimal emiAmount;
    private Integer bounceCount;
    private Map<String, Object> categorySummary;
}
