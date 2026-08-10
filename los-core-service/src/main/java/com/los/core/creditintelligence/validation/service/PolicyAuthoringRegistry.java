package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycFactCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable registry of facts/metrics/recons/operators for AI Policy Studio.
 * Extended in P0 with unit, period, operators, availability for Banking/Bureau BRE paths.
 * KYC-2: includes normalized KYC facts for future Decision Policy (not production authority).
 */
@Component
public class PolicyAuthoringRegistry {

    public static final String SCHEMA_VERSION = "POLICY_AUTHORING_REGISTRY_V2";

    public Map<String, Object> registry() {
        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("schemaVersion", SCHEMA_VERSION);
        reg.put("purpose", "Vocabulary for AI Policy Studio — mapping candidates only; never auto-finalizes");
        reg.put("facts", facts());
        reg.put("metrics", metrics());
        reg.put("reconciliations", List.of(
                "XSRC_GST_ITR_TURNOVER",
                "XSRC_GST_BANK_TURNOVER",
                "XSRC_ITR_BANK_TURNOVER",
                "XSRC_BUREAU_BANK_OBLIGATION",
                "XSRC_ITR_26AS_TDS",
                "TURNOVER_TRIANGULATION"
        ));
        reg.put("operators", List.of(
                "EQ", "NE", "LT", "LTE", "GT", "GTE", "BETWEEN", "IN", "NOT_IN",
                "VARIANCE_PCT_LTE", "EXISTS", "MISSING", "IS_MISSING", "AND", "OR", "NOT", "IF",
                "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "COUNT", "SUM", "AVERAGE", "MIN", "MAX"
        ));
        reg.put("outcomes", List.of(
                "APPROVE", "REFER", "REJECT", "DATA_INSUFFICIENT", "PASS", "FAIL", "MISSING_INFORMATION"
        ));
        reg.put("classifications", List.of(
                "VERIFIED", "DERIVED", "RECONCILED", "MANUAL", "DEFAULTED", "DATA_INSUFFICIENT"
        ));
        reg.put("periodFunctions", List.of(
                "TRAILING_12M", "TRAILING_3M", "TRAILING_6M", "FY", "AY",
                "CALENDAR_MONTH", "CURRENT_MONTH_EVAL_CLOCK", "POINT_IN_TIME"
        ));
        reg.put("productScopes", List.of(
                "UNSECURED_WORKING_CAPITAL", "SCF", "TERM_LOAN", "PLATFORM_DEFAULT",
                "STARTER", "DIGILEAP", "SMART_SWITCH", "REBOOST", "ALL"
        ));
        reg.put("decisionDomains", List.of(
                DecisionPolicyDomain.KYC.name(),
                DecisionPolicyDomain.ELIGIBILITY.name(),
                DecisionPolicyDomain.CREDIT.name(),
                DecisionPolicyDomain.RISK_SCORE.name(),
                DecisionPolicyDomain.LIMIT.name(),
                DecisionPolicyDomain.PRICING.name(),
                DecisionPolicyDomain.DECISION_REVIEW.name()
        ));
        reg.put("allowCanonicalAuthority", false);
        return reg;
    }

    public boolean hasCanonicalPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (Map<String, Object> m : metrics()) {
            if (path.equals(m.get("code"))) {
                return true;
            }
        }
        for (Map<String, Object> f : facts()) {
            if (path.equals(f.get("code"))) {
                return true;
            }
        }
        return "application.proposed_edi".equals(path)
                || "application.loan_amount".equals(path);
    }

    public List<Map<String, Object>> findMetricCandidates(String phrase) {
        String p = phrase == null ? "" : phrase.toLowerCase();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> m : metrics()) {
            String code = String.valueOf(m.get("code"));
            String desc = String.valueOf(m.getOrDefault("description", ""));
            if (code.toLowerCase().contains(p) || desc.toLowerCase().contains(p)
                    || synonymsMatch(m, p)) {
                hits.add(m);
            }
        }
        return hits;
    }

    private boolean synonymsMatch(Map<String, Object> m, String phrase) {
        Object syn = m.get("synonyms");
        if (!(syn instanceof List<?> list)) {
            return false;
        }
        for (Object s : list) {
            if (String.valueOf(s).toLowerCase().contains(phrase) || phrase.contains(String.valueOf(s).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> facts() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(entry("bureau.tradeline", "FACT", "Bureau tradeline", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.status_ntc", "FACT", "Canonical NTC / thin-file status", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.write_off", "FACT", "Write-off indicator", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.account_sold", "FACT", "Account sold status", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.legal_suit", "FACT", "Legal suit filed", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.settled", "FACT", "Settled status", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.restructured", "FACT", "Restructured status", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("bureau.multiple_pan", "FACT", "Multiple PAN on report", null, "POINT_IN_TIME",
                List.of("EQ", "EXISTS"), "CONSUMER_BUREAU", true));
        list.add(entry("gst.return", "FACT", "GST return", null, "POINT_IN_TIME", List.of("EXISTS"), "GST", true));
        list.add(entry("itr.return", "FACT", "ITR return", null, "POINT_IN_TIME", List.of("EXISTS"), "ITR", true));
        list.add(entry("bank.account", "FACT", "Bank account", null, "POINT_IN_TIME", List.of("EXISTS"),
                "BANK_STATEMENT", true));
        list.add(entry("bank.transaction", "FACT", "Bank transaction", "INR", "POINT_IN_TIME", List.of("EXISTS"),
                "BANK_STATEMENT", true));
        list.add(entry("form26as.entry", "FACT", "26AS entry", null, "POINT_IN_TIME", List.of("EXISTS"),
                "FORM_26AS", true));
        // KYC-2 normalized facts (Decision Policy vocabulary; not production authority)
        list.addAll(KycFactCatalog.registryEntries());
        return list;
    }

    private List<Map<String, Object>> metrics() {
        List<Map<String, Object>> list = new ArrayList<>();
        // Existing C6
        list.add(metric("gst.turnover.trailing_12m", "GST turnover T12M", "INR", "TRAILING_12M", true,
                List.of()));
        list.add(metric("itr.turnover.trailing_12m", "ITR turnover", "INR", "FY", true, List.of()));
        list.add(metric("bank.turnover.trailing_12m", "Bank turnover T12M", "INR", "TRAILING_12M", true, List.of()));
        list.add(metric("bureau.emi.monthly", "Bureau EMI", "INR", "MONTHLY", true, List.of()));
        list.add(metric("bank.emi.monthly", "Bank EMI", "INR", "MONTHLY", true, List.of()));
        list.add(metric("bureau.live_unsecured_count", "Live unsecured count", "COUNT", "POINT_IN_TIME", true, List.of()));
        list.add(metric("bank.abb.average", "Average bank balance", "INR", "TRAILING_3M", true,
                List.of("ABB", "Average Bank Balance")));
        list.add(metric("itr.income.total", "ITR income", "INR", "AY", true, List.of()));
        list.add(metric("obligation.ratio", "Obligation ratio", "PERCENT", "POINT_IN_TIME", true, List.of()));

        // Banking BRE
        list.add(metric("banking.avg_daily_balance_3m", "Average Daily Balance last 3 months", "INR", "TRAILING_3M", true,
                List.of("ADB", "Average Daily Balance", "ABB")));
        list.add(metric("banking.transaction_count.average_monthly_3m", "Average monthly transaction count 3m",
                "COUNT", "TRAILING_3M", true, List.of("Average monthly transactions", "average monthly transaction")));
        list.add(metric("banking.business_transaction_count.average_monthly_3m",
                "Business transaction count excluding transfers", "COUNT", "TRAILING_3M", true, List.of()));
        list.add(metric("banking.credit_transaction_count.average_monthly_3m",
                "Credit transaction count only", "COUNT", "TRAILING_3M", true, List.of()));
        list.add(metric("banking.settlement.avg_daily_3m", "Average Daily Settlement / QR settlement 3m",
                "INR", "TRAILING_3M", false, List.of("Average Daily Settlement", "Average Daily QR Settlement")));
        list.add(metric("banking.settlement.count_monthly_avg_3m", "Average Monthly Settlement count 3m",
                "COUNT", "TRAILING_3M", false, List.of("Average Monthly Settlement count")));
        list.add(metric("banking.inward_return.ratio_3m", "Inward cheque/ECS/ENACH return ratio 3m",
                "PERCENT", "TRAILING_3M", true, List.of("Inward Cheque Return")));
        list.add(metric("banking.inward_return.count_3m", "Inward return count 3m",
                "COUNT", "TRAILING_3M", true, List.of()));
        list.add(metric("banking.transaction_count.total_3m", "Total transactions last 3 months",
                "COUNT", "TRAILING_3M", true, List.of()));

        // Bureau BRE
        list.add(metric("bureau.score", "Bureau score", "SCORE", "POINT_IN_TIME", true, List.of("Bureau Score")));
        list.add(metric("bureau.max_dpd_6m", "Max DPD in last 6 months", "DAYS", "TRAILING_6M", true,
                List.of("DPD", "Days Past Due")));
        list.add(metric("bureau.inquiries.current_month", "Inquiries in current month (eval clock)",
                "COUNT", "CURRENT_MONTH_EVAL_CLOCK", true, List.of("inquiries", "enquiries")));
        list.add(metric("bureau.cc_overdue_amount", "Credit card overdue amount", "INR", "POINT_IN_TIME", true,
                List.of("credit card overdue")));
        list.add(metric("bureau.overdue.amount", "Loan overdue amount", "INR", "POINT_IN_TIME", true, List.of()));
        list.add(metric("bureau.overdue.age_months", "Overdue reporting age in months", "MONTHS", "POINT_IN_TIME", false,
                List.of("overdue reporting date")));
        list.add(metric("bureau.credit_after_overdue.exists", "New credit after overdue reporting date",
                "BOOLEAN", "POINT_IN_TIME", false, List.of()));
        list.add(metric("bureau.credit_after_overdue.clean_history_months",
                "Clean history months on post-overdue credit", "MONTHS", "POINT_IN_TIME", false, List.of("CLEAN")));

        // Application fields exposed as registry paths for mapping
        Map<String, Object> edi = metric("application.proposed_edi", "Proposed EDI (application field — unconfirmed)",
                "INR", "POINT_IN_TIME", true, List.of("EDI", "proposed EDI"));
        edi.put("type", "APPLICATION_FIELD");
        edi.put("availability", "CANDIDATE");
        list.add(edi);
        list.add(metric("application.loan_amount", "Proposed loan amount", "INR", "POINT_IN_TIME", true,
                List.of("loan amount")));
        return list;
    }

    private Map<String, Object> metric(String code, String description, String unit, String period,
                                       boolean available, List<String> synonyms) {
        Map<String, Object> m = entry(code, "METRIC", description, unit, period,
                List.of("EQ", "NE", "LT", "LTE", "GT", "GTE", "EXISTS"), "MULTI", available);
        m.put("synonyms", synonyms);
        return m;
    }

    private Map<String, Object> entry(String code, String type, String description, String unit, String period,
                                      List<String> operators, String source, boolean available) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("canonicalPath", code);
        m.put("type", type);
        m.put("description", description);
        m.put("unit", unit);
        m.put("periodSemantics", period);
        m.put("allowedOperators", operators);
        m.put("subject", "APPLICATION");
        m.put("sourceRequirements", List.of(source));
        m.put("classificationRequirements", List.of("VERIFIED", "DERIVED"));
        m.put("availability", available ? "AVAILABLE" : "UNAVAILABLE");
        return m;
    }
}
