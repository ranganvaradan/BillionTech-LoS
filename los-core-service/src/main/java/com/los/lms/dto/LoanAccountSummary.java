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
    /** From Encore {@code fees[]} when synced (bl-core blFeeDue). */
    private BigDecimal encoreBlFeeDue;
    /** From Encore {@code fees[]} when synced (bl-core lenderFeeDue). */
    private BigDecimal encoreLenderFeeDue;
    /** JSON string: Encore {@code accountStatementEntries} when synced. */
    private String encoreAccountStatementEntriesJson;
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
