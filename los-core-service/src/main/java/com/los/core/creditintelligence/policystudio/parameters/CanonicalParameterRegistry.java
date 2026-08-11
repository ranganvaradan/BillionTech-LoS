package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * POLICY-CONVERGENCE-1 — unified READ MODEL facade over existing registries/helpers.
 * Search order for ingestion: exact → alias → derived → capability → live rule/scorecard.
 */
public class CanonicalParameterRegistry {

    private final List<CanonicalParameterDefinition> all;

    public CanonicalParameterRegistry() {
        this.all = seed();
    }

    public List<CanonicalParameterDefinition> all() {
        return List.copyOf(all);
    }

    public Optional<CanonicalParameterDefinition> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String key = id.trim();
        return all.stream().filter(p -> key.equalsIgnoreCase(p.id())
                || key.equalsIgnoreCase(p.liveRuleParameter())
                || key.equalsIgnoreCase(p.liveScorecardParameter())).findFirst();
    }

    /**
     * Ingestion search: exact known parameter, alias/synonym, derived, capability/live vocab.
     */
    public Optional<CanonicalParameterDefinition> resolve(String phrase) {
        if (phrase == null || phrase.isBlank()) return Optional.empty();
        String q = phrase.trim().toLowerCase(Locale.ROOT);

        for (CanonicalParameterDefinition p : all) {
            if (p.id() != null && q.equals(p.id().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.businessName() != null && q.equals(p.businessName().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.liveRuleParameter() != null && q.equals(p.liveRuleParameter().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
            if (p.liveScorecardParameter() != null
                    && q.equals(p.liveScorecardParameter().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.aliases() == null) continue;
            for (String a : p.aliases()) {
                if (a != null && q.equals(a.toLowerCase(Locale.ROOT))) {
                    return Optional.of(p);
                }
            }
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.aliases() == null) continue;
            for (String a : p.aliases()) {
                if (a != null && !a.isBlank() && q.contains(a.toLowerCase(Locale.ROOT))) {
                    return Optional.of(p);
                }
            }
            if (p.businessName() != null && q.contains(p.businessName().toLowerCase(Locale.ROOT))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public List<CanonicalParameterDefinition> searchCompatibleCleanDefinitions() {
        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (CanonicalParameterDefinition p : all) {
            String id = p.id() == null ? "" : p.id().toLowerCase(Locale.ROOT);
            String name = p.businessName() == null ? "" : p.businessName().toLowerCase(Locale.ROOT);
            if (id.contains("clean") || name.contains("clean history") || name.contains("repayment history")) {
                out.add(p);
            }
        }
        return out;
    }

    public Map<String, Object> catalogueView() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", all.size());
        out.put("parameters", all.stream().map(CanonicalParameterDefinition::toBusinessView).toList());
        out.put("sources", sources());
        out.put("readModelOnly", true);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /** Distinct business sources for resolver Source dropdown. */
    public List<String> sources() {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        for (String s : List.of(
                "Application", "Bureau", "Bank Statement", "GST", "Financial Statements",
                "KYC", "Program / Product", "Customer / Borrower", "Manual Input", "Computed / Derived")) {
            ordered.put(s, Boolean.FALSE);
        }
        for (CanonicalParameterDefinition p : all) {
            if (p.evaluatedFrom() != null && !p.evaluatedFrom().isBlank()) {
                ordered.putIfAbsent(p.evaluatedFrom(), Boolean.TRUE);
            }
        }
        return new ArrayList<>(ordered.keySet());
    }

    /** Browse RAW then DERIVED for a source — only known registry entries. */
    public Map<String, Object> browseBySource(String source) {
        String src = source == null ? "" : source.trim();
        List<Map<String, Object>> raw = new ArrayList<>();
        List<Map<String, Object>> derived = new ArrayList<>();
        List<Map<String, Object>> manual = new ArrayList<>();
        for (CanonicalParameterDefinition p : all) {
            if (!sourceMatches(p.evaluatedFrom(), src)) continue;
            Map<String, Object> view = p.toBusinessView();
            if (CanonicalParameterDefinition.RAW.equals(p.type())) raw.add(view);
            else if (CanonicalParameterDefinition.MANUAL.equals(p.type())) manual.add(view);
            else derived.add(view);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", src);
        out.put("raw", raw);
        out.put("derived", derived);
        out.put("manual", manual);
        out.put("count", raw.size() + derived.size() + manual.size());
        out.put("invented", false);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /**
     * Business-language search across names, aliases, sources, live rule/scorecard vocab.
     * Does not manufacture new parameters.
     */
    public Map<String, Object> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> hits = new ArrayList<>();
        if (!q.isBlank()) {
            for (CanonicalParameterDefinition p : all) {
                int score = matchScore(p, q);
                if (score <= 0) continue;
                Map<String, Object> row = new LinkedHashMap<>(p.toBusinessView());
                row.put("matchScore", score);
                hits.add(row);
            }
            hits.sort((a, b) -> Integer.compare(
                    ((Number) b.getOrDefault("matchScore", 0)).intValue(),
                    ((Number) a.getOrDefault("matchScore", 0)).intValue()));
        }
        // Deduplicate by id
        LinkedHashMap<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> h : hits) {
            dedup.putIfAbsent(String.valueOf(h.get("id")), h);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("count", dedup.size());
        out.put("results", new ArrayList<>(dedup.values()));
        out.put("createsParameter", false);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    private static boolean sourceMatches(String evaluatedFrom, String selected) {
        if (selected == null || selected.isBlank()) return true;
        if (evaluatedFrom == null) return false;
        return evaluatedFrom.equalsIgnoreCase(selected.trim())
                || evaluatedFrom.toLowerCase(Locale.ROOT).contains(selected.trim().toLowerCase(Locale.ROOT));
    }

    private static int matchScore(CanonicalParameterDefinition p, String q) {
        int score = 0;
        if (p.id() != null && p.id().toLowerCase(Locale.ROOT).contains(q)) score = Math.max(score, 80);
        if (p.businessName() != null) {
            String n = p.businessName().toLowerCase(Locale.ROOT);
            if (n.equals(q)) score = Math.max(score, 100);
            else if (n.contains(q) || q.contains(n)) score = Math.max(score, 90);
        }
        if (p.evaluatedFrom() != null && p.evaluatedFrom().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 40);
        }
        if (p.liveRuleParameter() != null && p.liveRuleParameter().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 85);
        }
        if (p.liveScorecardParameter() != null
                && p.liveScorecardParameter().toLowerCase(Locale.ROOT).contains(q)) {
            score = Math.max(score, 85);
        }
        if (p.aliases() != null) {
            for (String a : p.aliases()) {
                if (a == null) continue;
                String al = a.toLowerCase(Locale.ROOT);
                if (al.equals(q)) score = Math.max(score, 98);
                else if (al.contains(q) || q.contains(al)) score = Math.max(score, 92);
            }
        }
        return score;
    }

    private static List<CanonicalParameterDefinition> seed() {
        List<CanonicalParameterDefinition> p = new ArrayList<>();
        // RAW bureau primitives (browse)
        p.add(def("bureau.tradeline.payment_history", "Payment history", "Bureau",
                CanonicalParameterDefinition.RAW, "HISTORY", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Bureau tradeline payment history",
                List.of("payment history", "tradeline history"), null, null));
        p.add(def("bureau.tradeline.account_open_date", "Account open date", "Bureau",
                CanonicalParameterDefinition.RAW, "DATE", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Bureau tradeline open date",
                List.of("account open date", "tradeline open date"), null, null));
        p.add(def("bureau.inquiry", "Bureau enquiry event", "Bureau",
                CanonicalParameterDefinition.RAW, "EVENT", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Bureau enquiry record",
                List.of("enquiry event", "inquiry event"), null, null));
        p.add(def("bureau.score", "Bureau score", "Bureau", CanonicalParameterDefinition.RAW,
                "SCORE", null, "AVAILABLE_AUTOMATICALLY", null, List.of(),
                "BureauMetricService / CreditControl bureauScore",
                List.of("cibil score", "credit bureau score", "bureau score", "credit score", "cibil"),
                "BUREAU_SCORE", "BUREAU_SCORE"));
        p.add(def("bureau.max_dpd_6m", "Maximum DPD (6 months)", "Bureau", CanonicalParameterDefinition.DERIVED,
                "DAYS", "TRAILING_6M", "AVAILABLE_AUTOMATICALLY",
                "Highest days-past-due observed on tradelines in trailing 6 months",
                List.of("bureau.tradeline.payment_history"),
                "PolicyBureauMetricService.maxDpd6m / BureauMetricService",
                List.of("max dpd", "days past due", "dpd last 6 months"),
                null, null));
        p.add(def("bureau.inquiries.current_month", "Bureau enquiries (current month)", "Bureau",
                CanonicalParameterDefinition.DERIVED, "COUNT", "CURRENT_MONTH", "AVAILABLE_AUTOMATICALLY",
                "Count of bureau enquiries in the current evaluation month",
                List.of("bureau.inquiry"),
                "PolicyBureauMetricService.inquiriesCurrentMonth",
                List.of("enquiries", "inquiries", "bureau enquiry"),
                null, null));
        p.add(def("bureau.live_unsecured_loan_count", "Live unsecured loans", "Bureau",
                CanonicalParameterDefinition.DERIVED, "COUNT", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Count of live unsecured tradelines",
                List.of("bureau.tradeline"),
                "BureauMetricService.computeLiveUnsecured",
                List.of("live unsecured", "unsecured loan count"),
                "LIVE_UNSECURED_LOAN_COUNT", null));
        p.add(def("bureau.cc_overdue_amount", "Credit-card overdue amount", "Bureau",
                CanonicalParameterDefinition.DERIVED, "INR", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Sum of credit-card overdue amounts",
                List.of("bureau.tradeline"),
                "PolicyBureauMetricService",
                List.of("credit card overdue", "cc overdue"),
                null, null));
        p.add(def("bureau.overdue.age_months", "Overdue age (months)", "Bureau",
                CanonicalParameterDefinition.DERIVED, "MONTHS", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Age in months of the overdue since it started",
                List.of("bureau.tradeline"),
                "PolicyBureauMetricService",
                List.of("overdue age", "overdue older than"),
                null, null));
        p.add(def("bureau.overdue.amount", "Overdue amount", "Bureau",
                CanonicalParameterDefinition.DERIVED, "INR", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Current overdue amount on qualifying tradelines",
                List.of("bureau.tradeline"),
                "PolicyBureauMetricService",
                List.of("overdue amount", "overdue below"),
                null, null));
        p.add(def("bureau.credit_after_overdue.exists", "New credit after overdue", "Bureau",
                CanonicalParameterDefinition.DERIVED, "BOOLEAN", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Whether a new credit facility was opened after the overdue event",
                List.of("bureau.tradeline.account_open_date"),
                "PolicyBureauMetricService.creditAfterOverdueExists",
                List.of("new credit after overdue", "credit after overdue"),
                null, null));
        p.add(def("bureau.credit_after_overdue.clean_history_months", "Clean history months (post-overdue)",
                "Bureau", CanonicalParameterDefinition.DERIVED, "MONTHS", "CUSTOMER_DEFINED",
                "NEEDS_CONFIGURATION",
                "Months of clean credit history after overdue — definition must be confirmed by Credit Manager",
                List.of("bureau.tradeline.payment_history", "bureau.max_dpd"),
                "PolicyBureauMetricService.cleanHistoryMonths (vocabulary-gated)",
                List.of("clean history", "clean credit history", "clean string", "6 months clean"),
                null, "REPAYMENT_HISTORY"));
        // RAW bank primitives (browse)
        p.add(def("bank.transaction.amount", "Transaction amount", "Bank Statement",
                CanonicalParameterDefinition.RAW, "INR", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Bank transaction amount",
                List.of("txn amount", "transaction amount"), null, null));
        p.add(def("bank.transaction.date", "Transaction date", "Bank Statement",
                CanonicalParameterDefinition.RAW, "DATE", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Bank transaction date",
                List.of("txn date", "transaction date"), null, null));
        p.add(def("bank.account.closing_balance", "Closing balance", "Bank Statement",
                CanonicalParameterDefinition.RAW, "INR", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "End-of-day / closing balance",
                List.of("closing balance", "eod balance"), null, null));
        p.add(def("bank.transaction.credit_debit", "Credit/debit indicator", "Bank Statement",
                CanonicalParameterDefinition.RAW, "FLAG", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Credit vs debit flag",
                List.of("credit debit", "dr cr"), null, null));
        p.add(def("bank.transaction.classification", "Transaction classification", "Bank Statement",
                CanonicalParameterDefinition.RAW, "CODE", null, "AVAILABLE_AUTOMATICALLY",
                null, List.of(), "Transaction classification / category",
                List.of("txn classification", "transaction category"), null, null));
        p.add(def("banking.monthly_credits_3m", "Monthly credits", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "INR", "TRAILING_3M", "DERIVABLE_FROM_AVAILABLE_DATA",
                "Sum of credit transactions over trailing months",
                List.of("bank.transaction"),
                "PolicyBankingMetricService",
                List.of("monthly credits", "credit sum"), null, null));
        p.add(def("banking.cheque_return_count_3m", "Cheque return count", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "COUNT", "TRAILING_3M", "AVAILABLE_AUTOMATICALLY",
                "Count of cheque/ECS returns in trailing 3 months",
                List.of("bank.transaction", "CHEQUE_RETURN"),
                "PolicyBankingMetricService",
                List.of("cheque return count", "bounce count"), null, null));
        p.add(def("banking.settlement.count_monthly_avg_3m", "Average monthly settlements", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "COUNT", "TRAILING_3M", "DERIVABLE_FROM_AVAILABLE_DATA",
                "Number of qualifying QR settlement credits during the trailing 3 months ÷ 3",
                List.of("bank.transaction", "QR_SETTLEMENT"),
                "banking.qr_settlement.average_monthly_count_3m",
                List.of("monthly settlements", "settlement count", "number of settlements", "average monthly settlements"),
                null, null));
        p.add(def("banking.settlement.avg_daily_3m", "Average daily settlements", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "INR", "TRAILING_3M", "DERIVABLE_FROM_AVAILABLE_DATA",
                "Sum of qualifying QR settlement credit amounts during trailing 3 months ÷ 90",
                List.of("bank.transaction", "QR_SETTLEMENT"),
                "banking.qr_settlement.average_daily_3m",
                List.of("daily settlements", "average daily settlement"),
                null, null));
        p.add(def("banking.avg_daily_balance_3m", "Average daily balance", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "INR", "TRAILING_3M", "AVAILABLE_AUTOMATICALLY",
                "End-of-day balance carry-forward average over trailing 3 months",
                List.of("bank.transaction", "bank.account"),
                "BankingMetricService / BankAverageDailyBalanceCalculator",
                List.of("adb", "average daily balance", "avg daily balance"),
                null, "AVERAGE_BANK_BALANCE"));
        p.add(def("banking.transaction_count.average_monthly_3m", "Average monthly transactions", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "COUNT", "TRAILING_3M", "DERIVABLE_FROM_AVAILABLE_DATA",
                "Total qualifying transactions in trailing 3 months ÷ 3",
                List.of("bank.transaction"),
                "PolicyBankingMetricService.transactionCount3m",
                List.of("monthly transactions", "average monthly transaction"),
                null, null));
        p.add(def("banking.inward_return.ratio_3m", "Inward return ratio", "Bank Statement",
                CanonicalParameterDefinition.DERIVED, "PERCENT", "TRAILING_3M", "AVAILABLE_AUTOMATICALLY",
                "Inward cheque/ECS/ENACH returns as a percentage of transactions",
                List.of("bank.transaction", "CHEQUE_RETURN", "NACH_RETURN"),
                "PolicyBankingMetricService.inwardChequeReturn",
                List.of("inward return", "inward cheque return", "ecs return"),
                "CHEQUE_BOUNCES_3M", null));
        p.add(def("gst.turnover.trailing_12m", "GST turnover (12 months)", "GST",
                CanonicalParameterDefinition.DERIVED, "INR", "TRAILING_12M", "AVAILABLE_AUTOMATICALLY",
                "Trailing 12-month GST turnover",
                List.of("gst.return"),
                "GstMetricService.computeTrailingTurnover",
                List.of("gst turnover", "annual gst turnover"),
                "ANNUAL_GST_TURNOVER", "GST_INCOME"));
        p.add(def("obligation.ratio", "Obligation ratio (FOIR)", "Computed / Derived",
                CanonicalParameterDefinition.DERIVED, "PERCENT", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Monthly obligations ÷ monthly income × 100",
                List.of("income", "emi"),
                "CreditControlService / PolicyRegistryMetricService.computeFoir",
                List.of("foir", "obligation ratio", "dti"),
                "OBLIGATION_RATIO", "OBLIGATION_RATIO"));
        p.add(def("application.proposed_edi", "Proposed EDI", "Application",
                CanonicalParameterDefinition.MANUAL, "INR", null, "MANUAL_INPUT_AVAILABLE",
                null, List.of("application"),
                "Application / Manual Input",
                List.of("edi", "proposed edi", "equated daily instalment"),
                null, null));
        p.add(def("kyc.quality", "KYC quality", "KYC",
                CanonicalParameterDefinition.DERIVED, "FLAG", "PIT", "AVAILABLE_AUTOMATICALLY",
                "Pass/fail from KYC outcome",
                List.of("kyc"),
                "CreditControlService KYC_QUALITY",
                List.of("kyc", "kyc pass", "kyc quality"),
                "KYC_PASS", "KYC_QUALITY"));
        return p;
    }

    private static CanonicalParameterDefinition def(
            String id, String name, String from, String type, String unit, String period,
            String availability, String calc, List<String> primitives, String binding,
            List<String> aliases, String liveRule, String liveScorecard) {
        return new CanonicalParameterDefinition(
                id, name, from, type, unit, period, availability, calc,
                primitives == null ? List.of() : List.copyOf(primitives),
                binding,
                aliases == null ? List.of() : List.copyOf(aliases),
                liveRule, liveScorecard);
    }
}
