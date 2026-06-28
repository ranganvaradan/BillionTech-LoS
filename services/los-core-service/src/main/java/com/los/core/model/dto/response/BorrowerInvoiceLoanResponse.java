package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * A PLP invoice-discounting loan surfaced to a borrower (for tracking and repayment).
 */
@Value
@Builder
public class BorrowerInvoiceLoanResponse {
    String loanId;
    String loanNumber;
    String invoiceId;
    String status;
    String friendlyStatus;
    BigDecimal requestedAmount;
    BigDecimal sanctionedAmount;
    BigDecimal disbursedAmount;
    BigDecimal outstandingAmount;
    BigDecimal totalRepayable;
    BigDecimal totalRepaid;
    String dueDate;
    /** True when the loan can accept a repayment now (disbursed / repayment-due / overdue). */
    boolean repayable;
}
