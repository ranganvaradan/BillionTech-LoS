package com.los.core.service.kfs.edi;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KfsWorkingDayCalculatorTest {

    @Test
    void addWorkingDays_skipsSunday() {
        // Monday 2026-06-01 + 3 working days => Thursday 2026-06-04 (no Sundays in between)
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate result = KfsWorkingDayCalculator.addWorkingDays(start, 3, List.of());
        assertEquals(LocalDate.of(2026, 6, 4), result);
    }

    @Test
    void addWorkingDays_skipsConfiguredHoliday() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate result = KfsWorkingDayCalculator.addWorkingDays(start, 3, List.of("2026-06-02"));
        assertEquals(LocalDate.of(2026, 6, 5), result);
    }
}
