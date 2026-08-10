package com.los.core.creditintelligence.gst.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Indian financial year helpers (Apr–Mar) and Karza MMYYYY period parsing.
 */
public final class GstPeriodUtils {

    private GstPeriodUtils() {
    }

    /**
     * Parse Karza {@code retPeriod} MMYYYY (e.g. {@code "042026"}) to {@code YYYY-MM}.
     */
    public static Optional<String> parseMmyyyyToYyyyMm(String mmyyyy) {
        if (mmyyyy == null || mmyyyy.isBlank()) {
            return Optional.empty();
        }
        String s = mmyyyy.trim();
        if (s.length() != 6) {
            // also accept YYYY-MM already
            if (s.matches("\\d{4}-\\d{2}")) {
                return Optional.of(s);
            }
            return Optional.empty();
        }
        try {
            int month = Integer.parseInt(s.substring(0, 2));
            int year = Integer.parseInt(s.substring(2, 6));
            if (month < 1 || month > 12) {
                return Optional.empty();
            }
            return Optional.of(String.format(Locale.ROOT, "%04d-%02d", year, month));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<YearMonth> parseToYearMonth(String period) {
        if (period == null || period.isBlank()) {
            return Optional.empty();
        }
        String yyyyMm = period.contains("-") ? period.trim()
                : parseMmyyyyToYyyyMm(period).orElse(null);
        if (yyyyMm == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.parse(yyyyMm));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Indian FY label for a calendar month, e.g. 2025-04 → "2025-26", 2026-03 → "2025-26". */
    public static String financialYearLabel(YearMonth ym) {
        int startYear = ym.getMonthValue() >= 4 ? ym.getYear() : ym.getYear() - 1;
        int endYearShort = (startYear + 1) % 100;
        return String.format(Locale.ROOT, "%d-%02d", startYear, endYearShort);
    }

    public static String financialYearLabel(String yyyyMm) {
        return parseToYearMonth(yyyyMm).map(GstPeriodUtils::financialYearLabel).orElse("UNKNOWN");
    }

    /** FY start YearMonth (April of FY start year). */
    public static YearMonth fyStart(YearMonth anyInFy) {
        int startYear = anyInFy.getMonthValue() >= 4 ? anyInFy.getYear() : anyInFy.getYear() - 1;
        return YearMonth.of(startYear, 4);
    }

    /** FY end YearMonth (March of FY end year). */
    public static YearMonth fyEnd(YearMonth anyInFy) {
        return fyStart(anyInFy).plusMonths(11);
    }

    /** Latest completed Indian FY as of {@code asOf} (FY ending March before asOf if asOf is Apr+). */
    public static YearMonth latestCompletedFyEnd(LocalDate asOf) {
        YearMonth ym = YearMonth.from(asOf);
        if (ym.getMonthValue() >= 4) {
            // current FY started this Apr; latest completed ends previous March
            return YearMonth.of(ym.getYear(), 3);
        }
        // Jan–Mar: still in FY that started previous Apr; latest completed is FY before that
        return YearMonth.of(ym.getYear() - 1, 3);
    }

    public static YearMonth latestCompletedFyStart(LocalDate asOf) {
        YearMonth end = latestCompletedFyEnd(asOf);
        return YearMonth.of(end.getYear() - 1, 4);
    }

    /**
     * Trailing N calendar months ending at {@code endInclusive} (inclusive).
     * Returns YYYY-MM strings newest-last order (chronological ascending).
     */
    public static List<String> trailingMonths(YearMonth endInclusive, int n) {
        List<String> out = new ArrayList<>();
        if (n <= 0 || endInclusive == null) {
            return out;
        }
        YearMonth start = endInclusive.minusMonths(n - 1L);
        for (YearMonth cur = start; !cur.isAfter(endInclusive); cur = cur.plusMonths(1)) {
            out.add(cur.toString());
        }
        return out;
    }

    /**
     * Current FY YTD periods from April of current FY through {@code endInclusive}
     * (clamped to FY). Ascending YYYY-MM.
     */
    public static List<String> currentFyYtdPeriods(YearMonth endInclusive) {
        YearMonth start = fyStart(endInclusive);
        YearMonth end = endInclusive.isBefore(start) ? start : endInclusive;
        if (end.isAfter(fyEnd(endInclusive))) {
            end = fyEnd(endInclusive);
        }
        List<String> out = new ArrayList<>();
        for (YearMonth cur = start; !cur.isAfter(end); cur = cur.plusMonths(1)) {
            out.add(cur.toString());
        }
        return out;
    }

    /** Latest completed calendar month as of date (previous month if day is mid-month). */
    public static YearMonth latestCompletedMonth(LocalDate asOf) {
        return YearMonth.from(asOf).minusMonths(1);
    }

    /**
     * Expected latest return period given filing lag days after month-end.
     * Until lag elapses after month-end, expected latest is the month before.
     */
    public static YearMonth expectedLatestCompletedReturnPeriod(LocalDate asOf, int filingLagDays) {
        YearMonth current = YearMonth.from(asOf);
        YearMonth candidate = current.minusMonths(1);
        LocalDate dueReady = candidate.atEndOfMonth().plusDays(Math.max(0, filingLagDays));
        if (asOf.isBefore(dueReady)) {
            return candidate.minusMonths(1);
        }
        return candidate;
    }

    public static List<String> quarterPeriods(YearMonth anyInQuarter) {
        int m = anyInQuarter.getMonthValue();
        int qStart = ((m - 1) / 3) * 3 + 1;
        YearMonth start = YearMonth.of(anyInQuarter.getYear(), qStart);
        return List.of(start.toString(), start.plusMonths(1).toString(), start.plusMonths(2).toString());
    }

    public static int compareYyyyMm(String a, String b) {
        return a.compareTo(b);
    }
}
