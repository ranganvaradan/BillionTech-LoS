package com.los.core.service.aa.providers;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes AA provider FI payloads into the {@code fetchedDataSummary} shape used by the UI.
 */
public final class AaFiDataParser {

    private AaFiDataParser() {
    }

    public static Map<String, Object> simulatedSummary() {
        Map<String, Object> fetchedData = new LinkedHashMap<>();
        fetchedData.put("fetchTimestamp", Instant.now().toString());
        fetchedData.put("accountCount", 2);
        fetchedData.put("accounts", List.of(
                Map.of("type", "SAVINGS", "bank", "SBI", "balance", 245000,
                        "avgMonthlyBalance", 180000, "txnCount6Months", 142),
                Map.of("type", "CURRENT", "bank", "HDFC", "balance", 890000,
                        "avgMonthlyBalance", 620000, "txnCount6Months", 387)
        ));
        fetchedData.put("totalBalance", 1135000);
        fetchedData.put("avgMonthlyInflow", 450000);
        fetchedData.put("avgMonthlyOutflow", 320000);
        fetchedData.put("regularEmiOutflows", 45000);
        fetchedData.put("bounceCount6Months", 0);
        fetchedData.put("simulated", true);
        return fetchedData;
    }

    public static Map<String, Object> parseSetuFiPayload(JsonNode root) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        collectAccounts(root, accounts);

        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalInflow = BigDecimal.ZERO;
        BigDecimal totalOutflow = BigDecimal.ZERO;
        int bounceCount = 0;

        for (Map<String, Object> account : accounts) {
            totalBalance = totalBalance.add(toBigDecimal(account.get("balance")));
            totalInflow = totalInflow.add(toBigDecimal(account.get("avgMonthlyInflow")));
            totalOutflow = totalOutflow.add(toBigDecimal(account.get("avgMonthlyOutflow")));
            bounceCount += toInt(account.get("bounceCount6Months"));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fetchTimestamp", Instant.now().toString());
        summary.put("accountCount", accounts.size());
        summary.put("accounts", accounts);
        summary.put("totalBalance", totalBalance.setScale(0, RoundingMode.HALF_UP).longValue());
        summary.put("avgMonthlyInflow", totalInflow.setScale(0, RoundingMode.HALF_UP).longValue());
        summary.put("avgMonthlyOutflow", totalOutflow.setScale(0, RoundingMode.HALF_UP).longValue());
        summary.put("regularEmiOutflows", inferEmiOutflows(accounts));
        summary.put("bounceCount6Months", bounceCount);
        summary.put("simulated", false);
        return summary;
    }

    private static void collectAccounts(JsonNode node, List<Map<String, Object>> accounts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (looksLikeAccount(node)) {
            accounts.add(toAccountSummary(node));
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                collectAccounts(element, accounts);
            }
            return;
        }
        if (node.isObject()) {
            for (String key : List.of("accounts", "Accounts", "fiObjects", "FI", "data", "payload")) {
                if (node.has(key)) {
                    collectAccounts(node.get(key), accounts);
                }
            }
            node.fields().forEachRemaining(entry -> collectAccounts(entry.getValue(), accounts));
        }
    }

    private static boolean looksLikeAccount(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        return node.has("maskedAccNumber")
                || node.has("accountNumber")
                || node.has("type")
                || node.has("fitype")
                || node.has("balance")
                || node.has("currentBalance");
    }

    private static Map<String, Object> toAccountSummary(JsonNode node) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("type", firstText(node, "type", "fitype", "accountType", "accountSubType", "summary"));
        account.put("bank", firstText(node, "bank", "bankName", "fipName", "linkedAccRef"));
        account.put("balance", readLong(node, "balance", "currentBalance", "currentBalanceAmount"));
        account.put("avgMonthlyBalance", readLong(node, "avgMonthlyBalance", "averageBalance"));
        account.put("txnCount6Months", readInt(node, "txnCount6Months", "transactionCount"));
        account.put("avgMonthlyInflow", readLong(node, "avgMonthlyInflow", "monthlyInflow"));
        account.put("avgMonthlyOutflow", readLong(node, "avgMonthlyOutflow", "monthlyOutflow"));
        account.put("bounceCount6Months", readInt(node, "bounceCount6Months", "bounceCount"));
        return account;
    }

    private static long readLong(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.longValue();
                }
                if (value.isTextual()) {
                    try {
                        return new BigDecimal(value.asText().trim()).longValue();
                    } catch (NumberFormatException ignored) {
                        // try next key
                    }
                }
            }
        }
        return 0L;
    }

    private static int readInt(JsonNode node, String... keys) {
        return (int) readLong(node, keys);
    }

    private static long inferEmiOutflows(List<Map<String, Object>> accounts) {
        return accounts.stream()
                .mapToLong(a -> toLong(a.get("avgMonthlyOutflow")) / Math.max(accounts.size(), 1) / 7)
                .sum();
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("").trim();
            if (!value.isEmpty()) {
                return value.toUpperCase(Locale.ROOT);
            }
        }
        return "DEPOSIT";
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return new BigDecimal(String.valueOf(value)).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int toInt(Object value) {
        return (int) toLong(value);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
