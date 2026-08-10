package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankRecurringObligation;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BANK_EMI_DETECTION_V1 — recurring EMI/NACH lender debits. No percentage estimates.
 */
@Component
public class BankEmiObligationDetector {

    private static final Pattern LENDER_NAME = Pattern.compile(
            "(HDFC\\s*BANK|BAJAJ\\s*FINSERV|BAJAJ\\s*FINANCE|ICICI|SBI|AXIS|KOTAK|FULLERTON|TATA\\s*CAPITAL|"
                    + "[A-Z][A-Z0-9\\s]{2,40}?\\s+EMI)",
            Pattern.CASE_INSENSITIVE);

    public record DetectionResult(
            String outcome,
            BigDecimal monthlyObligation,
            List<CiBankRecurringObligation> obligations,
            Map<String, Object> evidence) {
    }

    public DetectionResult detect(
            UUID tenantId,
            UUID applicationId,
            UUID bankAccountId,
            List<CiBankTransaction> transactions,
            int minOccurrences,
            double regularityThreshold) {

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("method", BankingConstants.BANK_EMI_DETECTION_V1);
        evidence.put("minOccurrences", minOccurrences);
        evidence.put("regularityThreshold", regularityThreshold);

        if (transactions == null || transactions.isEmpty()) {
            evidence.put("reason", "NO_TRANSACTIONS");
            return new DetectionResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, List.of(), evidence);
        }

        List<CiBankTransaction> emiTxns = transactions.stream()
                .filter(t -> !"DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus()))
                .filter(t -> TxnDirection.DEBIT.name().equalsIgnoreCase(t.getDirection()))
                .filter(t -> t.isEmiFlag() || TxnCategory.EMI.name().equalsIgnoreCase(t.getCategory()))
                .sorted(Comparator.comparing(CiBankTransaction::getTransactionDate))
                .toList();

        if (emiTxns.isEmpty()) {
            evidence.put("reason", "NO_EMI_TRANSACTIONS");
            return new DetectionResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, List.of(), evidence);
        }

        Map<String, List<CiBankTransaction>> byLenderAmount = emiTxns.stream()
                .collect(Collectors.groupingBy(t -> {
                    String lender = extractLender(t.getDescriptionRaw());
                    String amt = t.getAmount() != null
                            ? t.getAmount().setScale(0, RoundingMode.HALF_UP).toPlainString()
                            : "0";
                    return lender + "|" + amt;
                }, LinkedHashMap::new, Collectors.toList()));

        List<CiBankRecurringObligation> obligations = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, List<CiBankTransaction>> e : byLenderAmount.entrySet()) {
            List<CiBankTransaction> group = e.getValue();
            if (group.size() < minOccurrences) {
                continue;
            }
            double regularity = computeRegularity(group);
            if (regularity < regularityThreshold) {
                continue;
            }
            String[] parts = e.getKey().split("\\|", 2);
            String lender = parts[0];
            BigDecimal amount = group.get(0).getAmount();
            List<Object> ids = group.stream()
                    .map(t -> t.getId() != null ? t.getId().toString() : null)
                    .filter(id -> id != null)
                    .map(id -> (Object) id)
                    .toList();

            CiBankRecurringObligation obl = CiBankRecurringObligation.builder()
                    .tenantId(tenantId)
                    .applicationId(applicationId)
                    .bankAccountId(bankAccountId)
                    .lenderName(lender)
                    .detectedAmount(amount)
                    .frequency("MONTHLY")
                    .firstObserved(group.get(0).getTransactionDate())
                    .lastObserved(group.get(group.size() - 1).getTransactionDate())
                    .occurrenceCount(group.size())
                    .regularityScore(BigDecimal.valueOf(regularity).setScale(4, RoundingMode.HALF_UP))
                    .sourceTransactionIds(ids)
                    .confidence(BigDecimal.valueOf(Math.min(0.99, 0.7 + regularity * 0.25)))
                    .method(BankingConstants.BANK_EMI_DETECTION_V1)
                    .methodVersion("V1")
                    .estimated(false)
                    .qualityStatus("OK")
                    .metadata(Map.of("groupKey", e.getKey()))
                    .build();
            obligations.add(obl);
            total = total.add(amount);
        }

        evidence.put("obligationCount", obligations.size());
        evidence.put("emiTxnCount", emiTxns.size());
        evidence.put("monthlyObligation", total.toPlainString());

        if (obligations.isEmpty()) {
            evidence.put("reason", "INSUFFICIENT_RECURRENCE");
            return new DetectionResult(BankingMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, List.of(), evidence);
        }

        return new DetectionResult(BankingMetricOutcome.PASS.name(), total, obligations, evidence);
    }

    static String extractLender(String narration) {
        if (narration == null || narration.isBlank()) {
            return "UNKNOWN_LENDER";
        }
        Matcher m = LENDER_NAME.matcher(narration.toUpperCase());
        if (m.find()) {
            return m.group().replaceAll("\\s+", " ").trim();
        }
        String norm = BankTransactionClassifier.normalize(narration);
        return norm.length() > 40 ? norm.substring(0, 40) : norm;
    }

    static double computeRegularity(List<CiBankTransaction> group) {
        if (group.size() < 2) {
            return 0.0;
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < group.size(); i++) {
            LocalDate prev = group.get(i - 1).getTransactionDate();
            LocalDate cur = group.get(i).getTransactionDate();
            if (prev != null && cur != null) {
                gaps.add(ChronoUnit.DAYS.between(prev, cur));
            }
        }
        if (gaps.isEmpty()) {
            return 0.0;
        }
        double avg = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
        // Ideal monthly ≈ 28-35 days
        long nearMonthly = gaps.stream().filter(g -> g >= 25 && g <= 40).count();
        double monthlyRatio = (double) nearMonthly / gaps.size();
        double avgScore = (avg >= 25 && avg <= 40) ? 1.0 : Math.max(0, 1.0 - Math.abs(avg - 30) / 30.0);
        return Math.min(1.0, 0.6 * monthlyRatio + 0.4 * avgScore);
    }
}
