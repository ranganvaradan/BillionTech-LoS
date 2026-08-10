package com.los.core.creditintelligence.tax.util;

import com.los.core.creditintelligence.tax.domain.ItrForm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps provider form strings such as "ITR-6", "ITR6", "itr 4" → {@link ItrForm}.
 */
public final class ItrFormNormalizer {

    private static final Pattern ITR_NUM = Pattern.compile("ITR\\s*[-_]?\\s*([1-7])", Pattern.CASE_INSENSITIVE);

    private ItrFormNormalizer() {
    }

    public static ItrForm normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return ItrForm.UNKNOWN;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        Matcher m = ITR_NUM.matcher(t);
        if (m.find()) {
            return switch (m.group(1)) {
                case "1" -> ItrForm.ITR_1;
                case "2" -> ItrForm.ITR_2;
                case "3" -> ItrForm.ITR_3;
                case "4" -> ItrForm.ITR_4;
                case "5" -> ItrForm.ITR_5;
                case "6" -> ItrForm.ITR_6;
                case "7" -> ItrForm.ITR_7;
                default -> ItrForm.OTHER;
            };
        }
        try {
            return ItrForm.valueOf(t.replace('-', '_').replace(' ', '_'));
        } catch (Exception e) {
            return ItrForm.OTHER;
        }
    }

    public static boolean isPresumptiveForm(ItrForm form) {
        return form == ItrForm.ITR_4;
    }

    public static boolean looksPresumptive(String rawFormOrSection) {
        if (rawFormOrSection == null) {
            return false;
        }
        String u = rawFormOrSection.toUpperCase(Locale.ROOT);
        return u.contains("ITR-4") || u.contains("ITR4") || u.contains("ITR_4")
                || u.contains("44AD") || u.contains("44ADA") || u.contains("44AE");
    }
}
