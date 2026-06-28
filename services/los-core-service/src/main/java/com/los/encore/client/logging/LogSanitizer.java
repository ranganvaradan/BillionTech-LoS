package com.los.encore.client.logging;

/**
 * Masks obvious secret patterns in log payloads.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    public static String maskForLog(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        String t = text;
        t = t.replaceAll("(?i)(\"password\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
        t = t.replaceAll("(?i)(\"apiPassword\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
        t = t.replaceAll("(?i)(\"apiUsername\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
        t = t.replaceAll("(?i)(Authorization\\s*:\\s*Basic\\s+)[^\\s]+", "$1***");
        if (t.length() > maxLen) {
            t = t.substring(0, maxLen) + "...(truncated,len=" + text.length() + ")";
        }
        return t;
    }
}
