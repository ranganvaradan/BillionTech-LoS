package com.los.core.creditintelligence.policystudio.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Studio/banking BRE metric helpers. Do not mutate global banking.avg_daily_balance_3m.
 * Missing/unclassifiable → DATA_INSUFFICIENT (not zero).
 */
public class PolicyBankingMetricService {

    public static final String OUTCOME_PASS = "PASS";
    public static final String OUTCOME_DI = "DATA_INSUFFICIENT";

    public record TxnInput(
            LocalDate date,
            String narration,
            String direction,
            BigDecimal amount,
            String category,
            boolean classified
    ) {}

    public Map<String, Object> qrSettlementSuite(List<TxnInput> txns, boolean qrTaxonomyAvailable) {
        Map<String, Object> suite = new LinkedHashMap<>();
        if (!qrTaxonomyAvailable) {
            suite.put("banking.qr_settlement.amount_total_3m", di("QR taxonomy cannot identify QR settlements"));
            suite.put("banking.qr_settlement.average_daily_3m", di("QR taxonomy cannot identify QR settlements"));
            suite.put("banking.qr_settlement.count_3m", di("QR taxonomy cannot identify QR settlements"));
            suite.put("banking.qr_settlement.average_monthly_count_3m", di("QR taxonomy cannot identify QR settlements"));
            return suite;
        }
        List<TxnInput> qr = txns == null ? List.of() : txns.stream()
                .filter(t -> t.classified() && t.category() != null
                        && t.category().toUpperCase(Locale.ROOT).contains("QR"))
                .toList();
        BigDecimal total = qr.stream().map(TxnInput::amount).filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        suite.put("banking.qr_settlement.amount_total_3m", pass(total));
        suite.put("banking.qr_settlement.count_3m", pass(qr.size()));
        suite.put("banking.qr_settlement.average_monthly_count_3m",
                pass(BigDecimal.valueOf(qr.size()).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP)));
        suite.put("banking.qr_settlement.average_daily_3m",
                pass(total.divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP)));
        return suite;
    }

    public Map<String, Object> transactionCount3m(List<TxnInput> txns) {
        if (txns == null) {
            return Map.of(
                    "banking.transaction_count_3m", di("No transactions"),
                    "banking.average_monthly_transaction_count_3m", di("No transactions"));
        }
        long unknown = txns.stream().filter(t -> !t.classified()).count();
        if (unknown > 0 && unknown * 2 >= txns.size()) {
            return Map.of(
                    "banking.transaction_count_3m", di("Classification coverage insufficient"),
                    "banking.average_monthly_transaction_count_3m", di("Classification coverage insufficient"));
        }
        int count = txns.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("banking.transaction_count_3m", pass(count));
        out.put("banking.average_monthly_transaction_count_3m",
                pass(BigDecimal.valueOf(count).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP)));
        return out;
    }

    public Map<String, Object> inwardChequeReturn(List<TxnInput> txns, Integer totalTxnDenominator) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (txns == null) {
            out.put("banking.inward_cheque_return_count_3m", di("No transactions"));
            out.put("banking.inward_cheque_return_ratio_3m", di("No transactions"));
            return out;
        }
        int returns = (int) txns.stream().filter(t ->
                t.category() != null && t.category().toUpperCase(Locale.ROOT).contains("CHEQUE_RETURN")
                        || (t.narration() != null && t.narration().toUpperCase(Locale.ROOT).contains("CHEQUE RETURN"))
        ).count();
        out.put("banking.inward_cheque_return_count_3m", pass(returns));
        if (totalTxnDenominator == null || totalTxnDenominator <= 0) {
            out.put("banking.inward_cheque_return_ratio_3m", di("Ratio denominator missing"));
        } else {
            out.put("banking.inward_cheque_return_ratio_3m",
                    pass(BigDecimal.valueOf(returns)
                            .divide(BigDecimal.valueOf(totalTxnDenominator), 6, RoundingMode.HALF_UP)));
        }
        return out;
    }

    public Map<String, Object> classifiedCountOrDi(List<TxnInput> txns, String categoryHint, String metricCode) {
        if (txns == null || txns.isEmpty()) {
            return Map.of(metricCode, di("No transactions"));
        }
        long classified = txns.stream().filter(TxnInput::classified).count();
        if (classified * 2 < txns.size()) {
            return Map.of(metricCode, di("Classification coverage insufficient for " + categoryHint));
        }
        String hint = categoryHint.toUpperCase(Locale.ROOT);
        int count = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (TxnInput t : txns) {
            String n = t.narration() == null ? "" : t.narration().toUpperCase(Locale.ROOT);
            String c = t.category() == null ? "" : t.category().toUpperCase(Locale.ROOT);
            boolean match = c.contains(hint) || n.contains(hint)
                    || ("EMI_BOUNCE".equals(hint) && (n.contains("EMI") && (n.contains("BOUNCE") || n.contains("RETURN"))))
                    || ("ONLINE_GAMING".equals(hint) && (n.contains("GAMING") || n.contains("DREAM11") || n.contains("BETTING")))
                    || ("INTERCOMPANY".equals(hint) && (n.contains("INTERCOMPANY") || n.contains("INTER COMPANY")))
                    || ("LARGE_CREDIT".equals(hint) && t.amount() != null
                    && t.amount().compareTo(new BigDecimal("100000")) >= 0
                    && "CREDIT".equalsIgnoreCase(t.direction()));
            if (match) {
                count++;
                if (t.amount() != null) {
                    value = value.add(t.amount());
                }
            }
        }
        Map<String, Object> r = pass(count);
        r.put("amountTotal", value);
        return Map.of(metricCode, r);
    }

    /**
     * BANK_POLICY_ADJUSTED_ADB_3M — scoped exclusions; never mutates global ADB.
     * Bulk deposit exclusion requires resolved average-deposit definition else DI.
     */
    public Map<String, Object> adjustedAdb3m(
            BigDecimal rawAdb,
            List<Map<String, Object>> exclusions,
            boolean averageDepositDefinitionResolved,
            BigDecimal excludedBulkDeposits,
            BigDecimal excludedOther) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("metric", "BANK_POLICY_ADJUSTED_ADB_3M");
        evidence.put("doesNotMutate", "banking.avg_daily_balance_3m");
        if (rawAdb == null) {
            return di("Raw ADB missing", evidence);
        }
        evidence.put("rawAdb", rawAdb);
        BigDecimal excluded = BigDecimal.ZERO;
        List<Object> applied = new ArrayList<>();
        if (exclusions != null) {
            for (Map<String, Object> ex : exclusions) {
                String type = String.valueOf(ex.getOrDefault("type", ""));
                if ("BULK_DEPOSIT".equalsIgnoreCase(type) || type.toUpperCase(Locale.ROOT).contains("BULK")) {
                    if (!averageDepositDefinitionResolved) {
                        Map<String, Object> di = di("Bulk deposit exclusion requires resolved average-deposit definition", evidence);
                        di.put("executable", false);
                        return di;
                    }
                    BigDecimal amt = excludedBulkDeposits == null ? BigDecimal.ZERO : excludedBulkDeposits;
                    excluded = excluded.add(amt);
                    applied.add(Map.of("type", "BULK_DEPOSIT", "amount", amt));
                } else {
                    Object amtObj = ex.get("amount");
                    BigDecimal amt = amtObj == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(amtObj));
                    excluded = excluded.add(amt);
                    applied.add(Map.of("type", type, "amount", amt));
                }
            }
        }
        if (excludedOther != null) {
            excluded = excluded.add(excludedOther);
        }
        BigDecimal adjusted = rawAdb.subtract(excluded);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        }
        Map<String, Object> r = pass(adjusted);
        r.put("raw", rawAdb);
        r.put("adjusted", adjusted);
        r.put("excludedAmount", excluded);
        r.put("exclusionsApplied", applied);
        r.put("evidence", evidence);
        return r;
    }

    public Map<String, Object> pass(Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", OUTCOME_PASS);
        m.put("v", v);
        m.put("dataQualityStatus", "OK");
        return m;
    }

    public Map<String, Object> di(String reason) {
        return di(reason, Map.of());
    }

    public Map<String, Object> di(String reason, Map<String, Object> evidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", OUTCOME_DI);
        m.put("v", null);
        m.put("dataQualityStatus", OUTCOME_DI);
        m.put("reason", reason);
        m.put("evidence", evidence);
        return m;
    }
}
