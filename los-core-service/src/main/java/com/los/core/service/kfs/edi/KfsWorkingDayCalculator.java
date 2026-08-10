package com.los.core.service.kfs.edi;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes KFS validity date as today + N working days (skips Sundays and configured holidays).
 */
public final class KfsWorkingDayCalculator {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private KfsWorkingDayCalculator() {
    }

    public static LocalDate addWorkingDays(LocalDate start, int workingDays, List<String> holidayIsoDates) {
        Set<LocalDate> holidays = parseHolidays(holidayIsoDates);
        LocalDate cursor = start;
        int added = 0;
        while (added < workingDays) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            if (holidays.contains(cursor)) {
                continue;
            }
            added++;
        }
        return cursor;
    }

    private static Set<LocalDate> parseHolidays(List<String> holidayIsoDates) {
        Set<LocalDate> out = new HashSet<>();
        if (holidayIsoDates == null) {
            return out;
        }
        for (String raw : holidayIsoDates) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                out.add(LocalDate.parse(raw.trim(), ISO));
            } catch (DateTimeParseException ignored) {
                // skip invalid config entries
            }
        }
        return out;
    }
}
