package com.los.encore.client.api;

import java.math.BigDecimal;

/**
 * Parameters to open a loan OD account in Encore (bl-core parity: full {@code LoanOdAccountWSDto} fields).
 */
public record EncoreOpenLoanParams(
        String applicationNumber,
        String borrowerName,
        BigDecimal sanctionedAmount,
        BigDecimal interestRate,
        int tenureMonths,
        String productCode,
        // bl-core parity fields
        String branchCode,
        String customerId,
        String tenureUnit,
        BigDecimal penalInterestRate,
        int numberOfInstallments,
        String moratoriumType,
        int moratoriumPeriodMagnitude,
        String moratoriumPeriodUnit,
        boolean moratoriumNormalInterestRateApplicable,
        String moratoriumNormalInterestRate,
        String moratoriumInterestAccrualCalculation,
        // Geo from city/party
        String pinCode,
        String cityCode,
        String stateCode,
        String countryCode,
        // Co-lending (nullable)
        String colendingApplicable,
        String colenderProductCode,
        String colenderId,
        String colenderLendingRatio,
        String colenderNormalInterestRate,
        // Disbursement
        String disbursementDate,
        String userId
) {
    /**
     * Backward-compatible factory for callers that only have the original six fields.
     */
    public static EncoreOpenLoanParams minimal(String applicationNumber, String borrowerName,
                                                BigDecimal sanctionedAmount, BigDecimal interestRate,
                                                int tenureMonths, String productCode) {
        return new EncoreOpenLoanParams(
                applicationNumber, borrowerName, sanctionedAmount, interestRate, tenureMonths, productCode,
                null, null, "Month", BigDecimal.ZERO, tenureMonths,
                "None", 0, "Month", false, null, null,
                null, null, null, null,
                null, null, null, null, null,
                null, null
        );
    }
}
