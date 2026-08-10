package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Borrower-facing loan account summary (post-disbursement), sourced from the LMS account summary
 * with safe local fallback. Mirrors the post-disbursement "account summary" view in legacy portals.
 */
@Value
@Builder
public class BorrowerLoanAccountResponse {
    String loanAccountNumber;
    String loanStatus;
    BigDecimal sanctionedAmount;
    BigDecimal disbursedAmount;
    BigDecimal outstandingPrincipal;
    BigDecimal totalPaid;
    BigDecimal overdueAmount;
    int totalEmis;
    int paidEmis;
    int overdueEmis;
    LocalDate nextEmiDate;
    BigDecimal nextEmiAmount;
    LocalDate lastPaymentDate;
    int dpd;
    /** True when post-disbursement servicing data is available (loan is disbursed). */
    boolean servicingActive;
    /** LOS catalog loan product code (e.g. PERSONAL_LOAN). */
    String loanProduct;
    /**
     * Effective repayment mechanism from {@code loan_product_repayment_defaults}
     * (SMART_COLLECT, PAYU_PG, …). Invoice discounting uses PLP instead.
     */
    String repaymentMechanism;
    /**
     * False until LOS personal/term loan PayU checkout is wired (v1 stores config only).
     */
    boolean payuCheckoutAvailable;
}
