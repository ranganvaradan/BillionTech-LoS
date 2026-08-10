package com.los.core.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PayuHashUtil {

    /** Empty fields after udf2: udf3, udf4, udf5, and five trailing slots in PayU's formula. */
    private static final int FORWARD_HASH_EMPTY_SLOTS_AFTER_UDF2 = 8;

    private PayuHashUtil() {}

    public static String forwardHash(
            String key,
            String txnid,
            String amount,
            String productinfo,
            String firstname,
            String email,
            String udf1,
            String udf2,
            String salt) {
        List<String> parts = new ArrayList<>();
        parts.add(nullSafe(key));
        parts.add(nullSafe(txnid));
        parts.add(nullSafe(amount));
        parts.add(nullSafe(productinfo));
        parts.add(nullSafe(firstname));
        parts.add(nullSafe(email));
        parts.add(nullSafe(udf1));
        parts.add(nullSafe(udf2));
        for (int i = 0; i < FORWARD_HASH_EMPTY_SLOTS_AFTER_UDF2; i++) {
            parts.add("");
        }
        parts.add(nullSafe(salt));
        return sha512(String.join("|", parts));
    }

    public static String commandHash(String key, String command, String var1, String salt) {
        return sha512(String.join("|", nullSafe(key), nullSafe(command), nullSafe(var1), nullSafe(salt)));
    }

    public static String reverseHashFromCallback(Map<String, String> params, String salt, String merchantKey) {
        Map<String, String> normalized = normalizeParams(params);
        String key = firstNonBlank(normalized, "key");
        if (key == null) {
            key = merchantKey;
        }
        String additional = firstNonBlank(normalized, "additionalcharges", "additional_charges");
        List<String> parts = new ArrayList<>();
        if (additional != null) {
            parts.add(additional);
        }
        parts.add(nullSafe(salt));
        parts.add(nullSafe(normalized.get("status")));
        for (int i = 0; i < 5; i++) {
            parts.add("");
        }
        parts.add(nullSafe(normalized.get("udf5")));
        parts.add(nullSafe(normalized.get("udf4")));
        parts.add(nullSafe(normalized.get("udf3")));
        parts.add(nullSafe(normalized.get("udf2")));
        parts.add(nullSafe(normalized.get("udf1")));
        parts.add(nullSafe(normalized.get("email")));
        parts.add(nullSafe(normalized.get("firstname")));
        parts.add(nullSafe(normalized.get("productinfo")));
        parts.add(nullSafe(normalized.get("amount")));
        parts.add(nullSafe(normalized.get("txnid")));
        parts.add(nullSafe(key));
        return sha512(String.join("|", parts));
    }

    public static boolean reverseHashMatches(Map<String, String> params, String salt, String merchantKey, String received) {
        if (received == null || received.isBlank()) {
            return false;
        }
        if (received.equalsIgnoreCase(reverseHashFromCallback(params, salt, merchantKey))) {
            return true;
        }
        Map<String, String> normalized = normalizeParams(params);
        if (normalized.containsKey("splitinfo")) {
            if (received.equalsIgnoreCase(reverseHashWithSplitInfo(normalized, salt, merchantKey))) {
                return true;
            }
        }
        return received.equalsIgnoreCase(reverseHashLegacyNoUdf(params, salt, merchantKey));
    }

    private static String reverseHashWithSplitInfo(Map<String, String> normalized, String salt, String merchantKey) {
        String key = firstNonBlank(normalized, "key");
        if (key == null) {
            key = merchantKey;
        }
        String additional = firstNonBlank(normalized, "additionalcharges", "additional_charges");
        List<String> parts = new ArrayList<>();
        if (additional != null) {
            parts.add(additional);
        }
        parts.add(nullSafe(salt));
        parts.add(nullSafe(normalized.get("status")));
        parts.add(nullSafe(normalized.get("splitinfo")));
        for (int i = 0; i < 5; i++) {
            parts.add("");
        }
        parts.add(nullSafe(normalized.get("udf5")));
        parts.add(nullSafe(normalized.get("udf4")));
        parts.add(nullSafe(normalized.get("udf3")));
        parts.add(nullSafe(normalized.get("udf2")));
        parts.add(nullSafe(normalized.get("udf1")));
        parts.add(nullSafe(normalized.get("email")));
        parts.add(nullSafe(normalized.get("firstname")));
        parts.add(nullSafe(normalized.get("productinfo")));
        parts.add(nullSafe(normalized.get("amount")));
        parts.add(nullSafe(normalized.get("txnid")));
        parts.add(nullSafe(key));
        return sha512(String.join("|", parts));
    }

    public static String reverseHashLegacyNoUdf(Map<String, String> params, String salt, String merchantKey) {
        Map<String, String> normalized = normalizeParams(params);
        String key = firstNonBlank(normalized, "key");
        if (key == null) {
            key = merchantKey;
        }
        List<String> parts = new ArrayList<>();
        parts.add(nullSafe(salt));
        parts.add(nullSafe(normalized.get("status")));
        for (int i = 0; i < 9; i++) {
            parts.add("");
        }
        parts.add(nullSafe(normalized.get("email")));
        parts.add(nullSafe(normalized.get("firstname")));
        parts.add(nullSafe(normalized.get("productinfo")));
        parts.add(nullSafe(normalized.get("amount")));
        parts.add(nullSafe(normalized.get("txnid")));
        parts.add(nullSafe(key));
        return sha512(String.join("|", parts));
    }

    public static Map<String, String> normalizeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        params.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
        return normalized;
    }

    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String firstNonBlank(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private static String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }
}
