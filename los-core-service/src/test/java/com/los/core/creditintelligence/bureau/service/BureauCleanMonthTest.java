package com.los.core.creditintelligence.bureau.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BureauCleanMonthTest {

    @Test
    void stdWithNumericZeroIsClean_string000AloneIsNot() {
        assertTrue(BureauCleanMonth.isCleanMonth(0, "STD", "STD"));
        assertFalse(BureauCleanMonth.isCleanMonth(null, "000", "STD"));
        assertFalse(BureauCleanMonth.isCleanMonth(0, "*", "STD"));
        assertFalse(BureauCleanMonth.isCleanMonth(0, "", ""));
        assertFalse(BureauCleanMonth.isCleanMonth(0, "SPM", "STD"));
        assertFalse(BureauCleanMonth.isCleanMonth(0, "UNKNOWN", "STD"));
        assertTrue(BureauCleanMonth.isCleanMonth(0, "000", "STD"));
    }
}
