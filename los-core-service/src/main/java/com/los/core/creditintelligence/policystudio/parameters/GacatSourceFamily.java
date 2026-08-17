package com.los.core.creditintelligence.policystudio.parameters;

import java.util.Locale;
import java.util.Objects;

/**
 * Canonical GACAT {@code evaluatedFrom} / {@code source_family} labels.
 * Collapses capability-schema leaks (e.g. {@code BUREAU_RETAIL}) into the
 * catalogue family name used by Data &amp; Parameters source cards.
 */
public final class GacatSourceFamily {

    public static final String BUREAU_RETAIL = "Bureau Retail";
    public static final String BUREAU_COMMERCIAL = "Bureau Commercial";

    private GacatSourceFamily() {}

    public static String canonicalLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String folded = fold(trimmed);
        if ("bureau retail".equals(folded)) {
            return BUREAU_RETAIL;
        }
        if ("bureau commercial".equals(folded)) {
            return BUREAU_COMMERCIAL;
        }
        return trimmed;
    }

    public static boolean sameFamily(String a, String b) {
        String left = canonicalLabel(a);
        String right = canonicalLabel(b);
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    public static boolean isBureauRetail(String family) {
        return BUREAU_RETAIL.equals(canonicalLabel(family));
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static boolean equalsCanonical(String family, String expectedCanonical) {
        return Objects.equals(canonicalLabel(family), expectedCanonical);
    }
}
