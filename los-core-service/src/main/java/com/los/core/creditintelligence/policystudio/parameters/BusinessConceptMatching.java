package com.los.core.creditintelligence.policystudio.parameters;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * POLICY-STUDIO-GATE2 — token-safe business-phrase matching.
 * <p>
 * Critical: {@code "credit".contains("edi")} is true. Never use bare substring
 * {@code contains("edi")} for Proposed EDI detection.
 */
public final class BusinessConceptMatching {

    /** Whole-word EDI (not the "edi" inside "credit" / "credits" / "medical"). */
    private static final Pattern EDI_WORD = Pattern.compile(
            "(?i)(?<![a-z0-9])edi(?![a-z0-9])");
    private static final Pattern PROPOSED_EDI = Pattern.compile(
            "(?i)\\b(proposed\\s+edi|equated\\s+daily\\s+instal(?:ment)?|eligible\\s+disposable)\\b");
    private static final Pattern WRITE_OFF = Pattern.compile(
            "(?i)\\b(write[\\s-]?offs?|written[\\s-]?offs?|written[\\s-]?off)\\b");
    private static final Pattern CREDIT_CARD = Pattern.compile(
            "(?i)\\b(credit\\s*cards?|cc\\b)\\b");

    private BusinessConceptMatching() {}

    /** Normalize hyphens/underscores to spaces and collapse whitespace. */
    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** True when the phrase refers to Proposed EDI / equated daily instalment — not "credit". */
    public static boolean isProposedEdiPhrase(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalize(text);
        if (PROPOSED_EDI.matcher(n).find()) return true;
        // Bare "edi" as a word, but reject when the only hit is inside "credit*"
        if (!EDI_WORD.matcher(n).find()) return false;
        // If the only "edi" occurrences are inside credit/credits/medical, reject
        String stripped = n.replaceAll("\\bcredits?\\b", " ")
                .replaceAll("\\bmedical\\b", " ")
                .replaceAll("\\bimmediate\\b", " ")
                .replaceAll("\\bexpedite\\b", " ");
        return EDI_WORD.matcher(stripped).find();
    }

    public static boolean isWriteOffPhrase(String text) {
        return text != null && WRITE_OFF.matcher(text).find();
    }

    public static boolean isCreditCardExceptionPhrase(String text) {
        if (text == null) return false;
        String n = normalize(text);
        return isWriteOffPhrase(n)
                && (n.contains("except") || n.contains("excluding") || n.contains("other than"))
                && CREDIT_CARD.matcher(n).find();
    }

    /**
     * Alias hit: exact, or whole-word / whole-phrase containment after normalization.
     * Short aliases (&lt; 4 chars) require word-boundary match to avoid {@code edi}∈{@code credit}.
     */
    public static boolean aliasMatches(String phrase, String alias) {
        if (phrase == null || alias == null || alias.isBlank()) return false;
        String p = normalize(phrase);
        String a = normalize(alias);
        if (p.isEmpty() || a.isEmpty()) return false;
        if (p.equals(a)) return true;
        if (a.length() <= 3) {
            return Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(a) + "(?![a-z0-9])")
                    .matcher(p).find();
        }
        return p.contains(a) || a.contains(p);
    }

    /** Score boost for source-aware preference (higher = better). */
    public static int sourceAffinity(String concept, String evaluatedFrom) {
        if (evaluatedFrom == null) return 0;
        String from = evaluatedFrom.toLowerCase(Locale.ROOT);
        String c = normalize(concept);
        if (isWriteOffPhrase(c) || c.contains("bureau") || c.contains("cibil") || c.contains("score")) {
            return from.contains("bureau") ? 30 : 0;
        }
        if (c.contains("emi bounce") || c.contains("cheque") || c.contains("banking") || c.contains("adb")) {
            return from.contains("bank") ? 30 : 0;
        }
        if (c.contains("gst")) {
            return from.contains("gst") ? 30 : 0;
        }
        if (isProposedEdiPhrase(c) || c.contains("foir") || c.contains("application")) {
            return from.contains("application") || from.contains("manual") ? 20 : 0;
        }
        return 0;
    }
}
