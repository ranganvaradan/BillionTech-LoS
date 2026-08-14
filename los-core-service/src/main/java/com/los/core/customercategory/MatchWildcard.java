package com.los.core.customercategory;

/**
 * Wildcard token for enum/string match dimensions. Not used for amount bounds
 * (amount uses NULL = unbounded).
 */
public final class MatchWildcard {

    public static final String ANY = "ANY";

    private MatchWildcard() {}

    public static boolean isAny(String value) {
        return value != null && ANY.equalsIgnoreCase(value.trim());
    }

    public static String normalizeOrAny(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Match dimension value required (use ANY for wildcard)");
        }
        String t = value.trim();
        return isAny(t) ? ANY : t;
    }
}
