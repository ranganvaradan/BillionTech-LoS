package com.los.core.creditintelligence.tax.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indian FY ↔ AY helpers. FY2024-25 (Apr 2024–Mar 2025) maps to AY2025-26.
 */
public final class TaxYearUtils {

    private static final Pattern YEAR_PAIR = Pattern.compile(
            "^(?:FY|AY)?\\s*(\\d{4})\\s*[-–/]\\s*(\\d{2}|\\d{4})$", Pattern.CASE_INSENSITIVE);

    private TaxYearUtils() {
    }

    public static Optional<String> normalizeYearLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher m = YEAR_PAIR.matcher(raw.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        int start = Integer.parseInt(m.group(1));
        String endRaw = m.group(2);
        int end = endRaw.length() == 2
                ? (start / 100) * 100 + Integer.parseInt(endRaw)
                : Integer.parseInt(endRaw);
        if (end != start + 1 && !(endRaw.length() == 2 && end % 100 == (start + 1) % 100)) {
            // allow 2024-25 where end is 2025
            if (endRaw.length() == 2) {
                end = start + 1;
            } else if (end != start + 1) {
                return Optional.empty();
            }
        }
        if (endRaw.length() == 2) {
            end = start + 1;
        }
        return Optional.of(String.format("%d-%02d", start, end % 100));
    }

    public static Optional<String> fyToAy(String financialYear) {
        return normalizeYearLabel(stripPrefix(financialYear)).map(fy -> {
            int start = Integer.parseInt(fy.substring(0, 4));
            return String.format("%d-%02d", start + 1, (start + 2) % 100);
        });
    }

    public static Optional<String> ayToFy(String assessmentYear) {
        return normalizeYearLabel(stripPrefix(assessmentYear)).map(ay -> {
            int start = Integer.parseInt(ay.substring(0, 4));
            return String.format("%d-%02d", start - 1, start % 100);
        });
    }

    public static boolean isValidFyAyPair(String financialYear, String assessmentYear) {
        Optional<String> expectedAy = fyToAy(financialYear);
        Optional<String> ay = normalizeYearLabel(stripPrefix(assessmentYear));
        return expectedAy.isPresent() && ay.isPresent() && expectedAy.get().equals(ay.get());
    }

    /**
     * Returns trailing FY labels ending at {@code latestFy}, inclusive, newest first.
     */
    public static List<String> trailingFinancialYears(String latestFy, int count) {
        Optional<String> norm = normalizeYearLabel(stripPrefix(latestFy));
        if (norm.isEmpty() || count <= 0) {
            return List.of();
        }
        int start = Integer.parseInt(norm.get().substring(0, 4));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int s = start - i;
            out.add(String.format("%d-%02d", s, (s + 1) % 100));
        }
        return out;
    }

    public static List<String> trailingAssessmentYears(String latestAy, int count) {
        Optional<String> norm = normalizeYearLabel(stripPrefix(latestAy));
        if (norm.isEmpty() || count <= 0) {
            return List.of();
        }
        int start = Integer.parseInt(norm.get().substring(0, 4));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int s = start - i;
            out.add(String.format("%d-%02d", s, (s + 1) % 100));
        }
        return out;
    }

    public static int compareYearLabels(String a, String b) {
        Optional<String> na = normalizeYearLabel(stripPrefix(a));
        Optional<String> nb = normalizeYearLabel(stripPrefix(b));
        if (na.isEmpty() && nb.isEmpty()) {
            return 0;
        }
        if (na.isEmpty()) {
            return -1;
        }
        if (nb.isEmpty()) {
            return 1;
        }
        return Comparator.<String>naturalOrder().compare(na.get(), nb.get());
    }

    private static String stripPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.length() >= 2 && (t.regionMatches(true, 0, "FY", 0, 2)
                || t.regionMatches(true, 0, "AY", 0, 2))) {
            return t.substring(2).trim();
        }
        return t;
    }
}
