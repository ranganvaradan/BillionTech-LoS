package com.los.core.service.master;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical India state identity for workflow {@code allowedStates} matching.
 *
 * <p>Workflow admin stores geo-master display names (e.g. {@code Tamil Nadu}).
 * Intake APIs may persist ISO-style codes ({@code TN}) or geo-master codes
 * ({@code TAMIL_NADU}). All three must match the same identity.
 */
public final class IndiaStateIdentity {

    /**
     * ISO 3166-2:IN two-letter codes (and common two-letter aliases) → alphanumeric token.
     */
    private static final Map<String, String> SHORT_CODE_TO_TOKEN = Map.ofEntries(
            Map.entry("AN", "ANDAMANANDNICOBARISLANDS"),
            Map.entry("AP", "ANDHRAPRADESH"),
            Map.entry("AR", "ARUNACHALPRADESH"),
            Map.entry("AS", "ASSAM"),
            Map.entry("BR", "BIHAR"),
            Map.entry("CH", "CHANDIGARH"),
            Map.entry("CT", "CHHATTISGARH"),
            Map.entry("CG", "CHHATTISGARH"),
            Map.entry("DN", "DADRAANDNAGARHAVELI"),
            Map.entry("DD", "DAMANANDDIU"),
            Map.entry("DL", "DELHI"),
            Map.entry("GA", "GOA"),
            Map.entry("GJ", "GUJARAT"),
            Map.entry("HR", "HARYANA"),
            Map.entry("HP", "HIMACHALPRADESH"),
            Map.entry("JK", "JAMMUANDKASHMIR"),
            Map.entry("JH", "JHARKHAND"),
            Map.entry("KA", "KARNATAKA"),
            Map.entry("KL", "KERALA"),
            Map.entry("LA", "LADAKH"),
            Map.entry("LD", "LAKSHADWEEP"),
            Map.entry("MP", "MADHYAPRADESH"),
            Map.entry("MH", "MAHARASHTRA"),
            Map.entry("MN", "MANIPUR"),
            Map.entry("ML", "MEGHALAYA"),
            Map.entry("MZ", "MIZORAM"),
            Map.entry("NL", "NAGALAND"),
            Map.entry("OR", "ODISHA"),
            Map.entry("OD", "ODISHA"),
            Map.entry("PY", "PUDUCHERRY"),
            Map.entry("PB", "PUNJAB"),
            Map.entry("RJ", "RAJASTHAN"),
            Map.entry("SK", "SIKKIM"),
            Map.entry("TN", "TAMILNADU"),
            Map.entry("TG", "TELANGANA"),
            Map.entry("TS", "TELANGANA"),
            Map.entry("TR", "TRIPURA"),
            Map.entry("UP", "UTTARPRADESH"),
            Map.entry("UT", "UTTARAKHAND"),
            Map.entry("UK", "UTTARAKHAND"),
            Map.entry("WB", "WESTBENGAL")
    );

    private IndiaStateIdentity() {}

    public static boolean matchesAllowed(String candidate, Collection<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String identity = identity(candidate);
        if (identity.isEmpty()) {
            return true;
        }
        for (String allowedValue : allowed) {
            if (identity.equals(identity(allowedValue))) {
                return true;
            }
        }
        return false;
    }

    public static String identity(String raw) {
        String token = alphanumeric(raw);
        if (token.isEmpty()) {
            return "";
        }
        String fromShort = SHORT_CODE_TO_TOKEN.get(token);
        if (fromShort != null) {
            return fromShort;
        }
        return token;
    }

    private static String alphanumeric(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
