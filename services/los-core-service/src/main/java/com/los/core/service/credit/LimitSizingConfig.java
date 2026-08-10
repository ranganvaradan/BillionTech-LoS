package com.los.core.service.credit;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed from {@code rulesJson.limitSizing} on an underwriting rule set.
 * Drives computed scorecard keys used by hard rules — not hardcoded product logic.
 */
public record LimitSizingConfig(
        boolean enabled,
        String turnoverParameter,
        BigDecimal turnoverLimitPercent,
        BigDecimal standardTicketCap,
        BigDecimal maxDeviationCap,
        StandardCapMode standardCapMode,
        MaxDeviationMode maxDeviationMode,
        BigDecimal maxDeviationPercent,
        Object dependsOn,
        boolean sanctionCapEnabled,
        boolean camRecommendedCapEnabled) {

    public static final String DEFAULT_TURNOVER_PARAMETER = "ANNUAL_GST_TURNOVER";

    public enum StandardCapMode {
        MIN_OF_BOTH,
        TURNOVER_PERCENT,
        FIXED
    }

    public enum MaxDeviationMode {
        FIXED,
        TURNOVER_PERCENT
    }

    /**
     * Accepts a single object ({@code limitSizing}) or a list of objects (first enabled/valid wins).
     * Legacy single-object configs continue to work unchanged.
     */
    public static LimitSizingConfig fromRulesJson(Map<String, Object> rulesJson) {
        if (rulesJson == null || rulesJson.isEmpty()) {
            return null;
        }
        Object raw = rulesJson.get("limitSizing");
        if (raw instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    LimitSizingConfig cfg = fromMap(m);
                    if (cfg != null) {
                        return cfg;
                    }
                }
            }
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            return fromMap(m);
        }
        return null;
    }

    static LimitSizingConfig fromMap(Map<?, ?> m) {
        boolean enabled = bool(m.get("enabled"), false);
        if (!enabled) {
            return null;
        }
        String turnoverParam = str(m.get("turnoverParameter"));
        if (turnoverParam == null || turnoverParam.isBlank()) {
            turnoverParam = DEFAULT_TURNOVER_PARAMETER;
        }
        BigDecimal pct = toBd(m.get("turnoverLimitPercent"));
        BigDecimal standardCap = toBd(m.get("standardTicketCap"));
        BigDecimal maxCap = toBd(m.get("maxDeviationCap"));
        StandardCapMode standardMode = enumValue(
                m.get("standardCapMode"), StandardCapMode.class, StandardCapMode.MIN_OF_BOTH);
        MaxDeviationMode deviationMode = enumValue(
                m.get("maxDeviationMode"), MaxDeviationMode.class, MaxDeviationMode.FIXED);
        BigDecimal maxDeviationPercent = toBd(m.get("maxDeviationPercent"));
        if (standardMode == null || deviationMode == null
                || !validStandardMode(standardMode, pct, standardCap)
                || !validDeviationMode(deviationMode, maxCap, maxDeviationPercent)) {
            return null;
        }
        return new LimitSizingConfig(
                true,
                turnoverParam.trim().toUpperCase(Locale.ROOT),
                pct,
                standardCap,
                maxCap,
                standardMode,
                deviationMode,
                maxDeviationPercent,
                m.get("dependsOn"),
                bool(m.get("sanctionCapEnabled"), false),
                bool(m.get("camRecommendedCapEnabled"), false));
    }

    private static boolean validStandardMode(
            StandardCapMode mode, BigDecimal turnoverPercent, BigDecimal standardCap) {
        return switch (mode) {
            case MIN_OF_BOTH -> turnoverPercent != null && standardCap != null;
            case TURNOVER_PERCENT -> turnoverPercent != null;
            case FIXED -> standardCap != null;
        };
    }

    private static boolean validDeviationMode(
            MaxDeviationMode mode, BigDecimal fixedCap, BigDecimal turnoverPercent) {
        return switch (mode) {
            case FIXED -> fixedCap != null;
            case TURNOVER_PERCENT -> turnoverPercent != null;
        };
    }

    private static <E extends Enum<E>> E enumValue(Object raw, Class<E> type, E defaultValue) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o != null) {
            return "true".equalsIgnoreCase(String.valueOf(o).trim());
        }
        return def;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static BigDecimal toBd(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
