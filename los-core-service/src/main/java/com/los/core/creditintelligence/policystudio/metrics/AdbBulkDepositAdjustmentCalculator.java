package com.los.core.creditintelligence.policystudio.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * BANKING-BRE-FINAL-CLOSURE-1 — policy-scoped ADB bulk-deposit &gt;N× adjustment.
 * Reuses existing credit / loan / gaming classifier categories (no new taxonomy).
 * Strict {@code >} threshold (policy wording: "more than 10 times").
 * Adjusted ADB via EOD balance reconstruction with excluded credits removed — does not mutate
 * global {@code banking.avg_daily_balance_3m} catalogue definition.
 */
public final class AdbBulkDepositAdjustmentCalculator {

    public static final String ADJUSTMENT_ID = "banking.adb_bulk_deposit_adjustment";
    public static final String AFFECTED_METRIC = "banking.avg_daily_balance_3m";
    public static final String ADJUSTED_METRIC = "BANK_POLICY_ADJUSTED_ADB_3M";
    public static final String BINDING = "AdbBulkDepositAdjustmentCalculator.V1";
    public static final String OUTCOME_PASS = "PASS";
    public static final String OUTCOME_DI = "DATA_INSUFFICIENT";

    private AdbBulkDepositAdjustmentCalculator() {}

    public record Txn(
            LocalDate date,
            String narration,
            String direction,
            BigDecimal amount,
            String category,
            boolean classified,
            BigDecimal balanceAfter,
            String duplicateStatus
    ) {}

    public record Config(
            int periodMonths,
            BigDecimal multiple,
            boolean strictGreaterThan,
            boolean excludeLoanDisbursements,
            boolean excludeOnlineGaming,
            boolean excludeDuplicates
    ) {
        public static Config defaults() {
            return new Config(3, new BigDecimal("10"), true, true, true, true);
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
            BigDecimal mult = d.multiple;
            Object m = body.get("multiple");
            if (m == null) m = body.get("multipleOfAverageDeposits");
            if (m != null) {
                try {
                    mult = new BigDecimal(String.valueOf(m).trim().replace("×", "").replace("x", ""));
                    if (mult.compareTo(BigDecimal.ONE) < 0) mult = d.multiple;
                } catch (Exception ignored) {
                    mult = d.multiple;
                }
            }
            boolean strict = body.get("strictGreaterThan") == null
                    ? d.strictGreaterThan
                    : Boolean.parseBoolean(String.valueOf(body.get("strictGreaterThan")));
            boolean exclLoan = body.get("excludeLoanDisbursements") == null
                    ? d.excludeLoanDisbursements
                    : Boolean.parseBoolean(String.valueOf(body.get("excludeLoanDisbursements")));
            boolean exclGame = body.get("excludeOnlineGaming") == null
                    ? d.excludeOnlineGaming
                    : Boolean.parseBoolean(String.valueOf(body.get("excludeOnlineGaming")));
            boolean exclDup = body.get("excludeDuplicates") == null
                    ? d.excludeDuplicates
                    : Boolean.parseBoolean(String.valueOf(body.get("excludeDuplicates")));
            return new Config(months, mult, strict, exclLoan, exclGame, exclDup);
        }
    }

    public static Map<String, Object> evaluate(List<Txn> txns, Config config, LocalDate asOf) {
        Config cfg = config == null ? Config.defaults() : config;
        LocalDate end = asOf == null ? LocalDate.now() : asOf;
        LocalDate start = end.minusMonths(cfg.periodMonths()).plusDays(1);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("adjustmentId", ADJUSTMENT_ID);
        out.put("affectedMetric", AFFECTED_METRIC);
        out.put("adjustedMetric", ADJUSTED_METRIC);
        out.put("binding", BINDING);
        out.put("periodMonths", cfg.periodMonths());
        out.put("periodLabel", "Last " + cfg.periodMonths() + " months");
        out.put("windowStart", start.toString());
        out.put("windowEnd", end.toString());
        out.put("multiple", cfg.multiple());
        out.put("comparison", cfg.strictGreaterThan() ? ">" : ">=");
        out.put("depositPopulation", "Qualifying merchant credit deposits (existing classifiers)");
        out.put("existingExclusions", List.of(
                Map.of("name", "Loan disbursements", "applied", cfg.excludeLoanDisbursements()),
                Map.of("name", "Online gaming credits", "applied", cfg.excludeOnlineGaming())));
        out.put("missingDataTreatment",
                "DATA_INSUFFICIENT when bank balances/classification unavailable — never invent 0");

        if (txns == null || txns.isEmpty()) {
            return di(out, "No bank transactions available");
        }

        List<Txn> inWindow = new ArrayList<>();
        for (Txn t : txns) {
            if (t == null || t.date() == null) continue;
            if (cfg.excludeDuplicates() && "DUPLICATE".equalsIgnoreCase(t.duplicateStatus())) continue;
            if (!t.date().isBefore(start) && !t.date().isAfter(end)) inWindow.add(t);
        }
        if (inWindow.isEmpty()) {
            return di(out, "No transactions in calculation window");
        }

        long classified = inWindow.stream().filter(Txn::classified).count();
        if (classified * 2 < inWindow.size()) {
            out.put("classificationCoverage", classified + "/" + inWindow.size());
            return di(out, "Classification coverage insufficient for ADB bulk adjustment");
        }

        List<Txn> qualifyingDeposits = new ArrayList<>();
        for (Txn t : inWindow) {
            if (!isCredit(t) || t.amount() == null) continue;
            if (cfg.excludeLoanDisbursements() && isLoan(t)) continue;
            if (cfg.excludeOnlineGaming() && isGaming(t)) continue;
            if (t.amount().compareTo(BigDecimal.ZERO) <= 0) continue;
            qualifyingDeposits.add(t);
        }
        out.put("qualifyingDepositCount", qualifyingDeposits.size());
        if (qualifyingDeposits.isEmpty()) {
            return di(out, "Zero qualifying deposits — average deposit undefined");
        }

        // Robust baseline: mean of deposits ≤ 3× median. Prevents a bulk credit from
        // inflating "average deposits" and defeating the >10× test (circular self-inclusion).
        List<BigDecimal> amounts = qualifyingDeposits.stream().map(Txn::amount).sorted().toList();
        BigDecimal median = medianOf(amounts);
        BigDecimal coreCap = median.multiply(new BigDecimal("3"));
        List<Txn> coreForAverage = qualifyingDeposits.stream()
                .filter(t -> t.amount().compareTo(coreCap) <= 0)
                .toList();
        if (coreForAverage.isEmpty()) {
            // Degenerate: all deposits are extreme relative to median — use median as baseline
            coreForAverage = List.of();
        }
        BigDecimal avg;
        String averageMethod;
        if (coreForAverage.isEmpty()) {
            avg = median.setScale(2, RoundingMode.HALF_UP);
            averageMethod = "MEDIAN_FALLBACK";
        } else {
            BigDecimal sumDep = coreForAverage.stream().map(Txn::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sumDep.divide(BigDecimal.valueOf(coreForAverage.size()), 2, RoundingMode.HALF_UP);
            averageMethod = "MEAN_OF_DEPOSITS_LTE_3X_MEDIAN";
        }
        BigDecimal threshold = avg.multiply(cfg.multiple()).setScale(2, RoundingMode.HALF_UP);
        out.put("averageDepositAmount", avg);
        out.put("averageMethod", averageMethod);
        out.put("medianDepositAmount", median);
        out.put("coreDepositCountForAverage", coreForAverage.isEmpty() ? 0 : coreForAverage.size());
        out.put("bulkThreshold", threshold);
        out.put("multiplier", cfg.multiple());
        out.put("averageDefinition",
                "Average qualifying merchant credit amount over the period "
                        + "(mean of deposits ≤ 3× median; loan/gaming/duplicates excluded per config)");

        List<Map<String, Object>> candidateBulk = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        Set<String> excludedKeys = new LinkedHashSet<>();
        BigDecimal excludedAmount = BigDecimal.ZERO;
        for (Txn t : inWindow) {
            if (!isCredit(t) || t.amount() == null) continue;
            if (cfg.excludeLoanDisbursements() && isLoan(t)) continue;
            if (cfg.excludeOnlineGaming() && isGaming(t)) continue;
            boolean bulk = cfg.strictGreaterThan()
                    ? t.amount().compareTo(threshold) > 0
                    : t.amount().compareTo(threshold) >= 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", t.date().toString());
            row.put("amount", t.amount());
            row.put("narration", t.narration());
            row.put("category", t.category());
            if (bulk) {
                candidateBulk.add(row);
                String key = dedupeKey(t);
                if (excludedKeys.add(key)) {
                    excluded.add(row);
                    excludedAmount = excludedAmount.add(t.amount());
                }
            }
        }
        out.put("candidateBulkCredits", candidateBulk);
        out.put("excludedCredits", excluded);
        out.put("excludedBulkAmount", excludedAmount);

        AdbPair adb = computeBaseAndAdjusted(inWindow, start, end, excludedKeys);
        if (adb.baseOutcome.equals(OUTCOME_DI)) {
            out.put("baseAdb", null);
            out.put("adjustedAdb", null);
            return di(out, adb.reason == null ? "ADB reconstruction data insufficient" : adb.reason);
        }
        out.put("outcome", OUTCOME_PASS);
        out.put("v", adb.adjusted);
        out.put("baseAdb", adb.base);
        out.put("adjustedAdb", adb.adjusted);
        out.put("adbEvidence", adb.evidence);
        out.put("reason", null);
        return out;
    }

    /**
     * Staging fixture (hand-reconcilable):
     * Qualifying deposits 10k+12k+8k+10k+9k → avg 9,800; 10× = 98,000.
     * ₹1,20,000 &gt; threshold → excluded; ₹98,000 == threshold → NOT excluded (strict &gt;).
     */
    public static List<Txn> stagingFixture() {
        LocalDate asOf = LocalDate.of(2026, 8, 1);
        LocalDate start = asOf.minusMonths(3).plusDays(1);
        List<Txn> txns = new ArrayList<>();
        BigDecimal bal = new BigDecimal("50000");
        // Opening balance marker
        txns.add(txn(start, "OPENING BALANCE", "CREDIT", "0", "OTHER", true, bal, null));

        BigDecimal[] deps = {
                new BigDecimal("10000"), new BigDecimal("12000"), new BigDecimal("8000"),
                new BigDecimal("10000"), new BigDecimal("9000")
        };
        int day = 10;
        for (BigDecimal dep : deps) {
            bal = bal.add(dep);
            txns.add(txn(start.plusDays(day), "UPI/CR/MERCHANT SETTLEMENT", "CREDIT",
                    dep.toPlainString(), "QR_SETTLEMENT", true, bal, null));
            day += 12;
        }
        // Exactly 10× — must NOT exclude
        bal = bal.add(new BigDecimal("98000"));
        txns.add(txn(asOf.minusDays(20), "NEFT CR MERCHANT INFLOW 98K", "CREDIT",
                "98000", "OTHER", true, bal, null));
        // Bulk > 10× — exclude
        bal = bal.add(new BigDecimal("120000"));
        txns.add(txn(asOf.minusDays(15), "RTGS CR BULK MERCHANT 120K", "CREDIT",
                "120000", "OTHER", true, bal, null));
        // Loan disbursement — excluded from average population & already policy exclusion
        bal = bal.add(new BigDecimal("200000"));
        txns.add(txn(asOf.minusDays(12), "LOAN DISBURSEMENT HDFC", "CREDIT",
                "200000", "LOAN_DISBURSEMENT", true, bal, null));
        // Gaming — excluded from average population
        bal = bal.add(new BigDecimal("5000"));
        txns.add(txn(asOf.minusDays(8), "ONLINE GAMING CREDIT", "CREDIT",
                "5000", "ONLINE_GAMING", true, bal, null));
        // Noise debit
        bal = bal.subtract(new BigDecimal("3000"));
        txns.add(txn(asOf.minusDays(5), "UPI/DR/VENDOR", "DEBIT",
                "3000", "OTHER", true, bal, null));
        return txns;
    }

    private static AdbPair computeBaseAndAdjusted(
            List<Txn> window, LocalDate start, LocalDate end, Set<String> excludedKeys) {
        TreeMap<LocalDate, BigDecimal> baseEod = buildEod(window, false, Set.of());
        TreeMap<LocalDate, BigDecimal> adjEod = buildEod(window, true, excludedKeys);
        if (baseEod.isEmpty() || adjEod.isEmpty()) {
            return new AdbPair(OUTCOME_DI, null, null, "NO_EOD_BALANCES", Map.of());
        }
        BigDecimal base = averageCarryForward(baseEod, start, end);
        BigDecimal adj = averageCarryForward(adjEod, start, end);
        if (base == null || adj == null) {
            return new AdbPair(OUTCOME_DI, null, null, "INSUFFICIENT_CONTINUITY", Map.of());
        }
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("method", "EOD_CARRY_FORWARD_WITH_BULK_CREDIT_REVERSAL");
        ev.put("baseAdb", base);
        ev.put("adjustedAdb", adj);
        ev.put("excludedKeys", excludedKeys.size());
        return new AdbPair(OUTCOME_PASS, base, adj, null, ev);
    }

    /**
     * Build EOD map. When adjust=true, reverse excluded credit effects from posted balances
     * (subtract credit amount from that day and subsequent EODs) so ADB matches
     * "as if bulk credit never posted".
     */
    private static TreeMap<LocalDate, BigDecimal> buildEod(
            List<Txn> window, boolean adjust, Set<String> excludedKeys) {
        TreeMap<LocalDate, BigDecimal> eod = new TreeMap<>();
        BigDecimal last = null;
        List<Txn> sorted = new ArrayList<>(window);
        sorted.sort((a, b) -> {
            int c = a.date().compareTo(b.date());
            if (c != 0) return c;
            return Objects.toString(a.narration(), "").compareTo(Objects.toString(b.narration(), ""));
        });
        BigDecimal cumulativeExcluded = BigDecimal.ZERO;
        for (Txn t : sorted) {
            if (adjust && isCredit(t) && excludedKeys.contains(dedupeKey(t)) && t.amount() != null) {
                cumulativeExcluded = cumulativeExcluded.add(t.amount());
            }
            if (t.balanceAfter() != null) {
                BigDecimal bal = t.balanceAfter();
                if (adjust) bal = bal.subtract(cumulativeExcluded);
                last = bal;
                eod.put(t.date(), last);
            } else if (last != null && t.amount() != null) {
                boolean skip = adjust && isCredit(t) && excludedKeys.contains(dedupeKey(t));
                if (!skip) {
                    if (isCredit(t)) last = last.add(t.amount());
                    else if (isDebit(t)) last = last.subtract(t.amount());
                }
                eod.put(t.date(), last);
            }
        }
        return eod;
    }

    private static BigDecimal averageCarryForward(TreeMap<LocalDate, BigDecimal> eod, LocalDate start, LocalDate end) {
        if (eod.isEmpty()) return null;
        LocalDate first = eod.firstKey();
        LocalDate cursor = start.isBefore(first) ? first : start;
        BigDecimal running = eod.firstEntry().getValue();
        BigDecimal sum = BigDecimal.ZERO;
        int days = 0;
        while (!cursor.isAfter(end)) {
            if (eod.containsKey(cursor)) running = eod.get(cursor);
            sum = sum.add(running);
            days++;
            cursor = cursor.plusDays(1);
        }
        if (days == 0) return null;
        return sum.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> di(Map<String, Object> out, String reason) {
        out.put("outcome", OUTCOME_DI);
        out.put("v", null);
        out.put("reason", reason);
        out.put("adjustedAdb", null);
        return out;
    }

    private static boolean isCredit(Txn t) {
        return t.direction() != null && "CREDIT".equalsIgnoreCase(t.direction());
    }

    private static boolean isDebit(Txn t) {
        return t.direction() != null && "DEBIT".equalsIgnoreCase(t.direction());
    }

    private static boolean isLoan(Txn t) {
        String c = norm(t.category());
        String n = norm(t.narration());
        return c.contains("LOAN_DISBURSEMENT") || c.contains("LOAN") && c.contains("DISB")
                || n.contains("LOAN DISBURSEMENT") || n.contains("DISBURSAL");
    }

    private static boolean isGaming(Txn t) {
        String c = norm(t.category());
        String n = norm(t.narration());
        return c.contains("ONLINE_GAMING") || c.contains("GAMING")
                || n.contains("ONLINE GAMING") || n.contains("GAMING");
    }

    private static String dedupeKey(Txn t) {
        return Objects.toString(t.date(), "") + "|"
                + norm(t.narration()) + "|"
                + (t.amount() == null ? "" : t.amount().toPlainString());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal medianOf(List<BigDecimal> sorted) {
        if (sorted == null || sorted.isEmpty()) return BigDecimal.ZERO;
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static Txn txn(
            LocalDate d, String n, String dir, String amt, String cat,
            boolean classified, BigDecimal bal, String dup) {
        return new Txn(d, n, dir, new BigDecimal(amt), cat, classified, bal, dup);
    }

    private record AdbPair(
            String baseOutcome, BigDecimal base, BigDecimal adjusted, String reason, Map<String, Object> evidence) {}
}
