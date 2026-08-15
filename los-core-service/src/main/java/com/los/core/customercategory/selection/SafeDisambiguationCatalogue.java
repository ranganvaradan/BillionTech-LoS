package com.los.core.customercategory.selection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared safe disambiguation question catalogue — reuses intake-style keys.
 * Not a second questionnaire engine; lender Categories declare attribute values only.
 * CREDIT_THRESHOLD questions are never registered here.
 */
public final class SafeDisambiguationCatalogue {

    public static final String Q_FINANCIAL_DATA_ROUTE = "FINANCIAL_DATA_ROUTE";
    public static final String ATTR_FINANCIAL_DATA_ROUTE = "financial_data_route";

    public static final String OPT_FINANCIAL_STATEMENTS = "FINANCIAL_STATEMENTS";
    public static final String OPT_BANK_AA = "BANK_AA";
    public static final String OPT_BOTH = "BOTH";

    private SafeDisambiguationCatalogue() {}

    public record QuestionDef(
            String questionId,
            String attributeKey,
            String prompt,
            List<OptionDef> options,
            boolean safeForCategoryDisambiguation,
            String intakeFieldKey
    ) {}

    public record OptionDef(String value, String label) {}

    private static final Map<String, QuestionDef> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put(Q_FINANCIAL_DATA_ROUTE, new QuestionDef(
                Q_FINANCIAL_DATA_ROUTE,
                ATTR_FINANCIAL_DATA_ROUTE,
                "How would you like to provide financial information?",
                List.of(
                        new OptionDef(OPT_FINANCIAL_STATEMENTS, "Financial statements"),
                        new OptionDef(OPT_BANK_AA, "Bank account data"),
                        new OptionDef(OPT_BOTH, "I can provide both")),
                true,
                "financialDataRoute"));
    }

    public static QuestionDef get(String questionId) {
        return QUESTIONS.get(questionId);
    }

    public static List<QuestionDef> allSafe() {
        return QUESTIONS.values().stream().filter(QuestionDef::safeForCategoryDisambiguation).toList();
    }

    /** Answer retains Category if Category accepts the answer value (or BOTH). */
    public static boolean answerRetains(String attributeKey, String answer, List<String> categoryAccepted) {
        if (categoryAccepted == null || categoryAccepted.isEmpty() || answer == null) {
            return false;
        }
        String a = answer.trim().toUpperCase();
        for (String raw : categoryAccepted) {
            if (raw == null) {
                continue;
            }
            String v = raw.trim().toUpperCase();
            if (v.equals(a)) {
                return true;
            }
            // BOTH on Category means it accepts either route or explicit BOTH
            if (OPT_BOTH.equals(v) && (OPT_FINANCIAL_STATEMENTS.equals(a)
                    || OPT_BANK_AA.equals(a) || OPT_BOTH.equals(a))) {
                return true;
            }
        }
        // Customer BOTH: keep Categories that accept statements OR bank OR both
        if (OPT_BOTH.equals(a)) {
            for (String raw : categoryAccepted) {
                if (raw == null) {
                    continue;
                }
                String v = raw.trim().toUpperCase();
                if (OPT_FINANCIAL_STATEMENTS.equals(v) || OPT_BANK_AA.equals(v) || OPT_BOTH.equals(v)) {
                    return true;
                }
            }
        }
        return false;
    }
}
