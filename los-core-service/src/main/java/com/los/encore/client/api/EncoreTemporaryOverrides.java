package com.los.encore.client.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temporary Encore LMS stabilization overrides.
 */
public final class EncoreTemporaryOverrides {

    /**
     * Historical staging seed / fixture constant only.
     * <p>
     * LMS-PRODUCT-MAPPING-P0: must NOT be used as a runtime openLoanAccount fallback.
     * Live product codes come from workflow/application/program configuration.
     */
    @Deprecated(since = "LMS-PRODUCT-MAPPING-P0", forRemoval = false)
    public static final String DEFAULT_ENCORE_PRODUCT_CODE = "IPPOPAYM01";

    // TEMP FIX:
    // Using hardcoded Encore location codes until final LMS location master mapping is completed.
    // TODO: Restore dynamic location mapping after Encore location master is finalized.
    public static final String DEFAULT_ENCORE_LOCATION_CODE = "1";

    private static final String CUSTOMER_ID_PREFIX = "BL";
    private static final int MAX_ENCORE_CUSTOMER_ID_LENGTH = 15;
    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d+)(?:\\D*)$");

    /**
     * Builds a compact customer ID suitable for Encore's {@code customerId1} column
     * (max 15 chars). Extracts the trailing numeric sequence from the LOS application
     * number and prefixes it with "BL".
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "LOS-IND-20260514-91450"}  &rarr; {@code "BL91450"}</li>
     *   <li>{@code "LOS-IND-20260514-123456"} &rarr; {@code "BL123456"}</li>
     *   <li>{@code null}                       &rarr; {@code "BL0"} (fallback)</li>
     * </ul>
     *
     * @param applicationNumber the full LOS application number (e.g. {@code LOS-IND-20260514-91450})
     * @return a compact ID &le; 15 characters, never null
     */
    public static String buildEncoreCustomerId(String applicationNumber) {
        if (applicationNumber == null || applicationNumber.isBlank()) {
            return CUSTOMER_ID_PREFIX + "0";
        }
        Matcher m = TRAILING_DIGITS.matcher(applicationNumber);
        if (m.find()) {
            String numericPart = m.group(1);
            String candidate = CUSTOMER_ID_PREFIX + numericPart;
            if (candidate.length() <= MAX_ENCORE_CUSTOMER_ID_LENGTH) {
                return candidate;
            }
            return candidate.substring(candidate.length() - MAX_ENCORE_CUSTOMER_ID_LENGTH);
        }
        String stripped = applicationNumber.replaceAll("[^A-Za-z0-9]", "");
        if (stripped.length() > MAX_ENCORE_CUSTOMER_ID_LENGTH) {
            stripped = stripped.substring(stripped.length() - MAX_ENCORE_CUSTOMER_ID_LENGTH);
        }
        return stripped.isEmpty() ? CUSTOMER_ID_PREFIX + "0" : stripped;
    }

    private EncoreTemporaryOverrides() {
    }
}
