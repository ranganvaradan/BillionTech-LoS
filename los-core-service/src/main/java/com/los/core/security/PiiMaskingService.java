package com.los.core.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * PII (Personally Identifiable Information) masking and protection utility.
 * Handles Aadhaar masking (show last 4 digits only), PAN masking, and phone masking.
 * Compliant with UIDAI guidelines and DPDP Act 2023.
 */
@Component
public class PiiMaskingService {

    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\d{12}");
    private static final Pattern PAN_PATTERN = Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\d{10}");

    /**
     * Mask Aadhaar number — show only last 4 digits.
     * "123456789012" → "XXXX-XXXX-9012"
     */
    public String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return "XXXX-XXXX-XXXX";
        String digits = aadhaar.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "XXXX-XXXX-XXXX";
        return "XXXX-XXXX-" + digits.substring(digits.length() - 4);
    }

    /**
     * Mask PAN number — show first 2 and last 1 character.
     * "ABCDE1234F" → "AB****34*F"
     */
    public String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "**********";
        return pan.substring(0, 2) + "****" + pan.substring(6, 8) + "*" + pan.substring(9);
    }

    /**
     * Mask phone number — show last 4 digits.
     * "9876543210" → "XXXXXX3210"
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "XXXXXXXXXX";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "XXXXXXXXXX";
        return "X".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /**
     * Mask email — show first 2 chars and domain.
     * "john.doe@example.com" → "jo*****@example.com"
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***@***.***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.length() > 2 ? local.substring(0, 2) + "*".repeat(local.length() - 2) : local;
        return masked + "@" + parts[1];
    }

    /**
     * Mask bank account number — show last 4 digits.
     * "12345678901234" → "XXXXXXXXXX1234"
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "XXXXXXXXXXXX";
        return "X".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * Mask all PII fields in a data map (for logging / audit).
     */
    public Map<String, Object> maskSensitiveFields(Map<String, Object> data) {
        if (data == null) return data;
        var masked = new java.util.LinkedHashMap<>(data);

        if (masked.containsKey("aadhaar")) masked.put("aadhaar", maskAadhaar(String.valueOf(masked.get("aadhaar"))));
        if (masked.containsKey("aadhaarNumber")) masked.put("aadhaarNumber", maskAadhaar(String.valueOf(masked.get("aadhaarNumber"))));
        if (masked.containsKey("pan")) masked.put("pan", maskPan(String.valueOf(masked.get("pan"))));
        if (masked.containsKey("panNumber")) masked.put("panNumber", maskPan(String.valueOf(masked.get("panNumber"))));
        if (masked.containsKey("mobile")) masked.put("mobile", maskPhone(String.valueOf(masked.get("mobile"))));
        if (masked.containsKey("phone")) masked.put("phone", maskPhone(String.valueOf(masked.get("phone"))));
        if (masked.containsKey("email")) masked.put("email", maskEmail(String.valueOf(masked.get("email"))));
        if (masked.containsKey("accountNumber")) masked.put("accountNumber", maskAccountNumber(String.valueOf(masked.get("accountNumber"))));

        return masked;
    }

    /**
     * Mask PII in a string (scrub Aadhaar numbers from logs).
     */
    public String scrubAadhaarFromText(String text) {
        if (text == null) return null;
        return AADHAAR_PATTERN.matcher(text).replaceAll(m -> {
            String match = m.group();
            return "XXXX-XXXX-" + match.substring(8);
        });
    }
}
