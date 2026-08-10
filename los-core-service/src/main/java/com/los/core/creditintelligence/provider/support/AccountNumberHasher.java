package com.los.core.creditintelligence.provider.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountNumberHasher {

    private AccountNumberHasher() {
    }

    public static Map<String, String> hashAndLast4(String accountNumber) {
        Map<String, String> out = new LinkedHashMap<>();
        if (accountNumber == null || accountNumber.isBlank()) {
            out.put("accountNumberHash", null);
            out.put("accountNumberLast4", null);
            return out;
        }
        String trimmed = accountNumber.trim();
        out.put("accountNumberHash", sha256Hex(trimmed));
        out.put("accountNumberLast4", trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4));
        return out;
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
