package com.los.core.creditintelligence.aiunderwriter.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Masks PII before context is sent to any AI provider.
 */
@Component
public class PiiMinimizer {

    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern AADHAAR = Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b");
    private static final Pattern ACCOUNT = Pattern.compile("\\b\\d{9,18}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+91[\\s-]?)?[6-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final int NARRATION_MAX = 80;

    @SuppressWarnings("unchecked")
    public Map<String, Object> minimize(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }
        return (Map<String, Object>) minimizeValue(input);
    }

    public String maskText(String text) {
        if (text == null) {
            return null;
        }
        String s = text;
        s = PAN.matcher(s).replaceAll("XXXXX0000X");
        s = AADHAAR.matcher(s).replaceAll("XXXX XXXX XXXX");
        s = EMAIL.matcher(s).replaceAll("***@***");
        s = PHONE.matcher(s).replaceAll("XXXXXXXXXX");
        s = ACCOUNT.matcher(s).replaceAll("**********");
        return s;
    }

    public String truncateNarration(String narration) {
        if (narration == null) {
            return null;
        }
        String masked = maskText(narration);
        if (masked.length() <= NARRATION_MAX) {
            return masked;
        }
        return masked.substring(0, NARRATION_MAX) + "…";
    }

    private Object minimizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? "null" : String.valueOf(e.getKey());
                String lk = key.toLowerCase();
                if (lk.contains("pan") || lk.contains("aadhaar") || lk.contains("aadhar")
                        || lk.contains("account") || lk.contains("phone") || lk.contains("mobile")
                        || lk.contains("email") || lk.contains("address")) {
                    out.put(key, maskScalar(e.getValue()));
                } else if (lk.contains("narration") || lk.contains("remark")) {
                    out.put(key, truncateNarration(String.valueOf(e.getValue())));
                } else {
                    out.put(key, minimizeValue(e.getValue()));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(minimizeValue(item));
            }
            return out;
        }
        if (value instanceof String s) {
            return maskText(s);
        }
        return value;
    }

    private String maskScalar(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        if (s.isBlank()) {
            return s;
        }
        return maskText(s);
    }
}
