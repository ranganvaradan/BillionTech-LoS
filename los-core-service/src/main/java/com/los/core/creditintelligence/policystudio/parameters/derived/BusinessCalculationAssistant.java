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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-DERIVED-CALCULATION-UNIVERSAL-LENDER-FLOW-1
 * Target-semantics-first research. Outcomes:
 * CAN_CALCULATE | NEEDS_CLARIFICATION | MISSING_DATA | SEMANTIC_CONFLICT
 */
public final class BusinessCalculationAssistant {

    public static final String OUTCOME_CAN_CALCULATE = "CAN_CALCULATE";
    public static final String OUTCOME_NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";
    public static final String OUTCOME_MISSING_DATA = "MISSING_DATA";
    public static final String OUTCOME_SEMANTIC_CONFLICT = "SEMANTIC_CONFLICT";

    public static final String HISTORY_PAYMENT = "bureau.tradeline.payment_history";
    public static final String DPD_MONTH = "bureau.tradeline.dpd_month";
    public static final String REPORT_DATE = "bureau.report.date";
    public static final String OVERDUE_AMOUNT = "bureau.tradeline.overdue_amount";

    public static final String EVAL_DATE_AUTHORITY =
            "Application/policy evaluation business date when present; otherwise bureau report date. "
                    + "Persisted definitions use EVAL_AS_OF (never silent server wall-clock).";

    public static final String KIND_CONFIRM_EXISTING = "CONFIRM_EXISTING";
    public static final String KIND_AUTHOR_EXPRESSION = "AUTHOR_EXPRESSION";

    private static final Pattern PLUS_THRESHOLD = Pattern.compile("(\\d+)\\s*\\+");
    private static final Pattern GTE_THRESHOLD = Pattern.compile("(?:dpd\\s*)?(?:≥|>=)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOW_MONTHS = Pattern.compile("(?:trailing[_\\s-]*)?(\\d+)\\s*m(?:onth)?s?", Pattern.CASE_INSENSITIVE);

    private BusinessCalculationAssistant() {}

    public record ClarificationChoice(String id, String label) {}

    public record ClarificationQuestion(
            String id, String prompt, List<ClarificationChoice> choices) {}

    public record TargetSemantics(
            String intent,
            Integer dpdThreshold,
            String dpdMatchOp,
            Integer trailingMonths,
            String aggregation,
            boolean fromAuthoritativeTarget) {}

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
            boolean maxDpdProxyRejected,
            String proposalKind,
            boolean knownExistingCalculation,
            List<Map<String, Object>> conflictChoices) {

        public AssistantResult(
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
                boolean maxDpdProxyRejected) {
            this(businessOutcome, proposalStatus, confidence, humanExplanation, proposedExpression,
                    dataICanUse, missingInputs, assumptions, limitations, evidence,
                    clarificationQuestions, businessInterpretation, evaluationDateAuthority,
                    maxDpdProxyRejected, KIND_AUTHOR_EXPRESSION, false, List.of());
        }
    }

    public static Optional<AssistantResult> investigate(
            CanonicalParameterDefinition target,
            CanonicalParameterRegistry registry,
            String businessDescription,
            Map<String, String> clarificationAnswers) {
        if (target == null || registry == null) {
            return Optional.empty();
        }
        String desc = businessDescription == null ? "" : businessDescription.trim();
        Map<String, String> answers = clarificationAnswers == null ? Map.of() : clarificationAnswers;

        Optional<AssistantResult> conflict = detectSemanticConflict(target, desc);
        if (conflict.isPresent()) {
            return conflict;
        }

        TargetSemantics semantics = extractTargetSemantics(target, desc, answers);
        if ("COUNT_DPD_MONTHS".equals(semantics.intent())) {
            return Optional.of(investigateCountDpdMonths(target, registry, semantics));
        }
        if ("SUM_CC_OVERDUE".equals(semantics.intent()) || "KNOWN_IMPLEMENTED".equals(semantics.intent())) {
            return Optional.of(investigateKnownImplemented(target, registry, semantics));
        }
        if ("MONTHS_SINCE_OVERDUE".equals(semantics.intent())) {
            return Optional.of(investigateMonthsSinceOverdue(target, registry, desc, answers, semantics));
        }
        return Optional.empty();
    }

    /** Authoritative target metadata before weak keyword similarity. */
    public static TargetSemantics extractTargetSemantics(
            CanonicalParameterDefinition target,
            String businessDescription,
            Map<String, String> answers) {
        String blob = blobOf(target, businessDescription);
        String id = target.id() == null ? "" : target.id().toLowerCase(Locale.ROOT);
        String name = target.businessName() == null ? "" : target.businessName().toLowerCase(Locale.ROOT);
        String summary = target.calculationSummary() == null ? "" : target.calculationSummary().toLowerCase(Locale.ROOT);
        String period = target.period() == null ? "" : target.period().toUpperCase(Locale.ROOT);

        Integer threshold = thresholdFromAnswers(answers);
        boolean thresholdFromTarget = false;
        if (threshold == null) {
            Integer fromTarget = thresholdFromText(id + " " + name + " " + summary);
            if (fromTarget != null) {
                threshold = fromTarget;
                thresholdFromTarget = true;
            }
        }
        if (threshold == null) {
            threshold = thresholdFromText(businessDescription == null ? "" : businessDescription);
        }

        Integer window = windowFromPeriod(period);
        if (window == null) {
            window = windowFromText(id + " " + name + " " + summary);
        }
        if (window == null) {
            window = windowFromText(businessDescription == null ? "" : businessDescription);
        }

        boolean countIntent = id.contains("_count_") || id.contains("count_")
                || name.contains("count of") || summary.startsWith("count ")
                || blob.contains("count months") || blob.contains("count of");
        boolean trailingDpdCount = countIntent && (blob.contains("dpd") || blob.contains("past due"))
                && (window != null || blob.contains("trailing") || id.matches(".*_\\d+m$"));
        boolean cleanHistory = id.contains("clean_history") || id.contains("credit_after_overdue.clean")
                || (blob.contains("clean") && blob.contains("month")
                && (blob.contains("overdue") || blob.contains("after overdue")));
        boolean ccOverdue = id.equals("bureau.cc_overdue_amount")
                || (blob.contains("credit-card") || blob.contains("credit card") || blob.contains("cc overdue"))
                && blob.contains("overdue") && (blob.contains("amount") || blob.contains("sum"));
        boolean knownImplemented = target.capability() != null && target.capability().implemented()
                && !cleanHistory && !trailingDpdCount;

        if (trailingDpdCount) {
            int t = threshold == null ? 30 : threshold;
            String op = t >= 30 || thresholdFromTarget ? "GTE" : "GT";
            if (thresholdFromTarget || (name.contains("30+") || id.contains("30_plus") || summary.contains("≥30")
                    || summary.contains(">=30") || summary.contains("dpd≥30"))) {
                t = threshold == null ? 30 : threshold;
                op = "GTE";
            }
            int w = window == null ? 6 : window;
            return new TargetSemantics("COUNT_DPD_MONTHS", t, op, w, "COUNT", thresholdFromTarget || window != null);
        }
        if (cleanHistory || looksLikeMonthsSinceOverdueStrict(target, businessDescription)) {
            return new TargetSemantics("MONTHS_SINCE_OVERDUE", threshold, threshold != null && threshold >= 30 ? "GTE" : "GT",
                    null, "MONTHS_SINCE", threshold != null);
        }
        if (ccOverdue || (knownImplemented && id.contains("cc_overdue"))) {
            return new TargetSemantics("SUM_CC_OVERDUE", null, null, null, "SUM", true);
        }
        if (knownImplemented && CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(target.type())) {
            return new TargetSemantics("KNOWN_IMPLEMENTED", null, null, null, aggregationHint(target), true);
        }
        return new TargetSemantics("UNKNOWN", threshold, null, window, aggregationHint(target), false);
    }

    private static AssistantResult investigateCountDpdMonths(
            CanonicalParameterDefinition target,
            CanonicalParameterRegistry registry,
            TargetSemantics semantics) {
        Optional<CanonicalParameterDefinition> history = registry.findById(HISTORY_PAYMENT);
        Optional<CanonicalParameterDefinition> dpdMonth = registry.findById(DPD_MONTH);
        Optional<CanonicalParameterDefinition> reportDate = registry.findById(REPORT_DATE);
        List<String> evidence = new ArrayList<>();
        evidence.add("intent:COUNT_DPD_MONTHS");
        evidence.add("target_threshold:" + semantics.dpdThreshold());
        evidence.add("target_window_months:" + semantics.trailingMonths());
        evidence.add("max_dpd_proxy:rejected");
        List<String> limitations = List.of("max_dpd_* metrics are rejected as proxies for month-count");

        if (history.isEmpty() || !historySchemaSupportsDatedDpd(history.get())) {
            return new AssistantResult(
                    OUTCOME_MISSING_DATA,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    "I can't calculate this yet.\n\n"
                            + "I need bureau payment history with DPD and reporting month for each observation.",
                    null,
                    dataUseViews(history, dpdMonth, reportDate),
                    List.of("Dated bureau payment history (month + DPD)"),
                    List.of("Count of 30+ months requires a dated DPD series"),
                    limitations,
                    evidence,
                    List.of(),
                    "Count distinct months in a trailing window where DPD meets the target threshold.",
                    EVAL_DATE_AUTHORITY,
                    true,
                    KIND_AUTHOR_EXPRESSION,
                    false,
                    List.of());
        }

        int threshold = semantics.dpdThreshold() == null ? 30 : semantics.dpdThreshold();
        int window = semantics.trailingMonths() == null ? 6 : semantics.trailingMonths();
        String matchOp = semantics.dpdMatchOp() == null ? "GTE" : semantics.dpdMatchOp();
        Map<String, Object> expression = countPeriodsMatchingExpression(HISTORY_PAYMENT, matchOp, threshold, window);
        String explanation = "I'll count the number of reported months in the last " + window
                + " months where DPD was " + threshold + " days or more.";
        String interpretation = "Count distinct reported months in the trailing " + window
                + "-month window where bureau DPD " + matchOp + " " + threshold + ".";
        evidence.add("op:COUNT_PERIODS_MATCHING");
        evidence.add("clarification_suppressed:threshold_from_target");

        return new AssistantResult(
                OUTCOME_CAN_CALCULATE,
                DerivedCalculationResearchService.STATUS_READY_FOR_REVIEW,
                "HIGH",
                explanation,
                expression,
                dataUseViews(history, dpdMonth, reportDate),
                List.of(),
                List.of(
                        "Threshold " + matchOp + " " + threshold + " taken from target parameter semantics",
                        "Trailing window = " + window + " months ending at EVAL_AS_OF"),
                limitations,
                evidence,
                List.of(),
                interpretation,
                EVAL_DATE_AUTHORITY,
                true,
                KIND_AUTHOR_EXPRESSION,
                false,
                List.of());
    }

    private static AssistantResult investigateKnownImplemented(
            CanonicalParameterDefinition target,
            CanonicalParameterRegistry registry,
            TargetSemantics semantics) {
        List<String> evidence = new ArrayList<>();
        evidence.add("intent:KNOWN_IMPLEMENTED");
        evidence.add("capability.implemented=true");
        List<Map<String, Object>> declaredInputs = new ArrayList<>();
        if (target.requiredPrimitives() != null) {
            for (String pid : target.requiredPrimitives()) {
                registry.findById(pid).ifPresent(d -> declaredInputs.add(Map.of(
                        "parameterId", d.id(),
                        "displayName", businessInputLabel(d),
                        "role", "Declared input")));
            }
        }
        if ("SUM_CC_OVERDUE".equals(semantics.intent()) || "bureau.cc_overdue_amount".equals(target.id())) {
            List<Map<String, Object>> data = declaredInputs.isEmpty()
                    ? List.of(
                            Map.of("parameterId", "bureau.tradeline", "displayName", "Credit-card accounts", "role", "Account filter"),
                            Map.of("parameterId", OVERDUE_AMOUNT, "displayName", "Current overdue amount", "role", "Amount field"))
                    : List.of(
                            Map.of("parameterId", "bureau.tradeline.credit_card", "displayName", "Credit-card accounts", "role", "Account type filter"),
                            Map.of("parameterId", OVERDUE_AMOUNT, "displayName", "Current overdue amount", "role", "Overdue amount field"));
            // Wave-2 honesty: FILTER+SUM is now platform-capable, but catalogue "implemented"
            // / CONFIRM_EXISTING is NOT executable capability. Do not claim CAN_CALCULATE
            // without an authored SafeDerived expression (and Wave 2 does not auto-author
            // production definitions for cc_overdue).
            evidence.add("wave2:FILTER_SUM_platform_capable=true");
            evidence.add("wave2:executable_definition=false");
            evidence.add("wave2:confirm_existing_is_not_execution_authority=true");
            String explanation = "I can't treat this as executable yet.\n\n"
                    + "The platform can express credit-card overdue as FILTER + PROJECT + SUM over tradelines, "
                    + "but no executable authored SafeDerived definition is configured for this parameter. "
                    + "Catalogue implemented / Confirm Existing is not execution proof.";
            return new AssistantResult(
                    OUTCOME_MISSING_DATA,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    explanation,
                    null,
                    data,
                    List.of("Executable authored FILTER+SUM definition for credit-card overdue"),
                    List.of(
                            "Generic FILTER+SUM is supported by SafeDerived (Wave 2)",
                            "Production definition authoring is deferred (not Wave 2)"),
                    List.of(
                            "Do not interpret CONFIRM_EXISTING / catalogue implemented as CAN_CALCULATE",
                            "Studio helper related max metrics are not substitutes"),
                    evidence,
                    List.of(),
                    "Sum overdue amounts on CREDIT_CARD tradelines via generic collection operators — not yet authored.",
                    EVAL_DATE_AUTHORITY,
                    false,
                    KIND_CONFIRM_EXISTING,
                    false,
                    List.of());
        }
        String summary = target.calculationSummary() == null
                ? ("Calculate " + target.businessName())
                : target.calculationSummary();
        return new AssistantResult(
                OUTCOME_CAN_CALCULATE,
                DerivedCalculationResearchService.STATUS_READY_FOR_REVIEW,
                "HIGH",
                summary,
                null,
                declaredInputs,
                List.of(),
                List.of("Existing implemented calculation presented for lender confirmation"),
                List.of(),
                evidence,
                List.of(),
                summary,
                EVAL_DATE_AUTHORITY,
                false,
                KIND_CONFIRM_EXISTING,
                true,
                List.of());
    }

    private static AssistantResult investigateMonthsSinceOverdue(
            CanonicalParameterDefinition target,
            CanonicalParameterRegistry registry,
            String desc,
            Map<String, String> answers,
            TargetSemantics semantics) {
        Optional<CanonicalParameterDefinition> history = registry.findById(HISTORY_PAYMENT);
        Optional<CanonicalParameterDefinition> dpdMonth = registry.findById(DPD_MONTH);
        Optional<CanonicalParameterDefinition> reportDate = registry.findById(REPORT_DATE);

        boolean historyHasMonthAndDpd = history.isPresent() && historySchemaSupportsDatedDpd(history.get());
        List<String> evidence = new ArrayList<>();
        evidence.add("intent:months_since_last_overdue");
        List<String> limitations = new ArrayList<>();
        limitations.add("max_dpd_* metrics are rejected as proxies for clean-history months");

        Integer overdueThreshold = semantics.dpdThreshold();
        if (overdueThreshold == null) {
            overdueThreshold = resolveOverdueThreshold(desc, answers);
        }
        String interpretation = buildInterpretation(desc, overdueThreshold);

        if (!historyHasMonthAndDpd) {
            return new AssistantResult(
                    OUTCOME_MISSING_DATA,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    "I can't calculate this yet.\n\n"
                            + "I need a bureau payment history that includes both the DPD value and "
                            + "the reporting month for each observation.",
                    null,
                    dataUseViews(history, dpdMonth, reportDate),
                    List.of("Historical DPD values with corresponding reporting month/date"),
                    List.of("Dated collection required for last-overdue then month difference"),
                    limitations,
                    evidence,
                    List.of(),
                    interpretation,
                    EVAL_DATE_AUTHORITY,
                    true);
        }

        if (overdueThreshold == null) {
            ClarificationQuestion q = new ClarificationQuestion(
                    "overdue_threshold",
                    "Should I treat any DPD above 0 as an overdue, or only 30+ DPD?",
                    List.of(
                            new ClarificationChoice("dpd_gt_0", "Any DPD > 0"),
                            new ClarificationChoice("dpd_gte_30", "30+ DPD"),
                            new ClarificationChoice("other", "Something else")));
            return new AssistantResult(
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
                    true);
        }

        Map<String, Object> expression = monthsSinceLastMatchExpression(HISTORY_PAYMENT, overdueThreshold);
        String thresholdPhrase = overdueThreshold >= 30
                ? ("DPD ≥ " + overdueThreshold)
                : ("DPD > " + overdueThreshold);
        String explanation = "I'll find the most recent month in which any bureau account was overdue "
                + "(" + thresholdPhrase + "), then count the number of completed months since that overdue.";
        evidence.add("op:MONTHS_SINCE_LAST_MATCH");
        return new AssistantResult(
                OUTCOME_CAN_CALCULATE,
                DerivedCalculationResearchService.STATUS_READY_FOR_REVIEW,
                "HIGH",
                explanation,
                expression,
                dataUseViews(history, dpdMonth, reportDate),
                List.of(),
                List.of(
                        "Overdue means DPD " + (overdueThreshold >= 30 ? "≥ " : "> ") + overdueThreshold,
                        "Evaluation date uses EVAL_AS_OF"),
                limitations,
                evidence,
                List.of(),
                interpretation,
                EVAL_DATE_AUTHORITY,
                true);
    }

    private static Optional<AssistantResult> detectSemanticConflict(
            CanonicalParameterDefinition target, String desc) {
        if (desc == null || desc.isBlank()) {
            return Optional.empty();
        }
        String d = desc.toLowerCase(Locale.ROOT);
        String id = target.id() == null ? "" : target.id().toLowerCase(Locale.ROOT);
        String name = target.businessName() == null ? "" : target.businessName().toLowerCase(Locale.ROOT);
        boolean targetIsCcOverdue = id.contains("cc_overdue") || name.contains("credit-card overdue")
                || name.contains("credit card overdue");
        boolean expandsBeyondCc = d.contains("personal loan") || d.contains("business loan")
                || d.contains("all accounts") || d.contains("all loans")
                || (d.contains("include") && (d.contains("loan") || d.contains("tradeline"))
                && !d.contains("credit card") && !d.contains("credit-card"));
        if (targetIsCcOverdue && expandsBeyondCc) {
            List<Map<String, Object>> choices = List.of(
                    Map.of("id", "keep_canonical", "label", "Keep credit-card overdue amount"),
                    Map.of("id", "separate_parameter", "label", "Create a separate policy parameter"));
            return Optional.of(new AssistantResult(
                    OUTCOME_SEMANTIC_CONFLICT,
                    DerivedCalculationResearchService.STATUS_NEEDS_INPUT,
                    "HIGH",
                    "This would change the meaning of the parameter from credit-card overdue amount "
                            + "to total overdue across multiple account types.",
                    null,
                    List.of(),
                    List.of(),
                    List.of("Requested scope is broader than the canonical credit-card overdue meaning"),
                    List.of("Canonical parameter meaning is not silently redefined"),
                    List.of("semantic_conflict:cc_overdue_vs_multi_product"),
                    List.of(),
                    "User request expands beyond credit-card overdue amount.",
                    EVAL_DATE_AUTHORITY,
                    false,
                    KIND_CONFIRM_EXISTING,
                    true,
                    choices));
        }
        return Optional.empty();
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

    public static Map<String, Object> countPeriodsMatchingExpression(
            String historyId, String matchOp, int threshold, int windowMonths) {
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("op", "COUNT_PERIODS_MATCHING");
        expr.put("history", Map.of("op", "REF", "id", historyId));
        expr.put("matchField", "dpd");
        expr.put("matchOp", matchOp);
        expr.put("matchValue", threshold);
        expr.put("dateField", "month");
        expr.put("windowMonths", windowMonths);
        expr.put("distinctPeriods", true);
        expr.put("asOf", Map.of("op", "EVAL_AS_OF"));
        return expr;
    }

    /** Strict clean-history / months-since gate — excludes count-of-months metrics. */
    static boolean looksLikeMonthsSinceOverdueStrict(
            CanonicalParameterDefinition target, String businessDescription) {
        String blob = blobOf(target, businessDescription);
        String id = target.id() == null ? "" : target.id().toLowerCase(Locale.ROOT);
        if (id.contains("count") || blob.contains("count of") || blob.contains("count months")) {
            return false;
        }
        boolean months = blob.contains("month");
        boolean cleanOrHistory = blob.contains("clean") || blob.contains("since")
                || blob.contains("last overdue") || blob.contains("after overdue")
                || blob.contains("credit_after_overdue");
        // "payment history" alone must NOT qualify — that false-triggered dpd_30_plus_count_6m
        boolean overdueOrDpd = blob.contains("overdue") || blob.contains("delinquen")
                || (blob.contains("dpd") && cleanOrHistory);
        boolean fromDesc = businessDescription != null && !businessDescription.isBlank()
                && businessDescription.toLowerCase(Locale.ROOT).contains("last")
                && (businessDescription.toLowerCase(Locale.ROOT).contains("overdue")
                || businessDescription.toLowerCase(Locale.ROOT).contains("dpd"));
        boolean customerDefinedClean = "CUSTOMER_DEFINED".equalsIgnoreCase(
                target.period() == null ? "" : target.period())
                && (blob.contains("clean") || blob.contains("credit_after_overdue"));
        return fromDesc || customerDefinedClean || (months && cleanOrHistory && overdueOrDpd);
    }

    /** @deprecated use looksLikeMonthsSinceOverdueStrict — kept for older call sites/tests */
    static boolean looksLikeMonthsSinceOverdue(
            CanonicalParameterDefinition target, String businessDescription) {
        return looksLikeMonthsSinceOverdueStrict(target, businessDescription)
                || "COUNT_DPD_MONTHS".equals(extractTargetSemantics(target, businessDescription, Map.of()).intent())
                || "SUM_CC_OVERDUE".equals(extractTargetSemantics(target, businessDescription, Map.of()).intent())
                || "KNOWN_IMPLEMENTED".equals(extractTargetSemantics(target, businessDescription, Map.of()).intent());
    }

    static boolean historySchemaSupportsDatedDpd(CanonicalParameterDefinition history) {
        if (history == null) return false;
        String binding = (history.existingImplementationBinding() == null ? "" : history.existingImplementationBinding())
                + " " + (history.calculationSummary() == null ? "" : history.calculationSummary())
                + " " + (history.capability() == null || history.capability().providerFieldPath() == null
                ? "" : history.capability().providerFieldPath());
        String u = binding.toLowerCase(Locale.ROOT);
        boolean hasDpd = u.contains("dpd") || u.contains("dayspastdue") || u.contains("days past due");
        boolean hasMonth = u.contains("month") || u.contains("history48") || u.contains("yearmonth")
                || "HISTORY".equalsIgnoreCase(history.unit())
                || (history.period() != null && history.period().toUpperCase(Locale.ROOT).contains("MONTH"));
        boolean implemented = history.capability() != null && history.capability().implemented();
        return hasDpd && hasMonth && implemented;
    }

    static Integer resolveOverdueThreshold(String description, Map<String, String> answers) {
        Integer fromAns = thresholdFromAnswers(answers);
        if (fromAns != null) return fromAns;
        return thresholdFromText(description == null ? "" : description);
    }

    static String matchOpForThreshold(int threshold) {
        return threshold >= 30 ? "GTE" : "GT";
    }

    private static Integer thresholdFromAnswers(Map<String, String> answers) {
        if (answers == null) return null;
        String ans = answers.getOrDefault("overdue_threshold", "");
        if ("dpd_gt_0".equalsIgnoreCase(ans) || "0".equals(ans)) return 0;
        if ("dpd_gte_30".equalsIgnoreCase(ans) || "30".equals(ans)) return 30;
        return null;
    }

    static Integer thresholdFromText(String text) {
        if (text == null || text.isBlank()) return null;
        String d = text.toLowerCase(Locale.ROOT);
        if (d.contains("30_plus") || d.contains("30+") || d.contains("30 plus")) return 30;
        Matcher gte = GTE_THRESHOLD.matcher(d);
        if (gte.find()) return Integer.parseInt(gte.group(1));
        Matcher plus = PLUS_THRESHOLD.matcher(d);
        if (plus.find()) return Integer.parseInt(plus.group(1));
        if (d.contains("dpd > 0") || d.contains("dpd>0") || d.contains("ie dpd > 0")
                || d.contains("i.e. dpd > 0")) {
            return 0;
        }
        return null;
    }

    static Integer windowFromText(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = WINDOW_MONTHS.matcher(text.toLowerCase(Locale.ROOT));
        if (m.find()) return Integer.parseInt(m.group(1));
        if (text.toLowerCase(Locale.ROOT).contains("_6m") || text.contains("(6m)")) return 6;
        if (text.toLowerCase(Locale.ROOT).contains("_12m")) return 12;
        if (text.toLowerCase(Locale.ROOT).contains("_24m")) return 24;
        return null;
    }

    static Integer windowFromPeriod(String period) {
        if (period == null || period.isBlank()) return null;
        String p = period.toUpperCase(Locale.ROOT);
        if (p.contains("TRAILING_6") || p.contains("6M")) return 6;
        if (p.contains("TRAILING_12") || p.contains("12M")) return 12;
        if (p.contains("TRAILING_24") || p.contains("24M")) return 24;
        return null;
    }

    private static String aggregationHint(CanonicalParameterDefinition target) {
        if (target.capability() != null && target.capability().aggregation() != null) {
            return target.capability().aggregation();
        }
        return "";
    }

    private static String blobOf(CanonicalParameterDefinition target, String businessDescription) {
        return ((target.businessName() == null ? "" : target.businessName()) + " "
                + (target.calculationSummary() == null ? "" : target.calculationSummary()) + " "
                + (target.id() == null ? "" : target.id()) + " "
                + (businessDescription == null ? "" : businessDescription))
                .toLowerCase(Locale.ROOT);
    }

    private static String buildInterpretation(String desc, Integer threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("Find the most recent bureau tradeline month where DPD ");
        if (threshold == null) {
            sb.append("exceeds an overdue threshold (not yet confirmed)");
        } else {
            sb.append(threshold >= 30 ? "≥ " : "> ").append(threshold);
        }
        sb.append(", then count completed months from that observation to the evaluation date.");
        if (desc != null && !desc.isBlank()) {
            sb.append(" Lender description considered.");
        }
        return sb.toString();
    }

    private static String businessInputLabel(CanonicalParameterDefinition d) {
        if (d.id() != null && d.id().contains("payment_history")) return "Bureau payment history";
        if (d.id() != null && d.id().contains("dpd")) return "Days past due (DPD)";
        if (d.id() != null && d.id().contains("report.date")) return "Reporting month/date";
        if (d.id() != null && d.id().contains("overdue_amount")) return "Current overdue amount";
        return d.businessName() == null ? d.id() : d.businessName();
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
                "displayName", "Days past due (DPD)",
                "role", "Per-month DPD value")));
        reportDate.ifPresent(r -> out.add(Map.of(
                "parameterId", r.id(),
                "displayName", "Reporting month/date",
                "role", "Supports evaluation-date context")));
        return out;
    }

    public static boolean isMaxDpdProxyId(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        return s.contains("max_dpd") || s.matches(".*dpd\\.max.*");
    }

    public static Set<String> constructedOps() {
        return Set.of("MONTHS_SINCE_LAST_MATCH", "EVAL_AS_OF", "COUNT_PERIODS_MATCHING");
    }
}
