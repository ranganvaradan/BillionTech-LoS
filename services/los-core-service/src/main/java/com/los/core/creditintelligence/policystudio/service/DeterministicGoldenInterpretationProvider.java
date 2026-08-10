package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic golden interpreter for Banking BRE / Bureau BRE fixtures (no external LLM).
 */
@Component
public class DeterministicGoldenInterpretationProvider implements PolicyInterpretationProvider {

    public static final String PROVIDER_CODE = "DETERMINISTIC_GOLDEN_V1";

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public List<CiPolicyInterpretation> interpret(
            List<CiPolicyClause> clauses,
            PolicyAuthoringRegistry registry,
            PolicyClauseExtractor.FixtureKind kind) {
        List<CiPolicyInterpretation> out = new ArrayList<>();
        for (CiPolicyClause clause : clauses) {
            out.add(interpretOne(clause, kind));
        }
        return out;
    }

    private CiPolicyInterpretation interpretOne(CiPolicyClause clause, PolicyClauseExtractor.FixtureKind kind) {
        String text = clause.getNormalizedText() != null ? clause.getNormalizedText() : clause.getSourceText();
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, Object> expression = new LinkedHashMap<>();
        List<Object> inputs = new ArrayList<>();
        List<Object> outputs = new ArrayList<>();
        List<Object> products = new ArrayList<>();
        Map<String, Object> thresholds = new LinkedHashMap<>();
        Map<String, Object> breakdown = new LinkedHashMap<>();
        String meaning;
        String period = null;
        String type = clause.getClauseType();

        if (kind == PolicyClauseExtractor.FixtureKind.BANKING_BRE) {
            var r = interpretBanking(clause, text, lower);
            expression = r.expression;
            inputs = r.inputs;
            outputs = r.outputs;
            products = r.products;
            thresholds = r.thresholds;
            meaning = r.meaning;
            period = r.period;
            type = r.type;
            breakdown = r.breakdown;
        } else if (kind == PolicyClauseExtractor.FixtureKind.BUREAU_BRE) {
            var r = interpretBureau(clause, text, lower);
            expression = r.expression;
            inputs = r.inputs;
            outputs = r.outputs;
            products = r.products;
            thresholds = r.thresholds;
            meaning = r.meaning;
            period = r.period;
            type = r.type;
            breakdown = r.breakdown;
        } else if (KycPolicyAuthoringSupport.isKycClause(clause)
                || (kind == PolicyClauseExtractor.FixtureKind.KYC_BRE
                && KycPolicyAuthoringSupport.looksLikeKycClause(text))) {
            var r = interpretKyc(clause, text);
            expression = r.expression;
            inputs = r.inputs;
            outputs = r.outputs;
            products = r.products;
            thresholds = r.thresholds;
            meaning = r.meaning;
            period = r.period;
            type = r.type;
            breakdown = r.breakdown;
        } else if (kind == PolicyClauseExtractor.FixtureKind.KYC_BRE
                && (lower.contains("bureau score") || lower.contains("650"))) {
            // Mixed Decision Policy sample — credit clause alongside KYC
            meaning = "Credit underwriting: bureau score eligibility";
            expression = PolicyDsl.or(
                    PolicyDsl.eq(PolicyDsl.metric("bureau.status_ntc"), Map.of("const", true)),
                    PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)));
            inputs = new ArrayList<>(List.of("bureau.status_ntc", "bureau.score"));
            outputs = new ArrayList<>(List.of("PASS_FAIL"));
            period = "POINT_IN_TIME";
            type = ClauseType.HARD_RULE.name();
            breakdown.put("overall", 0.80);
        } else {
            meaning = "Heuristic interpretation of: " + text;
            expression = Map.of("op", "UNKNOWN", "source", text);
            breakdown.put("overall", 0.40);
        }

        return CiPolicyInterpretation.builder()
                .id(UUID.randomUUID())
                .clauseId(clause.getId())
                .interpretationVersion(1)
                .interpretedClauseType(type)
                .naturalLanguageMeaning(meaning)
                .candidateExpression(expression)
                .candidateInputs(inputs)
                .candidateOutputs(outputs)
                .candidateProductScope(products)
                .candidatePeriod(period)
                .candidateThresholds(thresholds)
                .confidence(new BigDecimal(String.valueOf(breakdown.getOrDefault("overall", 0.85))))
                .confidenceBreakdown(breakdown)
                .limitations("P0 deterministic golden — requires human mapping review before policy build")
                .aiModel("NONE")
                .aiModelVersion("P0")
                .promptVersion("GOLDEN_V1")
                .providerCode(PROVIDER_CODE)
                .build();
    }

    private Interp interpretBanking(CiPolicyClause clause, String text, String lower) {
        Interp r = new Interp();
        r.type = clause.getClauseType();
        r.breakdown.put("clauseExtraction", 0.95);
        r.breakdown.put("clauseClassification", 0.90);
        r.breakdown.put("overall", 0.88);

        if (ClauseType.INFORMATION_REQUIREMENT.name().equals(clause.getClauseType())) {
            r.meaning = "Report/information requirement (not pass/fail): " + text;
            r.outputs.add("INFORMATION");
            r.period = "TRAILING_3M";
            r.expression = Map.of("op", "REQUIRE_METRIC_AVAILABILITY", "phrase", text);
            mapInfoRequirement(r, lower);
            return r;
        }

        if (ClauseType.METRIC_ADJUSTMENT.name().equals(clause.getClauseType())) {
            r.meaning = "Metric adjustment for ADB calculation: " + text;
            r.outputs.add("METRIC_DEFINITION");
            r.period = "TRAILING_3M";
            r.type = ClauseType.METRIC_ADJUSTMENT.name();
            if (lower.contains("loan")) {
                r.expression = Map.of("op", "EXCLUDE", "from", "banking.avg_daily_balance_3m",
                        "category", "LOAN_DISBURSEMENT", "period", "TRAILING_3M");
                r.inputs.add("banking.avg_daily_balance_3m");
            } else if (lower.contains("gaming")) {
                r.expression = Map.of("op", "EXCLUDE", "from", "banking.avg_daily_balance_3m",
                        "category", "ONLINE_GAMING_CREDIT");
            } else if (lower.contains("bulk") || lower.contains("deposition")) {
                r.expression = Map.of("op", "EXCLUDE", "from", "banking.avg_daily_balance_3m",
                        "when", "amount > 10 * average_depositions_3m");
            }
            return r;
        }

        if (lower.contains("starter")) {
            r.products.add("STARTER");
            r.period = "TRAILING_3M";
            r.meaning = "STARTER: ADB 3m >= proposed EDI";
            r.expression = PolicyDsl.gte(PolicyDsl.metric("banking.avg_daily_balance_3m"),
                    PolicyDsl.appField("application.proposed_edi"));
            r.inputs.addAll(List.of("banking.avg_daily_balance_3m", "application.proposed_edi"));
            r.outputs.add("PASS_FAIL");
            r.thresholds.put("operator", "GTE");
            return r;
        }

        if (lower.contains("digileap") || lower.contains("digi leap")) {
            r.products.add("DIGILEAP");
            r.period = "TRAILING_3M";
            if (lower.contains("transaction")) {
                r.meaning = "DIGILEAP: average monthly transactions >= 20";
                r.expression = PolicyDsl.gte(PolicyDsl.metric("banking.transaction_count.average_monthly_3m"), 20);
                r.thresholds.put("minTransactions", 20);
                r.inputs.add("banking.transaction_count.average_monthly_3m");
            } else {
                r.meaning = "DIGILEAP: ADB/5 >= proposed EDI";
                r.expression = PolicyDsl.gte(
                        PolicyDsl.divide(PolicyDsl.metric("banking.avg_daily_balance_3m"), 5),
                        PolicyDsl.appField("application.proposed_edi"));
                r.inputs.addAll(List.of("banking.avg_daily_balance_3m", "application.proposed_edi"));
            }
            r.outputs.add("PASS_FAIL");
            return r;
        }

        if (lower.contains("smart switch")) {
            r.products.add("SMART_SWITCH");
            r.period = "TRAILING_3M";
            if (lower.contains("settlement") && (lower.contains("number") || lower.contains("count")
                    || lower.contains("minimum of 20"))) {
                r.meaning = "SMART_SWITCH: monthly average settlement count >= 20";
                r.expression = PolicyDsl.gte(PolicyDsl.metric("banking.settlement.count_monthly_avg_3m"), 20);
                r.thresholds.put("minSettlements", 20);
                r.inputs.add("banking.settlement.count_monthly_avg_3m");
            } else {
                r.meaning = "SMART_SWITCH: Average Daily Settlement / 10 >= proposed EDI";
                r.expression = PolicyDsl.gte(
                        PolicyDsl.divide(PolicyDsl.metric("banking.settlement.avg_daily_3m"), 10),
                        PolicyDsl.appField("application.proposed_edi"));
                r.inputs.addAll(List.of("banking.settlement.avg_daily_3m", "application.proposed_edi"));
            }
            r.outputs.add("PASS_FAIL");
            return r;
        }

        if (lower.contains("reboost") || lower.contains("re boost")) {
            r.products.add("REBOOST");
            r.period = "TRAILING_3M";
            r.thresholds.put("loanAmountBoundary", Map.of("op", "GT", "value", 60000));
            if (lower.contains("transaction")) {
                r.meaning = "REBOOST (loan amount > 60000): monthly avg transactions >= 30";
                r.expression = PolicyDsl.and(
                        PolicyDsl.gt(PolicyDsl.appField("application.loan_amount"), 60000),
                        PolicyDsl.gte(PolicyDsl.metric("banking.transaction_count.average_monthly_3m"), 30));
                r.thresholds.put("minTransactions", 30);
            } else {
                r.meaning = "REBOOST (loan amount > 60000): ADB/5 >= proposed EDI";
                r.expression = PolicyDsl.and(
                        PolicyDsl.gt(PolicyDsl.appField("application.loan_amount"), 60000),
                        PolicyDsl.gte(
                                PolicyDsl.divide(PolicyDsl.metric("banking.avg_daily_balance_3m"), 5),
                                PolicyDsl.appField("application.proposed_edi")));
            }
            r.outputs.add("PASS_FAIL");
            return r;
        }

        if (lower.contains("inward") && (lower.contains("return") || lower.contains("cheque"))) {
            r.products.add("ALL_BANK_STATEMENT");
            r.period = "TRAILING_3M";
            r.meaning = "Inward returns: if txn>100 then ratio<=5% else if txn<100 then count<=5; exactly 100 unresolved";
            r.expression = PolicyDsl.iff(
                    PolicyDsl.gt(PolicyDsl.metric("banking.transaction_count.total_3m"), 100),
                    PolicyDsl.lte(PolicyDsl.metric("banking.inward_return.ratio_3m"), 5),
                    PolicyDsl.lte(PolicyDsl.metric("banking.inward_return.count_3m"), 5));
            r.thresholds.put("txnBoundary", 100);
            r.thresholds.put("ratioPct", 5);
            r.thresholds.put("countMax", 5);
            r.breakdown.put("operatorInterpretation", 0.55);
            r.outputs.add("PASS_FAIL");
            return r;
        }

        r.meaning = "Banking clause: " + text;
        r.expression = Map.of("op", "HARD", "source", text);
        return r;
    }

    private void mapInfoRequirement(Interp r, String lower) {
        if (lower.contains("average daily balance")) {
            r.inputs.add("banking.avg_daily_balance_3m");
        } else if (lower.contains("average monthly transaction")) {
            r.inputs.add("banking.transaction_count.average_monthly_3m");
        } else if (lower.contains("qr settlement") || lower.contains("daily qr")) {
            r.inputs.add("banking.settlement.avg_daily_3m");
        } else if (lower.contains("settlement count")) {
            r.inputs.add("banking.settlement.count_monthly_avg_3m");
        } else if (lower.contains("inward")) {
            r.inputs.add("banking.inward_return.ratio_3m");
            r.inputs.add("banking.inward_return.count_3m");
        }
    }

    private Interp interpretBureau(CiPolicyClause clause, String text, String lower) {
        Interp r = new Interp();
        r.type = clause.getClauseType();
        r.products.add("ALL");
        r.breakdown.put("clauseExtraction", 0.95);
        r.breakdown.put("overall", 0.90);
        r.outputs.add("PASS_FAIL");

        if (clause.getParentClauseId() != null) {
            return interpretOverdueChild(clause, lower, r);
        }

        if (lower.contains("bureau score") || (lower.contains("score") && lower.contains("650"))) {
            r.meaning = "Score allowed only if -1 OR NTC OR score >= 650 (not simply >=650)";
            r.expression = PolicyDsl.or(
                    PolicyDsl.eq(PolicyDsl.metric("bureau.score"), -1),
                    PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), true),
                    PolicyDsl.gte(PolicyDsl.metric("bureau.score"), 650));
            r.inputs.addAll(List.of("bureau.score", "bureau.status_ntc"));
            r.thresholds.put("minScore", 650);
            r.thresholds.put("specialScores", List.of(-1, "NTC"));
            return r;
        }

        if (lower.contains("write-off") || lower.contains("write off")) {
            r.meaning = "No loan write-offs except credit cards";
            r.expression = PolicyDsl.and(
                    PolicyDsl.exists(PolicyDsl.fact("bureau.write_off")),
                    Map.of("op", "NE", "left", Map.of("fact", "bureau.tradeline.product"), "right", "CREDIT_CARD"));
            r.inputs.add("bureau.write_off");
            return r;
        }

        if (lower.contains("overdue rule")) {
            r.meaning = "Parent overdue exception: pass only if all child rules 1–4 pass";
            r.type = ClauseType.EXCEPTION.name();
            r.expression = Map.of("op", "AND_CHILDREN", "parent", "OVERDUE_EXCEPTION", "children", List.of("1", "2", "3", "4"));
            return r;
        }

        if (lower.contains("no loan overdue") && !lower.contains("overdue rule")) {
            r.meaning = "No loan overdue except CC subject to overdue exception rules";
            r.expression = Map.of("op", "HARD", "ref", "OVERDUE_EXCEPTION");
            return r;
        }

        if (lower.contains("credit card overdue") || (lower.contains("overdue amounts greater than"))) {
            r.meaning = "CC overdue > 5000 → FAIL (5000 PASS, 5001 FAIL)";
            r.expression = PolicyDsl.gt(PolicyDsl.metric("bureau.cc_overdue_amount"), 5000);
            r.thresholds.put("ccOverdue", Map.of("op", "GT", "value", 5000));
            r.inputs.add("bureau.cc_overdue_amount");
            // onTrue FAIL for GT expression evaluated as violation
            return r;
        }

        if (lower.contains("days past due") || lower.contains("(dpd)") || lower.contains("dpd")) {
            r.meaning = "Max DPD > 30 in last 6 months → FAIL; period uses eval window not wall clock";
            r.period = "TRAILING_6M";
            r.expression = PolicyDsl.gt(PolicyDsl.metric("bureau.max_dpd_6m"), 30);
            r.thresholds.put("dpd", Map.of("op", "GT", "value", 30));
            r.inputs.add("bureau.max_dpd_6m");
            return r;
        }

        if (lower.contains("settled") || lower.contains("restructured")) {
            r.meaning = "Settled or Restructured status → FAIL";
            r.expression = PolicyDsl.or(
                    PolicyDsl.eq(PolicyDsl.fact("bureau.settled"), true),
                    PolicyDsl.eq(PolicyDsl.fact("bureau.restructured"), true));
            r.inputs.addAll(List.of("bureau.settled", "bureau.restructured"));
            return r;
        }

        if (lower.contains("legal suit")) {
            r.meaning = "Legal suit filed → FAIL";
            r.expression = PolicyDsl.eq(PolicyDsl.fact("bureau.legal_suit"), true);
            r.inputs.add("bureau.legal_suit");
            return r;
        }

        if (lower.contains("dbt") || lower.contains("pwos") || lower.contains("lss")) {
            r.meaning = "DBT / PWOS / LSS string → FAIL; terms require vocabulary confirmation";
            r.expression = PolicyDsl.or(
                    Map.of("op", "CONTAINS_STATUS", "value", "DBT"),
                    Map.of("op", "CONTAINS_STATUS", "value", "PWOS"),
                    Map.of("op", "CONTAINS_STATUS", "value", "LSS"));
            return r;
        }

        if (lower.contains("multiple") && lower.contains("pan")) {
            r.meaning = "Multiple PAN → FAIL";
            r.expression = PolicyDsl.eq(PolicyDsl.fact("bureau.multiple_pan"), true);
            r.inputs.add("bureau.multiple_pan");
            return r;
        }

        if (lower.contains("inquir") || lower.contains("enquir")) {
            r.meaning = "Inquiries in current month (EvaluationContext clock) > 3 → FAIL";
            r.period = "CURRENT_MONTH_EVAL_CLOCK";
            r.expression = PolicyDsl.gt(PolicyDsl.metric("bureau.inquiries.current_month"), 3);
            r.thresholds.put("inquiries", Map.of("op", "GT", "value", 3));
            r.inputs.add("bureau.inquiries.current_month");
            return r;
        }

        if (lower.contains("account sold")) {
            r.meaning = "Account Sold → FAIL";
            r.expression = PolicyDsl.eq(PolicyDsl.fact("bureau.account_sold"), true);
            r.inputs.add("bureau.account_sold");
            return r;
        }

        r.meaning = "Bureau clause: " + text;
        r.expression = Map.of("op", "HARD", "source", text);
        return r;
    }

    private Interp interpretOverdueChild(CiPolicyClause clause, String lower, Interp r) {
        r.type = ClauseType.EXCEPTION.name();
        String num = clause.getClauseNumber();
        switch (num == null ? "" : num) {
            case "1" -> {
                r.meaning = "Overdue reporting date > 1 year from application date";
                r.expression = PolicyDsl.gt(PolicyDsl.metric("bureau.overdue.age_months"), 12);
                r.inputs.add("bureau.overdue.age_months");
            }
            case "2" -> {
                r.meaning = "Customer took new loans after overdue reporting date";
                r.expression = PolicyDsl.eq(PolicyDsl.metric("bureau.credit_after_overdue.exists"), true);
                r.inputs.add("bureau.credit_after_overdue.exists");
            }
            case "3" -> {
                r.meaning = "New loans have >= 6 months CLEAN string history";
                r.expression = PolicyDsl.gte(PolicyDsl.metric("bureau.credit_after_overdue.clean_history_months"), 6);
                r.inputs.add("bureau.credit_after_overdue.clean_history_months");
                r.thresholds.put("cleanMonths", 6);
            }
            case "4" -> {
                r.meaning = "Overdue amount < 1500";
                r.expression = PolicyDsl.lt(PolicyDsl.metric("bureau.overdue.amount"), 1500);
                r.inputs.add("bureau.overdue.amount");
                r.thresholds.put("maxOverdue", 1500);
            }
            default -> r.meaning = "Overdue child: " + clause.getSourceText();
        }
        return r;
    }

    /**
     * KYC-3 golden interpretation — existing Policy DSL only.
     * Evaluates normalized facts; never CALL_PROVIDER / RUN_VKYC.
     */
    private Interp interpretKyc(CiPolicyClause clause, String text) {
        Interp r = new Interp();
        r.type = clause.getClauseType() == null ? ClauseType.HARD_RULE.name() : clause.getClauseType();
        r.period = "POINT_IN_TIME";
        r.breakdown.put("clauseExtraction", 0.92);
        r.breakdown.put("clauseClassification", 0.90);
        r.breakdown.put("overall", 0.86);
        r.outputs.add("PASS_REFER_FAIL_MISSING");

        KycPolicyAuthoringSupport.Classification c = KycPolicyAuthoringSupport.classify(text);
        if (!c.kycRelated()) {
            r.meaning = "Non-KYC clause in KYC document: " + text;
            r.expression = Map.of("op", "UNKNOWN", "source", text);
            r.breakdown.put("overall", 0.40);
            return r;
        }
        r.meaning = c.naturalLanguageMeaning();
        r.expression = c.dslExpression() == null ? Map.of() : new LinkedHashMap<>(c.dslExpression());
        r.inputs.addAll(c.factPaths());
        r.inputs.addAll(c.applicationFields());
        if (c.scopeHints().get("borrowerTypes") instanceof List<?> bt) {
            r.products.addAll(bt);
        }
        if (c.scopeHints().get("requestedAmount") instanceof Map<?, ?> amt) {
            r.thresholds.put("requestedAmount", amt.get("value"));
        }
        if (c.unsupportedCapability()) {
            r.breakdown.put("overall", 0.55);
            r.outputs.clear();
            r.outputs.add("DATA_SOURCE_REQUIRED");
        }
        if (c.matchCapabilityMissing()) {
            r.breakdown.put("overall", 0.60);
            r.outputs.add("MATCH_CAPABILITY_REQUIRED");
        }
        return r;
    }

    private static final class Interp {
        String meaning;
        String type;
        String period;
        Map<String, Object> expression = new LinkedHashMap<>();
        List<Object> inputs = new ArrayList<>();
        List<Object> outputs = new ArrayList<>();
        List<Object> products = new ArrayList<>();
        Map<String, Object> thresholds = new LinkedHashMap<>();
        Map<String, Object> breakdown = new LinkedHashMap<>();
    }
}
