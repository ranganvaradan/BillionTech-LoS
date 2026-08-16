package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * POLICY-DERIVED-CALCULATION-BUSINESS-ASSISTANT-1
 * Operation-aware research over catalogue/schema — not keyword REF proxies.
 * Outcomes: CAN_CALCULATE | NEEDS_CLARIFICATION | MISSING_DATA.
 */
public final class BusinessCalculationAssistant {

    public static final String OUTCOME_CAN_CALCULATE = "CAN_CALCULATE";
    public static final String OUTCOME_NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";
    public static final String OUTCOME_MISSING_DATA = "MISSING_DATA";

    public static final String HISTORY_PAYMENT = "bureau.tradeline.payment_history";
    public static final String DPD_MONTH = "bureau.tradeline.dpd_month";
    public static final String REPORT_DATE = "bureau.report.date";

    public static final String EVAL_DATE_AUTHORITY =
            "Application/policy evaluation business date when present; otherwise bureau report date. "
                    + "Persisted definitions use EVAL_AS_OF (never silent server wall-clock).";

    private BusinessCalculationAssistant() {}

    public record ClarificationChoice(String id, String label) {}

    public record ClarificationQuestion(
            String id, String prompt, List<ClarificationChoice> choices) {}

    public record AssistantResult(
            String businessOutcome,
            String proposalStatus,
            String confidence,
            String humanExplanation,
            Map<String, Object> proposedExpression,
            List<Map<String, Object>> dataICanUse,
            List<String> missingInputs,
            List<String> assumptions,
            List<String> limitations,
            List<String> evidence,
            List<ClarificationQuestion> clarificationQuestions,
            String businessInterpretation,
            String evaluationDateAuthority,
            boolean maxDpdProxyRejected) {}

    /**
     * Investigate whether a months-since-last-overdue style metric can be built from
     * available bureau history schema + optional lender business description.
     */
    public static Optional<AssistantResult> investigate(
            CanonicalParameterDefinition target,
            CanonicalParameterRegistry registry,
            String businessDescription,
            Map<String, String> clarificationAnswers) {
        if (target == null || registry == null) {
            return Optional.empty();
        }
        if (!looksLikeMonthsSinceOverdue(target, businessDescription)) {
            return Optional.empty();
        }

        Optional<CanonicalParameterDefinition> history = registry.findById(HISTORY_PAYMENT);
        Optional<CanonicalParameterDefinition> dpdMonth = registry.findById(DPD_MONTH);
        Optional<CanonicalParameterDefinition> reportDate = registry.findById(REPORT_DATE);

        boolean historyHasMonthAndDpd = history.isPresent() && historySchemaSupportsDatedDpd(history.get());
        boolean dpdMonthImplemented = dpdMonth.isPresent()
                && dpdMonth.get().capability() != null
                && dpdMonth.get().capability().implemented();

        List<String> evidence = new ArrayList<>();
        evidence.add("intent:months_since_last_overdue");
        evidence.add("schema_scan:payment_history");
        List<String> limitations = new ArrayList<>();
        limitations.add("max_dpd_* metrics are rejected as proxies for clean-history months");
        boolean maxDpdRejected = true;

        String desc = businessDescription == null ? "" : businessDescription.trim();
        Map<String, String> answers = clarificationAnswers == null ? Map.of() : clarificationAnswers;
        Integer overdueThreshold = resolveOverdueThreshold(desc, answers);

        String interpretation = buildInterpretation(desc, overdueThreshold);

        if (!historyHasMonthAndDpd && !dpdMonthImplemented) {
            return Optional.of(new AssistantResult(
                    OUTCOME_MISSING_DATA,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    "I can't calculate this yet.\n\n"
                            + "I need a bureau payment history that includes both the DPD value and "
                            + "the reporting month for each observation. That dated history is not "
                            + "available in the current catalogue.",
                    null,
                    List.of(),
                    List.of(
                            "Historical DPD values with corresponding reporting month/date",
                            "Bureau payment / tradeline history series"),
                    List.of("Requested metric needs dated overdue observations"),
                    limitations,
                    evidence,
                    List.of(),
                    interpretation,
                    EVAL_DATE_AUTHORITY,
                    maxDpdRejected));
        }

        if (history.isEmpty() || !historySchemaSupportsDatedDpd(history.get())) {
            // dpd_month alone without aligned month series is insufficient for "last overdue date"
            return Optional.of(new AssistantResult(
                    OUTCOME_MISSING_DATA,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    "I can't calculate this yet.\n\n"
                            + "I can see DPD-related fields, but I cannot reliably identify the "
                            + "month/date for each historical overdue observation as a dated series.",
                    null,
                    dataUseViews(history, dpdMonth, reportDate),
                    List.of(
                            "Reporting month/date aligned to each historical DPD observation",
                            "Bureau payment history series (month + DPD)"),
                    List.of("Dated collection required for last-overdue then month difference"),
                    limitations,
                    evidence,
                    List.of(),
                    interpretation,
                    EVAL_DATE_AUTHORITY,
                    maxDpdRejected));
        }

        if (overdueThreshold == null) {
            ClarificationQuestion q = new ClarificationQuestion(
                    "overdue_threshold",
                    "Should I treat any DPD above 0 as an overdue, or only 30+ DPD?",
                    List.of(
                            new ClarificationChoice("dpd_gt_0", "Any DPD > 0"),
                            new ClarificationChoice("dpd_gte_30", "30+ DPD"),
                            new ClarificationChoice("other", "Something else")));
            return Optional.of(new AssistantResult(
                    OUTCOME_NEEDS_CLARIFICATION,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "MEDIUM",
                    "I need one detail.\n\n"
                            + "I can use bureau payment history (DPD by reported month), but I need "
                            + "to know what should count as an overdue before I calculate clean "
                            + "history months.",
                    null,
                    dataUseViews(history, dpdMonth, reportDate),
                    List.of(),
                    List.of("Overdue threshold not yet confirmed"),
                    limitations,
                    evidence,
                    List.of(q),
                    interpretation,
                    EVAL_DATE_AUTHORITY,
                    maxDpdRejected));
        }

        Map<String, Object> expression = monthsSinceLastMatchExpression(HISTORY_PAYMENT, overdueThreshold);
        List<Map<String, Object>> dataUsed = dataUseViews(history, dpdMonth, reportDate);
        String thresholdPhrase = overdueThreshold >= 30
                ? ("DPD ≥ " + overdueThreshold)
                : ("DPD > " + overdueThreshold);
        String explanation = "I can calculate this.\n\n"
                + "I'll find the most recent month in which any bureau account was overdue "
                + "(" + thresholdPhrase + "), then count the number of months since that overdue.\n\n"
                + "I'll use:\n"
                + "• Bureau payment history\n"
                + "• DPD for each reported month\n"
                + "• Reporting month/date\n\n"
                + "Result: Clean history months";

        evidence.add("operation:FILTER_history_WHERE_dpd_GT_" + overdueThreshold);
        evidence.add("operation:MAX_observation_month");
        evidence.add("operation:MONTH_DIFF_to_EVAL_AS_OF");
        evidence.add("op:MONTHS_SINCE_LAST_MATCH");

        return Optional.of(new AssistantResult(
                OUTCOME_CAN_CALCULATE,
                DerivedCalculationResearchService.STATUS_READY_FOR_REVIEW,
                "HIGH",
                explanation,
                expression,
                dataUsed,
                List.of(),
                List.of(
                        "Overdue means DPD > " + overdueThreshold + " on any tradeline/account month",
                        "Evaluation date uses EVAL_AS_OF (application/report date authority)"),
                limitations,
                evidence,
                List.of(),
                interpretation,
                EVAL_DATE_AUTHORITY,
                maxDpdRejected));
    }

    public static Map<String, Object> monthsSinceLastMatchExpression(String historyId, int threshold) {
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("op", "MONTHS_SINCE_LAST_MATCH");
        expr.put("history", Map.of("op", "REF", "id", historyId));
        expr.put("matchField", "dpd");
        expr.put("matchOp", matchOpForThreshold(threshold));
        expr.put("matchValue", threshold);
        expr.put("dateField", "month");
        expr.put("asOf", Map.of("op", "EVAL_AS_OF"));
        return expr;
    }

    static boolean looksLikeMonthsSinceOverdue(
            CanonicalParameterDefinition target, String businessDescription) {
        String blob = ((target.businessName() == null ? "" : target.businessName()) + " "
                + (target.calculationSummary() == null ? "" : target.calculationSummary()) + " "
                + (target.id() == null ? "" : target.id()) + " "
                + (businessDescription == null ? "" : businessDescription))
                .toLowerCase(Locale.ROOT);
        boolean months = blob.contains("month");
        boolean cleanOrHistory = blob.contains("clean") || blob.contains("history")
                || blob.contains("since") || blob.contains("last overdue")
                || blob.contains("after overdue") || blob.contains("credit_after_overdue");
        boolean overdueOrDpd = blob.contains("overdue") || blob.contains("dpd")
                || blob.contains("delinquen") || blob.contains("past due");
        boolean fromDesc = businessDescription != null && !businessDescription.isBlank()
                && (businessDescription.toLowerCase(Locale.ROOT).contains("last")
                && (overdueOrDpd || businessDescription.toLowerCase(Locale.ROOT).contains("overdue")));
        return (months && cleanOrHistory && overdueOrDpd) || fromDesc
                || "CUSTOMER_DEFINED".equalsIgnoreCase(
                target.period() == null ? "" : target.period())
                && (blob.contains("clean") || blob.contains("credit_after_overdue"));
    }

    static boolean historySchemaSupportsDatedDpd(CanonicalParameterDefinition history) {
        if (history == null) return false;
        String binding = (history.existingImplementationBinding() == null ? "" : history.existingImplementationBinding())
                + " " + (history.calculationSummary() == null ? "" : history.calculationSummary())
                + " " + (history.capability() == null || history.capability().providerFieldPath() == null
                ? "" : history.capability().providerFieldPath())
                + " " + (history.liveRuleParameter() == null ? "" : history.liveRuleParameter());
        String u = binding.toLowerCase(Locale.ROOT);
        boolean hasDpd = u.contains("dpd") || u.contains("dayspastdue") || u.contains("days past due");
        boolean hasMonth = u.contains("month") || u.contains("history48") || u.contains("yearmonth")
                || "HISTORY".equalsIgnoreCase(history.unit())
                || (history.period() != null && history.period().toUpperCase(Locale.ROOT).contains("MONTH"));
        boolean implemented = history.capability() != null && history.capability().implemented();
        return hasDpd && hasMonth && implemented;
    }

    static Integer resolveOverdueThreshold(String description, Map<String, String> answers) {
        String ans = answers.getOrDefault("overdue_threshold", "");
        if ("dpd_gt_0".equalsIgnoreCase(ans) || "0".equals(ans)) return 0;
        if ("dpd_gte_30".equalsIgnoreCase(ans) || "30".equals(ans)) return 30;
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (d.contains("dpd > 0") || d.contains("dpd>0") || d.contains("dpd greater than 0")
                || d.contains("days past due > 0") || d.contains("overdue, ie dpd > 0")
                || d.contains("ie dpd > 0") || d.contains("i.e. dpd > 0")) {
            return 0;
        }
        if (d.contains("30+") || d.contains("30 +") || d.contains("dpd >= 30")
                || d.contains("dpd > 30") || d.contains("30 days")) {
            return 30;
        }
        return null;
    }

    /** Threshold semantics: 0 → DPD &gt; 0 (GT); 30 → DPD ≥ 30 (GTE). */
    static String matchOpForThreshold(int threshold) {
        return threshold >= 30 ? "GTE" : "GT";
    }

    private static String buildInterpretation(String desc, Integer threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("Find the most recent bureau tradeline month where DPD ");
        if (threshold == null) {
            sb.append("exceeds an overdue threshold (not yet confirmed)");
        } else {
            sb.append("> ").append(threshold);
        }
        sb.append(", then count completed months from that observation to the evaluation date.");
        if (desc != null && !desc.isBlank()) {
            sb.append(" Lender description considered.");
        }
        return sb.toString();
    }

    private static List<Map<String, Object>> dataUseViews(
            Optional<CanonicalParameterDefinition> history,
            Optional<CanonicalParameterDefinition> dpdMonth,
            Optional<CanonicalParameterDefinition> reportDate) {
        List<Map<String, Object>> out = new ArrayList<>();
        history.ifPresent(h -> out.add(Map.of(
                "parameterId", h.id(),
                "displayName", "Bureau payment history",
                "role", "Dated DPD observations by tradeline/month")));
        dpdMonth.ifPresent(d -> out.add(Map.of(
                "parameterId", d.id(),
                "displayName", "Days past due (month)",
                "role", "Per-month DPD value")));
        reportDate.ifPresent(r -> out.add(Map.of(
                "parameterId", r.id(),
                "displayName", "Bureau report date",
                "role", "Supports evaluation-date context")));
        return out;
    }

    public static boolean isMaxDpdProxyId(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        return s.contains("max_dpd") || s.matches(".*dpd\\.max.*");
    }

    public static Set<String> constructedOps() {
        return Set.of("MONTHS_SINCE_LAST_MATCH", "EVAL_AS_OF");
    }
}
