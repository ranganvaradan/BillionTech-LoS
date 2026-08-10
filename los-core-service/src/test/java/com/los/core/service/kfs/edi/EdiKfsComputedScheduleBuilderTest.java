package com.los.core.service.kfs.edi;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdiKfsComputedScheduleBuilderTest {

    @Test
    void buildRawSchedule_producesOneRowPerTenureDay() {
        List<Map<String, Object>> rows = EdiKfsComputedScheduleBuilder.buildRawSchedule(
                new BigDecimal("10000"),
                new BigDecimal("24"),
                5,
                null,
                LocalDate.of(2026, 7, 1));

        assertEquals(5, rows.size());
        assertEquals(1, rows.get(0).get("sequenceNum"));
        assertEquals("01-Jul-2026", rows.get(0).get("valueDateStr"));
        assertTrue(new BigDecimal(String.valueOf(rows.get(4).get("balance"))).compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void mapRows_fromComputedSchedule_hasRsAmounts() {
        List<EdiKfsScheduleRow> rows = EdiKfsComputedScheduleBuilder.build(
                new BigDecimal("10000"),
                new BigDecimal("24"),
                3,
                null,
                LocalDate.of(2026, 7, 1));

        assertFalse(rows.isEmpty());
        assertTrue(rows.get(0).installmentAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(rows.get(0).normalInterestAmount().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void calculateDailyInstallment_isPositiveForTypicalLoan() {
        BigDecimal daily = EdiKfsComputedScheduleBuilder.calculateDailyInstallment(
                new BigDecimal("85000"), new BigDecimal("18"), 90);
        assertTrue(daily.compareTo(BigDecimal.ZERO) > 0);
    }
}
