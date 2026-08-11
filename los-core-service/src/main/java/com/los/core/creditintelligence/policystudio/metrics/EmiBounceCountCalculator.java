package com.los.core.creditintelligence.policystudio.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 — EMI Bounce Count executable calculation.
 * Reuses existing EMI + bounce/return classifier semantics (no second classifier).
 * Missing / unclassifiable bank data → DATA_INSUFFICIENT (never invent zero).
 */
public final class EmiBounceCountCalculator {

    public static final String METRIC_ID = "banking.emi_bounce_count_3m";
    public static final String BINDING = "EmiBounceCountCalculator.V1";
    public static final String OUTCOME_PASS = "PASS";
    public static final String OUTCOME_DI = "DATA_INSUFFICIENT";

    private EmiBounceCountCalculator() {}

    public record Txn(
            LocalDate date,
            String narration,
            String direction,
            BigDecimal amount,
            String category,
            boolean classified,
            boolean emiFlag,
            boolean bounceFlag,
            boolean returnFlag,
            String duplicateStatus
    ) {}

    public record Config(
            int periodMonths,
            String emiIdentification,
            String bounceIdentification,
            boolean excludeDuplicates
    ) {
        public static Config defaults() {
            return new Config(3, "EXISTING_EMI_CLASSIFIER", "EXISTING_BOUNCE_RETURN_CLASSIFIER", true);
        }

        public static Config fromBody(Map<String, Object> body) {
            Config d = defaults();
            if (body == null || body.isEmpty()) return d;
            int months = d.periodMonths;
            Object pm = body.get("periodMonths");
            if (pm != null) {
                try {
                    months = Math.max(1, Math.min(12, Integer.parseInt(String.valueOf(pm).trim())));
                } catch (NumberFormatException ignored) {
                    months = d.periodMonths;
                }
            }
            String emi = body.get("emiIdentification") == null
                    ? d.emiIdentification : String.valueOf(body.get("emiIdentification")).trim();
            String bounce = body.get("bounceIdentification") == null
                    ? d.bounceIdentification : String.valueOf(body.get("bounceIdentification")).trim();
            boolean excl = body.get("excludeDuplicates") == null
                    ? d.excludeDuplicates
                    : Boolean.parseBoolean(String.valueOf(body.get("excludeDuplicates")));
            if (!"EXISTING_EMI_CLASSIFIER".equalsIgnoreCase(emi)
                    && !"NARRATION_EMI_DEBIT".equalsIgnoreCase(emi)) {
                emi = d.emiIdentification;
            }
            if (!"EXISTING_BOUNCE_RETURN_CLASSIFIER".equalsIgnoreCase(bounce)
                    && !"NARRATION_RETURN_WITH_EMI".equalsIgnoreCase(bounce)) {
                bounce = d.bounceIdentification;
            }
            return new Config(months, emi.toUpperCase(Locale.ROOT), bounce.toUpperCase(Locale.ROOT), excl);
        }
    }

    public static Map<String, Object> evaluate(List<Txn> txns, Config config, LocalDate asOf) {
        Config cfg = config == null ? Config.defaults() : config;
        LocalDate end = asOf == null ? LocalDate.now() : asOf;
        LocalDate start = end.minusMonths(cfg.periodMonths()).plusDays(1);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricId", METRIC_ID);
        out.put("binding", BINDING);
        out.put("periodMonths", cfg.periodMonths());
        out.put("periodLabel", "Last " + cfg.periodMonths() + " months");
        out.put("windowStart", start.toString());
        out.put("windowEnd", end.toString());
        out.put("emiIdentification", cfg.emiIdentification());
        out.put("bounceIdentification", cfg.bounceIdentification());
        out.put("calculation",
                "Count EMI repayment events with matched return/bounce events during the period");
        out.put("missingDataTreatment",
                "DATA_INSUFFICIENT when bank transactions or classification coverage is unavailable — never invent 0");

        if (txns == null || txns.isEmpty()) {
            out.put("outcome", OUTCOME_DI);
            out.put("v", null);
            out.put("reason", "No bank transactions available");
            out.put("emiCandidates", 0);
            out.put("matchedBouncedEmiEvents", 0);
            out.put("emiBounceCount", null);
            out.put("previewRows", List.of());
            return out;
        }

        List<Txn> inWindow = new ArrayList<>();
        for (Txn t : txns) {
            if (t == null || t.date() == null) continue;
            if (cfg.excludeDuplicates() && t.duplicateStatus() != null
                    && "DUPLICATE".equalsIgnoreCase(t.duplicateStatus())) {
                continue;
            }
            if (!t.date().isBefore(start) && !t.date().isAfter(end)) {
                inWindow.add(t);
            }
        }
        if (inWindow.isEmpty()) {
            out.put("outcome", OUTCOME_DI);
            out.put("v", null);
            out.put("reason", "No transactions in calculation window");
            out.put("emiCandidates", 0);
            out.put("matchedBouncedEmiEvents", 0);
            out.put("emiBounceCount", null);
            out.put("previewRows", List.of());
            return out;
        }

        long classified = inWindow.stream().filter(Txn::classified).count();
        if (classified * 2 < inWindow.size()) {
            out.put("outcome", OUTCOME_DI);
            out.put("v", null);
            out.put("reason", "Classification coverage insufficient for EMI bounce calculation");
            out.put("emiCandidates", 0);
            out.put("matchedBouncedEmiEvents", 0);
            out.put("emiBounceCount", null);
            out.put("previewRows", List.of());
            out.put("classificationCoverage", classified + "/" + inWindow.size());
            return out;
        }

        List<Txn> emiCandidates = inWindow.stream().filter(t -> isEmiCandidate(t, cfg)).toList();
        List<Map<String, Object>> matched = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (Txn t : inWindow) {
            if (!isEmiBounceEvent(t, cfg)) continue;
            String key = dedupeKey(t);
            if (cfg.excludeDuplicates() && !dedupe.add(key)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", t.date().toString());
            row.put("narration", t.narration());
            row.put("amount", t.amount());
            row.put("category", t.category());
            row.put("direction", t.direction());
            row.put("matchReason", matchReason(t));
            matched.add(row);
        }

        int count = matched.size();
        out.put("outcome", OUTCOME_PASS);
        out.put("v", count);
        out.put("emiCandidates", emiCandidates.size());
        out.put("matchedBouncedEmiEvents", count);
        out.put("emiBounceCount", count);
        out.put("previewRows", matched);
        out.put("dataQualityStatus", "OK");
        out.put("reason", null);
        return out;
    }

    /**
     * Staging fixture (hand-reconcilable):
     * 5 EMI repayment attempts (3 successful + 2 that later bounce),
     * 2 qualifying bounced/returned EMI events (+ 1 duplicate ignored),
     * 1 non-EMI cheque return ignored → count = 2.
     */
    public static List<Txn> stagingFixture() {
        LocalDate asOf = LocalDate.of(2026, 8, 1);
        List<Txn> txns = new ArrayList<>();
        // Successful EMI debits (candidates, not bounces)
        txns.add(txn(asOf.minusDays(10), "HDFC BANK EMI 1234", "DEBIT", "5000", "EMI", true, true, false, false));
        txns.add(txn(asOf.minusDays(20), "BAJAJ FINSERV EMI", "DEBIT", "3200", "EMI", true, true, false, false));
        txns.add(txn(asOf.minusDays(40), "EQUATED MONTHLY INSTALLMENT HOME", "DEBIT", "15000", "EMI", true, true, false, false));
        // EMI repayment attempts that bounce (still EMI candidates)
        txns.add(txn(asOf.minusDays(13), "HDFC BANK EMI 999", "DEBIT", "5000", "EMI", true, true, false, false));
        txns.add(txn(asOf.minusDays(36), "BAJAJ FINSERV EMI LOAN", "DEBIT", "3200", "EMI", true, true, false, false));
        // Bounced EMI returns (count these — 2)
        txns.add(txn(asOf.minusDays(12), "NACH RETURN EMI HDFC BANK EMI 999", "DEBIT", "5000",
                "NACH_RETURN", true, false, true, true));
        txns.add(txn(asOf.minusDays(35), "ECS RETURN LOAN EMI BAJAJ", "DEBIT", "3200",
                "NACH_RETURN", true, false, true, true));
        // Duplicate of first bounce — must not double-count
        txns.add(txn(asOf.minusDays(12), "NACH RETURN EMI HDFC BANK EMI 999", "DEBIT", "5000",
                "NACH_RETURN", true, false, true, true, "DUPLICATE"));
        // Unrelated cheque return (not EMI) — must not count
        txns.add(txn(asOf.minusDays(15), "CHEQUE RETURN CUSTOMER XYZ", "DEBIT", "1000",
                "CHEQUE_RETURN", true, false, true, true));
        // Noise credit
        txns.add(txn(asOf.minusDays(5), "UPI/CR/MERCHANT", "CREDIT", "2000", "OTHER", true, false, false, false));
        return txns;
    }

    private static Txn txn(
            LocalDate d, String n, String dir, String amt, String cat,
            boolean classified, boolean emi, boolean bounce, boolean ret) {
        return txn(d, n, dir, amt, cat, classified, emi, bounce, ret, null);
    }

    private static Txn txn(
            LocalDate d, String n, String dir, String amt, String cat,
            boolean classified, boolean emi, boolean bounce, boolean ret, String dup) {
        return new Txn(d, n, dir, new BigDecimal(amt), cat, classified, emi, bounce, ret, dup);
    }

    static boolean isEmiCandidate(Txn t, Config cfg) {
        if (t == null) return false;
        String n = norm(t.narration());
        String c = norm(t.category());
        boolean debit = t.direction() == null || "DEBIT".equalsIgnoreCase(t.direction());
        if ("NARRATION_EMI_DEBIT".equals(cfg.emiIdentification())) {
            return debit && n.contains("EMI");
        }
        // EXISTING_EMI_CLASSIFIER
        if (t.emiFlag() || c.equals("EMI") || c.contains("EMI")) return debit;
        return debit && n.contains("EMI") && !isReturnCategory(c) && !t.bounceFlag() && !t.returnFlag();
    }

    static boolean isEmiBounceEvent(Txn t, Config cfg) {
        if (t == null) return false;
        String n = norm(t.narration());
        String c = norm(t.category());
        boolean bounceLike = t.bounceFlag() || t.returnFlag() || isReturnCategory(c)
                || n.contains("BOUNCE") || n.contains("RETURN");
        if (!bounceLike) return false;
        // Must be EMI-related: narration mentions EMI, or category pairing with EMI wording
        boolean emiRelated = n.contains("EMI") || n.contains("EQUATED MONTHLY") || n.contains("INSTALLMENT");
        if ("NARRATION_RETURN_WITH_EMI".equals(cfg.bounceIdentification())) {
            return emiRelated;
        }
        // EXISTING_BOUNCE_RETURN_CLASSIFIER — bounce/return classifier hit + EMI wording
        return emiRelated;
    }

    private static boolean isReturnCategory(String c) {
        return c.contains("NACH_RETURN") || c.contains("CHEQUE_RETURN") || c.contains("RETURN");
    }

    private static String matchReason(Txn t) {
        String c = norm(t.category());
        if (c.contains("NACH_RETURN")) return "NACH/ECS return with EMI narration (existing bounce classifier)";
        if (c.contains("CHEQUE_RETURN")) return "Cheque return with EMI narration (existing bounce classifier)";
        if (t.bounceFlag() || t.returnFlag()) return "Bounce/return flag + EMI narration";
        return "Return/bounce narration with EMI";
    }

    private static String dedupeKey(Txn t) {
        return Objects.toString(t.date(), "") + "|"
                + norm(t.narration()) + "|"
                + (t.amount() == null ? "" : t.amount().toPlainString()) + "|"
                + norm(t.category());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
