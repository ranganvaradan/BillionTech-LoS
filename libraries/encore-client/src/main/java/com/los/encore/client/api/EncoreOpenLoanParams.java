package com.los.encore.client.api;

import java.math.BigDecimal;

/**
 * Parameters to open a loan OD account in Encore (legacy {@code openLoanAccount} payload subset).
 */
public record EncoreOpenLoanParams(
        String applicationNumber,
        String borrowerName,
        BigDecimal sanctionedAmount,
        BigDecimal interestRate,
        int tenureMonths,
        String productCode
) {}
