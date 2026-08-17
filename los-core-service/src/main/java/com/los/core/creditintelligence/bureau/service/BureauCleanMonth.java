package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.PaymentHistoryMonthInput;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Package-visible CLEAN_MONTH predicate for Equifax derived metrics.
 *
 * <p>A month is CLEAN iff an observation exists, numeric {@code dpd == 0}, and monthly
 * payment/asset status is not adverse. {@code *} / blank / unknown codes are NOT CLEAN.
 * {@code STD} or {@code 000} alone is not CLEAN without numeric {@code dpd == 0}.
 */
final class BureauCleanMonth {

    private BureauCleanMonth() {
    }

    static boolean isCleanMonth(CiBureauPaymentHistory row) {
        if (row == null || row.getMonth() == null) {
            return false;
        }
        return isCleanMonth(row.getDpd(), row.getProviderRawStatus(), row.getAssetClassificationStatus());
    }

    static boolean isCleanMonth(PaymentHistoryMonthInput row) {
        if (row == null || row.period() == null) {
            return false;
        }
        return isCleanMonth(row.dpd(), row.providerRawStatus(), row.assetClassificationStatus());
    }

    /**
     * CLEAN iff observation exists (caller), {@code dpd != null && dpd == 0}, and status is
     * not adverse / star / blank-without-known-good / unknown.
     */
    static boolean isCleanMonth(Integer dpd, String providerRawStatus, String assetClassificationStatus) {
        if (dpd == null || dpd != 0) {
            return false;
        }
        String raw = normalize(providerRawStatus);
        String acs = normalize(assetClassificationStatus);
        boolean rawPresent = raw != null;
        boolean acsPresent = acs != null;
        if (!rawPresent && !acsPresent) {
            return false;
        }
        if (isStar(raw) || isStar(acs)) {
            return false;
        }
        if (isAdverseMonthly(raw) || isAdverseMonthly(acs)) {
            return false;
        }
        if (rawPresent && !isKnownNonAdverse(raw)) {
            return false;
        }
        if (acsPresent && !isKnownNonAdverse(acs)) {
            return false;
        }
        return true;
    }

    /**
     * Adverse monthly: {@code SPM}, or any status containing DBT/LOSS/LSS/PWOS/WILFUL/SUIT
     * (case insensitive). {@code *} is not treated as a delinquency-event code here.
     */
    static boolean isAdverseMonthly(String status) {
        String u = normalize(status);
        if (u == null) {
            return false;
        }
        if ("SPM".equals(u)) {
            return true;
        }
        return u.contains("DBT")
                || u.contains("LOSS")
                || u.contains("LSS")
                || u.contains("PWOS")
                || u.contains("WILFUL")
                || u.contains("SUIT");
    }

    static boolean isAffirmativeSuit(String status) {
        String u = normalize(status);
        if (u == null || "*".equals(u)) {
            return false;
        }
        if ("NO".equals(u) || "N".equals(u) || "FALSE".equals(u) || "0".equals(u)) {
            return false;
        }
        return "YES".equals(u)
                || "Y".equals(u)
                || "TRUE".equals(u)
                || "1".equals(u)
                || "SUIT".equals(u)
                || u.startsWith("SUIT")
                || u.contains("SUIT FILED")
                || u.contains("SUITFILED");
    }

    /**
     * Consecutive CLEAN calendar months starting at the first reported month on/after
     * {@code openYm} (or {@code openYm} when that month is reported).
     */
    static int consecutiveCleanFromFirstReported(Collection<PaymentHistoryMonthInput> rows, YearMonth openYm) {
        NavigableMap<YearMonth, PaymentHistoryMonthInput> byMonth = indexByPeriod(rows);
        if (byMonth.isEmpty()) {
            return 0;
        }
        YearMonth first = null;
        for (YearMonth ym : byMonth.keySet()) {
            if (openYm == null || !ym.isBefore(openYm)) {
                first = ym;
                break;
            }
        }
        if (first == null) {
            return 0;
        }
        int n = 0;
        YearMonth cursor = first;
        while (true) {
            PaymentHistoryMonthInput row = byMonth.get(cursor);
            if (row == null || !isCleanMonth(row)) {
                break;
            }
            n++;
            cursor = cursor.plusMonths(1);
        }
        return n;
    }

    /** Longest consecutive CLEAN calendar-month streak on/after {@code openYm} (inclusive). */
    static int maxConsecutiveCleanStreak(Collection<PaymentHistoryMonthInput> rows, YearMonth openYm) {
        NavigableMap<YearMonth, PaymentHistoryMonthInput> byMonth = indexByPeriod(rows);
        int max = 0;
        int cur = 0;
        YearMonth prevClean = null;
        for (var e : byMonth.entrySet()) {
            YearMonth ym = e.getKey();
            if (openYm != null && ym.isBefore(openYm)) {
                continue;
            }
            if (!isCleanMonth(e.getValue())) {
                cur = 0;
                prevClean = null;
                continue;
            }
            if (prevClean != null && prevClean.plusMonths(1).equals(ym)) {
                cur++;
            } else {
                cur = 1;
            }
            prevClean = ym;
            if (cur > max) {
                max = cur;
            }
        }
        return max;
    }

    static NavigableMap<YearMonth, PaymentHistoryMonthInput> indexByPeriod(
            Collection<PaymentHistoryMonthInput> rows) {
        NavigableMap<YearMonth, PaymentHistoryMonthInput> byMonth = new TreeMap<>();
        if (rows == null) {
            return byMonth;
        }
        for (PaymentHistoryMonthInput row : rows) {
            if (row == null || row.period() == null) {
                continue;
            }
            byMonth.putIfAbsent(row.period(), row);
        }
        return byMonth;
    }

    static List<PaymentHistoryMonthInput> safe(List<PaymentHistoryMonthInput> rows) {
        return rows != null ? rows : List.of();
    }

    private static boolean isStar(String normalized) {
        return "*".equals(normalized);
    }

    private static boolean isKnownNonAdverse(String normalized) {
        return "STD".equals(normalized)
                || "STANDARD".equals(normalized)
                || "000".equals(normalized)
                || "00".equals(normalized)
                || "0".equals(normalized)
                || "CURRENT".equals(normalized);
    }

    private static String normalize(String status) {
        if (status == null) {
            return null;
        }
        String t = status.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.toUpperCase(Locale.ROOT);
    }
}
