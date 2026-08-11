package com.los.core.creditintelligence.policystudio.parameters;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Token-safe matching for system rule ids.
 * <p>
 * Important: {@code "MONTHLY_CREDITS".contains("EDI")} is true (substring of CREDITS).
 * Always use these helpers instead of bare {@code contains("EDI")}.
 */
public final class SystemRuleIdTokens {

    /** Whole-token EDI (e.g. {@code ..._GTE_EDI}, {@code ..._EDI_IF_...}), not CREDITS. */
    private static final Pattern EDI_TOKEN = Pattern.compile("(^|[^A-Z0-9])EDI([^A-Z0-9]|$)");
    private static final Pattern ADB_TOKEN = Pattern.compile("(^|[^A-Z0-9])ADB([^A-Z0-9]|$)");

    private SystemRuleIdTokens() {}

    public static String upper(String systemRuleId) {
        return systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
    }

    /** True when the id has a Proposed-EDI / capacity-vs-EDI token, not MONTHLY_CREDITS. */
    public static boolean hasProposedEdiToken(String systemRuleId) {
        String u = upper(systemRuleId);
        if (u.isEmpty()) return false;
        if (u.contains("PROPOSED_EDI")) return true;
        return EDI_TOKEN.matcher(u).find();
    }

    public static boolean hasAdbToken(String systemRuleId) {
        String u = upper(systemRuleId);
        return !u.isEmpty() && ADB_TOKEN.matcher(u).find();
    }

    public static boolean isBankingCapacityVsEdi(String systemRuleId) {
        return hasProposedEdiToken(systemRuleId)
                && (hasAdbToken(systemRuleId)
                || upper(systemRuleId).contains("SETTLEMENT")
                || upper(systemRuleId).contains("BANK"));
    }
}
