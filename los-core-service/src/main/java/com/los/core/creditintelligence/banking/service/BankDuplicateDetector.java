package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Duplicate detection: same account + providerTxnId OR (date+amount+direction+utr)
 * OR (date+amount+direction+normalizedNarration+balanceAfter). Never date+amount alone.
 */
@Component
public class BankDuplicateDetector {

    public record DuplicateGroup(
            int canonicalIndex,
            List<Integer> memberIndexes,
            String matchingBasis,
            BigDecimal confidence) {
    }

    public List<DuplicateGroup> detectIndexed(List<CiBankTransaction> txns) {
        if (txns == null || txns.isEmpty()) {
            return List.of();
        }
        List<DuplicateGroup> groups = new ArrayList<>();
        boolean[] claimed = new boolean[txns.size()];

        groupBy(txns, claimed, groups, t -> {
            if (t.getProviderTransactionId() == null || t.getProviderTransactionId().isBlank()) {
                return null;
            }
            return "PID|" + t.getProviderTransactionId().trim();
        }, "PROVIDER_TXN_ID", BigDecimal.valueOf(0.99));

        groupBy(txns, claimed, groups, t -> {
            if (t.getUtrReference() == null || t.getUtrReference().isBlank()) {
                return null;
            }
            return utrKey(t);
        }, "DATE_AMOUNT_DIRECTION_UTR", BigDecimal.valueOf(0.95));

        groupBy(txns, claimed, groups, t -> {
            if (t.getBalanceAfter() == null) {
                return null;
            }
            return narrBalKey(t);
        }, "DATE_AMOUNT_DIRECTION_NARRATION_BALANCE", BigDecimal.valueOf(0.9));

        return groups;
    }

    private void groupBy(
            List<CiBankTransaction> txns,
            boolean[] claimed,
            List<DuplicateGroup> groups,
            Function<CiBankTransaction, String> keyFn,
            String basis,
            BigDecimal confidence) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < txns.size(); i++) {
            if (claimed[i]) {
                continue;
            }
            String key = keyFn.apply(txns.get(i));
            if (key == null) {
                continue;
            }
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> members : map.values()) {
            if (members.size() < 2) {
                continue;
            }
            int canonical = members.get(0);
            for (Integer i : members) {
                claimed[i] = true;
            }
            groups.add(new DuplicateGroup(canonical, members, basis, confidence));
        }
    }

    private static String utrKey(CiBankTransaction t) {
        return String.join("|",
                dateStr(t.getTransactionDate()),
                amt(t.getAmount()),
                str(t.getDirection()),
                t.getUtrReference().trim().toUpperCase());
    }

    private static String narrBalKey(CiBankTransaction t) {
        String narr = t.getDescriptionNormalized() != null
                ? t.getDescriptionNormalized()
                : BankTransactionClassifier.normalize(t.getDescriptionRaw());
        return String.join("|",
                dateStr(t.getTransactionDate()),
                amt(t.getAmount()),
                str(t.getDirection()),
                narr,
                "BAL",
                amt(t.getBalanceAfter()));
    }

    private static String dateStr(LocalDate d) {
        return d != null ? d.toString() : "";
    }

    private static String amt(BigDecimal a) {
        return a != null ? a.stripTrailingZeros().toPlainString() : "";
    }

    private static String str(String s) {
        return s != null ? s : "";
    }
}
