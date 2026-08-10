package com.los.core.service.kfs.edi;

import java.math.BigDecimal;

/** One repayment schedule line for EDI KFS Word tables. */
public record EdiKfsScheduleRow(
        int demandNumber,
        String demandDate,
        BigDecimal installmentAmount,
        BigDecimal normalInterestAmount,
        BigDecimal principalAmount,
        BigDecimal balance) {

    public String installmentAmountRs() {
        return "Rs." + format(installmentAmount);
    }

    public String normalInterestRs() {
        return "Rs." + format(normalInterestAmount);
    }

    public String principalRs() {
        return "Rs." + format(principalAmount);
    }

    public String balanceRs() {
        return "Rs." + format(balance);
    }

    private static String format(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
