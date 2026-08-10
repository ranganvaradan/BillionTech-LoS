package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.BankingMismatchClassification;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ADJUSTED_BANKING_TURNOVER_V1 — exclude loan disbursement, self transfer, capital infusion,
 * interest credits, refunds; include CUSTOMER_RECEIPT / OTHER_OPERATING / business_receipt_flag.
 */
@Component
public class BankAdjustedTurnoverCalculator {

    private static final Set<String> EXCLUDE = Set.of(
            TxnCategory.LOAN_DISBURSEMENT.name(),
            TxnCategory.SELF_TRANSFER.name(),
            TxnCategory.CAPITAL_INFUSION.name(),
            TxnCategory.INTEREST_CREDIT.name(),
            TxnCategory.REFUND.name());

    public record TurnoverResult(
            String outcome,
            BigDecimal adjustedCredits,
            BigDecimal cashDeposits,
            BigDecimal cashDepositRatio,
            double classificationCoverage,
            List<UUID> includedIds,
            List<UUID> excludedIds,
            Map<String, Object> evidence) {
    }

    public TurnoverResult calculate(
            List<CiBankTransaction> transactions,
            int months,
            LocalDate asOf,
            double minClassificationCoverage) {

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("method", BankingConstants.ADJUSTED_BANKING_TURNOVER_V1);
        evidence.put("months", months);

        if (transactions == null || transactions.isEmpty()) {
            evidence.put("reason", "NO_TRANSACTIONS");
            return new TurnoverResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, null, null, 0.0, List.of(), List.of(), evidence);
        }

        LocalDate end = asOf != null ? asOf : LocalDate.now();
        LocalDate start = end.minusMonths(months).plusDays(1);
        evidence.put("windowStart", start.toString());
        evidence.put("windowEnd", end.toString());

        List<CiBankTransaction> credits = transactions.stream()
                .filter(t -> !"DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus()))
                .filter(t -> TxnDirection.CREDIT.name().equalsIgnoreCase(t.getDirection()))
                .filter(t -> t.getTransactionDate() != null
                        && !t.getTransactionDate().isBefore(start)
                        && !t.getTransactionDate().isAfter(end))
                .toList();

        if (credits.isEmpty()) {
            evidence.put("reason", "NO_CREDITS_IN_WINDOW");
            // Valid empty credits window with complete statement could be zero — but without
            // classification evidence of completeness we treat as DI for adjusted turnover.
            return new TurnoverResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, null, null, 0.0, List.of(), List.of(), evidence);
        }

        long classified = credits.stream()
                .filter(t -> t.getCategory() != null
                        && !TxnCategory.UNKNOWN.name().equalsIgnoreCase(t.getCategory()))
                .count();
        double coverage = (double) classified / credits.size();
        evidence.put("classificationCoverage", coverage);
        evidence.put("creditCount", credits.size());
        evidence.put("classifiedCount", classified);

        if (coverage < minClassificationCoverage) {
            evidence.put("reason", "CLASSIFICATION_COVERAGE_BELOW_THRESHOLD");
            evidence.put("threshold", minClassificationCoverage);
            return new TurnoverResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, null, null, coverage, List.of(), List.of(), evidence);
        }

        BigDecimal adjusted = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        List<UUID> included = new ArrayList<>();
        List<UUID> excluded = new ArrayList<>();
        List<String> exclusionReasons = new ArrayList<>();

        for (CiBankTransaction t : credits) {
            String cat = t.getCategory() != null ? t.getCategory() : TxnCategory.UNKNOWN.name();
            if (EXCLUDE.contains(cat) || t.isSelfTransferFlag()) {
                excluded.add(t.getId());
                String reason = cat.equals(TxnCategory.LOAN_DISBURSEMENT.name())
                        ? BankingMismatchClassification.LOAN_DISBURSEMENT_EXCLUDED.name()
                        : cat.equals(TxnCategory.SELF_TRANSFER.name()) || t.isSelfTransferFlag()
                        ? BankingMismatchClassification.SELF_TRANSFER_EXCLUDED.name()
                        : cat.equals(TxnCategory.CAPITAL_INFUSION.name())
                        ? BankingMismatchClassification.CAPITAL_INFUSION_EXCLUDED.name()
                        : "EXCLUDED_" + cat;
                exclusionReasons.add(reason);
                continue;
            }
            boolean include = t.isBusinessReceiptFlag()
                    || TxnCategory.CUSTOMER_RECEIPT.name().equals(cat)
                    || TxnCategory.OTHER_OPERATING.name().equals(cat)
                    || TxnCategory.CASH_DEPOSIT.name().equals(cat)
                    || TxnCategory.SALARY.name().equals(cat);
            if (!include && TxnCategory.UNKNOWN.name().equals(cat)) {
                excluded.add(t.getId());
                continue;
            }
            if (!include) {
                excluded.add(t.getId());
                continue;
            }
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            adjusted = adjusted.add(amt);
            included.add(t.getId());
            if (t.isCashFlag() || TxnCategory.CASH_DEPOSIT.name().equals(cat)) {
                cash = cash.add(amt);
            }
        }

        BigDecimal ratio = adjusted.compareTo(BigDecimal.ZERO) > 0
                ? cash.divide(adjusted, 4, RoundingMode.HALF_UP)
                : null;

        evidence.put("adjustedCredits", adjusted.toPlainString());
        evidence.put("cashDeposits", cash.toPlainString());
        evidence.put("cashDepositRatio", ratio != null ? ratio.toPlainString() : null);
        evidence.put("includedCount", included.size());
        evidence.put("excludedCount", excluded.size());
        evidence.put("exclusionReasonsSample", exclusionReasons.stream().limit(20).toList());

        // Valid zero adjusted credits is PASS (not DI) when coverage ok
        return new TurnoverResult(BankingMetricOutcome.PASS.name(),
                adjusted, cash, ratio, coverage, included, excluded, evidence);
    }
}
