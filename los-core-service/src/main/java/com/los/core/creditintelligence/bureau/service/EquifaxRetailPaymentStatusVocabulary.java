package com.los.core.creditintelligence.bureau.service;

import java.util.Locale;
import java.util.Set;

/**
 * Equifax retail {@code History48Months/Month/PaymentStatus} vocabulary proven by saved
 * Equifax HTML legends (1265095.html, sample-bureau-report.html, 9398 SAMPLE EQUIFAX XML.html).
 * Exact tokens only — substring matching is forbidden for short codes such as {@code RC}.
 */
public final class EquifaxRetailPaymentStatusVocabulary {

    public static final String EVIDENCE =
            "Equifax report payment-history legend in saved samples "
                    + "docs/1265095.html, docs/sample-bureau-report.html, docs/9398 - SAMPLE EQUIFAX XML.html";

    /** DBT = Doubtful. */
    public static final Set<String> DBT = Set.of("DBT");

    /** PWOS = Post Written Off Settled / Post Write Off Settled. Not PWOC, not generic write-off. */
    public static final Set<String> PWOS = Set.of("PWOS");

    /**
     * Equifax provider code {@code LOSS} = Loss. Canonical business concept is LSS.
     * {@code WOF}/{@code SFWO}/{@code WDWO} are write-off family, not this count.
     */
    public static final Set<String> LOSS = Set.of("LOSS");

    /**
     * Restructuring family. {@code SFR} is Suit Filed-Restructured (also suit-filed).
     * Count each tradeline once even if several months carry codes.
     */
    public static final Set<String> RESTRUCTURED = Set.of("RES", "RGM", "RNC", "RCV", "RC", "SFR");

    public enum Family {
        DBT,
        PWOS,
        LOSS,
        RESTRUCTURED
    }

    private EquifaxRetailPaymentStatusVocabulary() {}

    public static Set<String> codes(Family family) {
        return switch (family) {
            case DBT -> DBT;
            case PWOS -> PWOS;
            case LOSS -> LOSS;
            case RESTRUCTURED -> RESTRUCTURED;
        };
    }

    public static String canonicalParameterId(Family family) {
        return switch (family) {
            case DBT -> "bureau.dbt_account_count";
            case PWOS -> "bureau.pwos_account_count";
            case LOSS -> "bureau.lss_account_count";
            case RESTRUCTURED -> "bureau.restructured_account_count";
        };
    }

    /**
     * Exact token. {@code *} / blank / all-stars are not affirmative.
     */
    public static String token(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty() || t.chars().allMatch(ch -> ch == '*')) {
            return null;
        }
        int space = t.indexOf(' ');
        if (space > 0) {
            t = t.substring(0, space);
        }
        return t;
    }

    public static boolean matches(String raw, Family family) {
        String t = token(raw);
        return t != null && codes(family).contains(t);
    }

    public static boolean isNonAffirmative(String raw) {
        return token(raw) == null;
    }
}
