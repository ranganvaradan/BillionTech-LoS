package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankAccountType;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.BankingMismatchClassification;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OD/CC utilisation only with a valid limit (sanctioned / drawingPower / overdraft).
 * Otherwise DATA_INSUFFICIENT — never invent utilisation from thin air.
 */
@Component
public class BankOdUtilisationCalculator {

    public record OdResult(
            String outcome,
            BigDecimal averageUtilisationPct,
            BigDecimal peakUtilisationPct,
            int daysAbove90Pct,
            Map<String, Object> evidence) {
    }

    public OdResult calculate(
            CiBankAccount account,
            List<CiBankTransaction> transactions,
            int months,
            LocalDate asOf,
            double warningPct) {

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("warningPct", warningPct);

        if (account == null) {
            evidence.put("reason", "NO_ACCOUNT");
            return di(evidence);
        }

        String type = account.getAccountType();
        boolean odCc = BankAccountType.OVERDRAFT.name().equalsIgnoreCase(type)
                || BankAccountType.CASH_CREDIT.name().equalsIgnoreCase(type);
        evidence.put("accountType", type);
        if (!odCc) {
            evidence.put("reason", "NOT_OD_CC_ACCOUNT");
            return di(evidence);
        }

        BigDecimal limit = firstPositive(
                account.getDrawingPower(),
                account.getOverdraftLimit(),
                account.getSanctionedLimit());
        if (limit == null) {
            evidence.put("reason", BankingMismatchClassification.OD_LIMIT_MISSING.name());
            return di(evidence);
        }
        evidence.put("limit", limit.toPlainString());

        if (transactions == null || transactions.isEmpty()) {
            evidence.put("reason", "NO_TRANSACTIONS");
            return di(evidence);
        }

        LocalDate end = asOf != null ? asOf : LocalDate.now();
        LocalDate start = end.minusMonths(months).plusDays(1);

        BigDecimal sumPct = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        int days = 0;
        int above90 = 0;
        BigDecimal warning = BigDecimal.valueOf(warningPct);

        for (CiBankTransaction t : transactions) {
            if (t.getBalanceAfter() == null || t.getTransactionDate() == null) {
                continue;
            }
            if (t.getTransactionDate().isBefore(start) || t.getTransactionDate().isAfter(end)) {
                continue;
            }
            // Utilisation: outstanding / limit. For OD, outstanding often = |negative balance| or limit - available.
            BigDecimal outstanding = t.getBalanceAfter().signum() < 0
                    ? t.getBalanceAfter().abs()
                    : limit.subtract(t.getBalanceAfter()).max(BigDecimal.ZERO);
            BigDecimal pct = outstanding.multiply(BigDecimal.valueOf(100))
                    .divide(limit, 2, RoundingMode.HALF_UP);
            sumPct = sumPct.add(pct);
            if (pct.compareTo(peak) > 0) {
                peak = pct;
            }
            if (pct.compareTo(warning) >= 0) {
                above90++;
            }
            days++;
        }

        if (days == 0) {
            evidence.put("reason", "NO_BALANCE_POINTS");
            return di(evidence);
        }

        BigDecimal avg = sumPct.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        evidence.put("days", days);
        evidence.put("averageUtilisationPct", avg.toPlainString());
        evidence.put("peakUtilisationPct", peak.toPlainString());
        evidence.put("daysAboveWarning", above90);
        return new OdResult(BankingMetricOutcome.PASS.name(), avg, peak, above90, evidence);
    }

    private static OdResult di(Map<String, Object> evidence) {
        return new OdResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, null, 0, evidence);
    }

    private static BigDecimal firstPositive(BigDecimal... vals) {
        if (vals == null) {
            return null;
        }
        for (BigDecimal v : vals) {
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }
        return null;
    }
}
