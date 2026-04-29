package com.billiontech.bankstatement.service.tamper;

import com.billiontech.bankstatement.model.entity.BankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
public class TamperDetectionService {

    public TamperResult check(List<BankTransaction> transactions) {
        List<String> issues = new ArrayList<>();

        if (transactions.isEmpty()) {
            return TamperResult.builder().status("SKIPPED").details("No transactions to verify").issues(issues).build();
        }

        // Check running balance consistency
        int balanceMismatches = checkBalanceConsistency(transactions, issues);

        // Check date sequence
        checkDateSequence(transactions, issues);

        // Check for suspicious patterns
        checkSuspiciousPatterns(transactions, issues);

        String status;
        if (issues.isEmpty()) {
            status = "CLEAN";
        } else if (balanceMismatches > transactions.size() * 0.1) {
            status = "SUSPICIOUS";
        } else {
            status = "WARNING";
        }

        return TamperResult.builder()
                .status(status)
                .details(issues.isEmpty() ? "No tampering indicators found" : String.join("; ", issues))
                .issues(issues)
                .build();
    }

    private int checkBalanceConsistency(List<BankTransaction> transactions, List<String> issues) {
        int mismatches = 0;
        for (int i = 1; i < transactions.size(); i++) {
            BankTransaction prev = transactions.get(i - 1);
            BankTransaction curr = transactions.get(i);

            if (prev.getRunningBalance() != null && curr.getRunningBalance() != null) {
                BigDecimal expectedBalance = prev.getRunningBalance()
                        .add(Optional.ofNullable(curr.getCreditAmount()).orElse(BigDecimal.ZERO))
                        .subtract(Optional.ofNullable(curr.getDebitAmount()).orElse(BigDecimal.ZERO));

                if (expectedBalance.compareTo(curr.getRunningBalance()) != 0) {
                    BigDecimal diff = expectedBalance.subtract(curr.getRunningBalance()).abs();
                    if (diff.compareTo(new BigDecimal("0.01")) > 0) {
                        mismatches++;
                    }
                }
            }
        }
        if (mismatches > 0) {
            issues.add("Running balance inconsistency in " + mismatches + " transactions");
        }
        return mismatches;
    }

    private void checkDateSequence(List<BankTransaction> transactions, List<String> issues) {
        int outOfOrder = 0;
        for (int i = 1; i < transactions.size(); i++) {
            if (transactions.get(i).getTransactionDate().isBefore(transactions.get(i - 1).getTransactionDate())) {
                outOfOrder++;
            }
        }
        if (outOfOrder > 0) {
            issues.add(outOfOrder + " transactions out of chronological order");
        }
    }

    private void checkSuspiciousPatterns(List<BankTransaction> transactions, List<String> issues) {
        // Check for duplicate transactions
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        for (BankTransaction txn : transactions) {
            String key = txn.getTransactionDate() + "|" + txn.getNarration() + "|"
                    + txn.getDebitAmount() + "|" + txn.getCreditAmount();
            if (!seen.add(key)) {
                duplicates++;
            }
        }
        if (duplicates > 3) {
            issues.add(duplicates + " potential duplicate transactions found");
        }

        // Check for round-amount patterns
        long roundAmounts = transactions.stream()
                .filter(t -> {
                    BigDecimal amount = Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO)
                            .max(Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO));
                    return amount.compareTo(new BigDecimal("10000")) >= 0
                            && amount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) == 0;
                }).count();
        if (roundAmounts > transactions.size() * 0.3) {
            issues.add("Unusually high number of round-amount transactions (" + roundAmounts + ")");
        }
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class TamperResult {
        private String status;
        private String details;
        private List<String> issues;
    }
}
