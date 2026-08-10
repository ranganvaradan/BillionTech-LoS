package com.los.core.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayuHashUtilTest {

    private static final String KEY = "g8yc8J";
    private static final String SALT = "BBc2U41mOZPLuteAYBW9wJ2Lnm9V9rQV";

    @Test
    void forwardHash_withUdf1AndUdf2_matchesPayuLosLoanExample() {
        String hash = PayuHashUtil.forwardHash(
                KEY,
                "LOS-a8e60342-1782294074622",
                "4442.50",
                "LOS Loan Repayment LOS-IND-20260623-19007",
                "VIGNESH S",
                "vigneshb1@bl.com",
                "240b65e5-5a10-4e6f-b536-2bc9a3d35fb0",
                "a8e60342-1381-4241-b5a4-ef700c0c455d",
                SALT);
        assertEquals(
                "9fc2732978710720a762d49409101c913e9cf47feff97687332adeb0d923b99c0784c6ece601fde76eeb64db242e2701bf21565757bc87ecb861e5b2b183d819",
                hash);
    }
}
