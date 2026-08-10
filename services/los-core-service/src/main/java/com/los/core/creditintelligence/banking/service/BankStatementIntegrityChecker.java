package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.CiBankStatementQuality;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
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

/**
 * Balance continuity, gaps, unsorted detection → CiBankStatementQuality fields.
 */
@Component
public class BankStatementIntegrityChecker {

    public CiBankStatementQuality check(
            UUID tenantId,
            UUID applicationId,
            UUID bankAccountId,
            UUID sourceRecordId,
            List<CiBankTransaction> transactions,
            LocalDate statementFrom,
            LocalDate statementTo) {

        Map<String, Object> meta = new LinkedHashMap<>();
        List<Object> findings = new ArrayList<>();
        int balanceBreaks = 0;
        int duplicateCount = 0;

        List<CiBankTransaction> txns = transactions != null ? new ArrayList<>(transactions) : List.of();
        for (CiBankTransaction t : txns) {
            if ("DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus())) {
                duplicateCount++;
            }
        }

        List<CiBankTransaction> unique = txns.stream()
                .filter(t -> !"DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus()))
                .sorted(Comparator.comparing(CiBankTransaction::getTransactionDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        boolean unsorted = false;
        for (int i = 1; i < txns.size(); i++) {
            LocalDate prev = txns.get(i - 1).getTransactionDate();
            LocalDate cur = txns.get(i).getTransactionDate();
            if (prev != null && cur != null && cur.isBefore(prev)) {
                unsorted = true;
                break;
            }
        }
        if (unsorted) {
            findings.add(Map.of("code", "UNSORTED_TRANSACTIONS"));
        }

        BigDecimal running = null;
        for (CiBankTransaction t : unique) {
            if (running != null && t.getAmount() != null && t.getBalanceAfter() != null) {
                BigDecimal expected = TxnDirection.CREDIT.name().equalsIgnoreCase(t.getDirection())
                        ? running.add(t.getAmount())
                        : running.subtract(t.getAmount());
                if (expected.subtract(t.getBalanceAfter()).abs().compareTo(new BigDecimal("1.00")) > 0) {
                    balanceBreaks++;
                    if (findings.size() < 20) {
                        findings.add(Map.of(
                                "code", "BALANCE_BREAK",
                                "date", t.getTransactionDate() != null ? t.getTransactionDate().toString() : null,
                                "expected", expected.toPlainString(),
                                "actual", t.getBalanceAfter().toPlainString()));
                    }
                }
            }
            if (t.getBalanceAfter() != null) {
                running = t.getBalanceAfter();
            } else if (running != null && t.getAmount() != null) {
                running = TxnDirection.CREDIT.name().equalsIgnoreCase(t.getDirection())
                        ? running.add(t.getAmount())
                        : running.subtract(t.getAmount());
            }
        }

        LocalDate from = statementFrom;
        LocalDate to = statementTo;
        if (from == null && !unique.isEmpty()) {
            from = unique.get(0).getTransactionDate();
        }
        if (to == null && !unique.isEmpty()) {
            to = unique.get(unique.size() - 1).getTransactionDate();
        }

        int calendarExpected = (from != null && to != null)
                ? (int) ChronoUnit.DAYS.between(from, to) + 1 : 0;
        long covered = unique.stream()
                .map(CiBankTransaction::getTransactionDate)
                .filter(d -> d != null)
                .distinct()
                .count();

        BigDecimal completeness = calendarExpected > 0
                ? BigDecimal.valueOf(covered).divide(BigDecimal.valueOf(calendarExpected), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long classified = unique.stream()
                .filter(t -> t.getCategory() != null && !"UNKNOWN".equalsIgnoreCase(t.getCategory()))
                .count();
        BigDecimal classCoverage = unique.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(classified).divide(BigDecimal.valueOf(unique.size()), 4, RoundingMode.HALF_UP);

        String integrity;
        if (unique.isEmpty()) {
            integrity = "NO_TRANSACTIONS";
        } else if (balanceBreaks > 5) {
            integrity = "FAILED";
        } else if (balanceBreaks > 0 || unsorted) {
            integrity = "WARNING";
        } else {
            integrity = "OK";
        }

        BigDecimal qualityScore = BigDecimal.ONE
                .subtract(BigDecimal.valueOf(balanceBreaks).multiply(new BigDecimal("0.05")))
                .max(BigDecimal.ZERO)
                .min(completeness.add(classCoverage).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP));

        meta.put("unsorted", unsorted);
        meta.put("uniqueTxnCount", unique.size());

        return CiBankStatementQuality.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .bankAccountId(bankAccountId)
                .sourceRecordId(sourceRecordId)
                .statementFrom(from)
                .statementTo(to)
                .calendarDaysExpected(calendarExpected > 0 ? calendarExpected : null)
                .calendarDaysCovered((int) covered)
                .completenessRatio(completeness)
                .integrityStatus(integrity)
                .balanceBreaks(balanceBreaks)
                .duplicateCount(duplicateCount)
                .classificationCoverage(classCoverage)
                .qualityScore(qualityScore)
                .findings(findings)
                .metadata(meta)
                .build();
    }
}
