package com.los.lms.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class LoanAccountSummary {

    private String applicationNumber;
    private String lmsReferenceId;
    private String loanStatus;
    private BigDecimal sanctionedAmount;
    private BigDecimal disbursedAmount;
    private BigDecimal outstandingPrincipal;
    private BigDecimal totalPaid;
    private BigDecimal overdueAmount;
    private int totalEmis;
    private int paidEmis;
    private int overdueEmis;
    private LocalDate nextEmiDate;
    private BigDecimal nextEmiAmount;
    private LocalDate lastPaymentDate;
    private int dpd;
    private boolean npaFlag;
    private String npaCategory;
    private LocalDate npaDate;
}
