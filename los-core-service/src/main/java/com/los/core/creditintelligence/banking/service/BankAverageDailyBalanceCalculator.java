package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * BANK_AVERAGE_DAILY_BALANCE_V1 — EOD carry-forward. Gap / insufficient continuity → DATA_INSUFFICIENT.
 * Valid zero balance days are included (not treated as missing).
 */
@Component
public class BankAverageDailyBalanceCalculator {

    public record AdbResult(
            String outcome,
            BigDecimal averageDailyBalance,
            BigDecimal minimumBalance,
            int negativeDays,
            Map<String, Object> evidence) {
    }

    public AdbResult calculate(List<CiBankTransaction> transactions, int months, LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("method", BankingConstants.BANK_AVERAGE_DAILY_BALANCE_V1);
        evidence.put("months", months);
        if (transactions == null || transactions.isEmpty()) {
            evidence.put("reason", "NO_TRANSACTIONS");
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
        }

        LocalDate end = asOf != null ? asOf : LocalDate.now();
        LocalDate start = end.minusMonths(months).plusDays(1);
        evidence.put("windowStart", start.toString());
        evidence.put("windowEnd", end.toString());

        List<CiBankTransaction> window = transactions.stream()
                .filter(t -> !"DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus()))
                .filter(t -> t.getTransactionDate() != null
                        && !t.getTransactionDate().isBefore(start)
                        && !t.getTransactionDate().isAfter(end))
                .sorted(Comparator.comparing(CiBankTransaction::getTransactionDate)
                        .thenComparing(t -> t.getId() != null ? t.getId().toString() : ""))
                .toList();

        long withBalance = window.stream().filter(t -> t.getBalanceAfter() != null).count();
        if (withBalance == 0) {
            evidence.put("reason", "NO_BALANCE_SERIES");
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
        }

        // Build EOD map with carry-forward; detect continuity breaks
        TreeMap<LocalDate, BigDecimal> eod = new TreeMap<>();
        BigDecimal lastKnown = null;
        LocalDate lastDate = null;
        int balanceBreaks = 0;
        List<String> breakDates = new ArrayList<>();

        for (CiBankTransaction t : window) {
            if (t.getBalanceAfter() != null) {
                if (lastKnown != null && lastDate != null) {
                    BigDecimal expected = applyTxn(lastKnown, t);
                    // Soft continuity check only when previous balance known and txn not first of day
                    if (expected != null && t.getBalanceAfter().subtract(expected).abs()
                            .compareTo(new BigDecimal("1.00")) > 0
                            && eod.containsKey(t.getTransactionDate())) {
                        // same-day multi-txn: skip break count for intermediate
                    } else if (expected != null
                            && lastDate.equals(t.getTransactionDate().minusDays(1))
                            && t.getBalanceAfter().subtract(expected).abs().compareTo(new BigDecimal("1.00")) > 0) {
                        balanceBreaks++;
                        breakDates.add(t.getTransactionDate().toString());
                    }
                }
                lastKnown = t.getBalanceAfter();
                lastDate = t.getTransactionDate();
                eod.put(t.getTransactionDate(), lastKnown);
            } else if (lastKnown != null) {
                lastKnown = applyTxn(lastKnown, t);
                lastDate = t.getTransactionDate();
                eod.put(t.getTransactionDate(), lastKnown);
            }
        }

        if (eod.isEmpty()) {
            evidence.put("reason", "NO_EOD_BALANCES");
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
        }

        // Require opening balance point at or before window start, or first txn near start
        LocalDate firstEod = eod.firstKey();
        long gapFromStart = java.time.temporal.ChronoUnit.DAYS.between(start, firstEod);
        double coverageDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1.0;
        if (gapFromStart > 7 && eod.size() < coverageDays * 0.5) {
            evidence.put("reason", "INSUFFICIENT_CONTINUITY");
            evidence.put("gapFromStartDays", gapFromStart);
            evidence.put("balanceBreaks", balanceBreaks);
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
        }

        BigDecimal running = eod.firstEntry().getValue();
        LocalDate cursor = start.isBefore(firstEod) ? firstEod : start;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        int days = 0;
        int negativeDays = 0;

        while (!cursor.isAfter(end)) {
            if (eod.containsKey(cursor)) {
                running = eod.get(cursor);
            }
            // Carry-forward for days without txn
            sum = sum.add(running);
            if (min == null || running.compareTo(min) < 0) {
                min = running;
            }
            if (running.compareTo(BigDecimal.ZERO) < 0) {
                negativeDays++;
            }
            days++;
            cursor = cursor.plusDays(1);
        }

        if (days == 0) {
            evidence.put("reason", "ZERO_DAYS");
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
        }

        BigDecimal avg = sum.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        evidence.put("days", days);
        evidence.put("balanceBreaks", balanceBreaks);
        evidence.put("breakDates", breakDates);
        evidence.put("negativeDays", negativeDays);
        evidence.put("average", avg.toPlainString());
        evidence.put("minimum", min != null ? min.toPlainString() : null);

        if (balanceBreaks > Math.max(3, days / 30)) {
            evidence.put("reason", "EXCESSIVE_BALANCE_BREAKS");
            return new AdbResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, negativeDays, evidence);
        }

        return new AdbResult(BankingMetricOutcome.PASS.name(), avg, min, negativeDays, evidence);
    }

    private static BigDecimal applyTxn(BigDecimal balance, CiBankTransaction t) {
        if (balance == null || t.getAmount() == null) {
            return balance;
        }
        if (TxnDirection.CREDIT.name().equalsIgnoreCase(t.getDirection())) {
            return balance.add(t.getAmount());
        }
        if (TxnDirection.DEBIT.name().equalsIgnoreCase(t.getDirection())) {
            return balance.subtract(t.getAmount());
        }
        return balance;
    }
}
