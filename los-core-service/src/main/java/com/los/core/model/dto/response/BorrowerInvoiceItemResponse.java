package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * A single PLP invoice surfaced to a borrower on the LOS Invoice discounting page.
 */
@Value
@Builder
public class BorrowerInvoiceItemResponse {
    String invoiceId;
    String invoiceNumber;
    String invoiceDate;
    String dueDate;
    BigDecimal invoiceAmount;
    BigDecimal netAmount;
    BigDecimal eligibleAmount;
    BigDecimal availableAmount;
    String status;
    String friendlyStatus;
    String programId;
    String anchorId;
    String flowType;
    /** Purchase-flow: borrower may accept an ELIGIBLE invoice before financing. */
    boolean acceptable;
    /** True when the borrower may request finance against this invoice now. */
    boolean financeable;
    /** Suggested maximum financeable amount (available amount, falling back to eligible/net). */
    BigDecimal maxFinanceableAmount;
    /** Default amount to pre-fill on the finance request form. */
    BigDecimal suggestedFinanceAmount;
    /** PRUS amount after PayU success, pending settlement. */
    BigDecimal pipAmount;
    String subProgramId;
    String isEarlyPayAllowed;
    String showEarlyPay;
    BigDecimal balDueAmount;
    boolean earlyPayable;
    /** Original digital invoice filename when attached by anchor. */
    String digitalInvoiceFileName;
    String digitalInvoiceContentType;
    /** True when borrower may delete this SBD/PO invoice (program flag + pre-finance status). */
    boolean deletable;
    /** Program-level borrower repayment switch for this invoice's program. */
    boolean enablePaymentForBorrower;
}
