package com.los.core.service.aa.providers;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
                        "avgMonthlyBalance", 180000, "txnCount6Months", 142,
                        "accountNumber", "XXXX1234", "accountKey", "SBI|1234"),
                Map.of("type", "CURRENT", "bank", "HDFC", "balance", 890000,
                        "avgMonthlyBalance", 620000, "txnCount6Months", 387,
                        "accountNumber", "XXXX5678", "accountKey", "HDFC|5678")
        ));
        fetchedData.put("totalBalance", 1135000);
        fetchedData.put("avgMonthlyInflow", 450000);
        fetchedData.put("avgMonthlyOutflow", 320000);
        fetchedData.put("regularEmiOutflows", 45000);
        fetchedData.put("bounceCount6Months", 0);
        fetchedData.put("transactions", buildSimulatedTransactions());
        fetchedData.put("simulated", true);
        return fetchedData;
    }

    /**
     * ~180 days of transactions across both accounts for Phase C3 E2E.
     */
    static List<Map<String, Object>> buildSimulatedTransactions() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(180);
        List<Map<String, Object>> txns = new ArrayList<>();
        BigDecimal sbiBal = new BigDecimal("150000");
        BigDecimal hdfcBal = new BigDecimal("500000");
        int seq = 1;

        // Monthly EMI NACH debits (~45000) for ~6 months on CURRENT (HDFC)
        for (int m = 0; m < 6; m++) {
            LocalDate emiDate = start.plusDays(15 + m * 30L);
            if (emiDate.isAfter(end)) {
                break;
            }
            BigDecimal amt = new BigDecimal("45000");
            hdfcBal = hdfcBal.subtract(amt);
            txns.add(txn(emiDate, amt, "DEBIT", "NACH DEBIT HDFC BANK EMI", "NACH",
                    hdfcBal, "SIM-EMI-HDFC-" + m, "HDFC|5678", seq++));
            hdfcBal = hdfcBal.subtract(amt);
            txns.add(txn(emiDate.plusDays(2), amt, "DEBIT", "NACH BAJAJ FINSERV EMI", "NACH",
                    hdfcBal, "SIM-EMI-BAJAJ-" + m, "HDFC|5678", seq++));
            // restore after second EMI for balance realism — recompute from add
            // (already subtracted twice above)
        }

        // Customer receipts (CREDIT NEFT/UPI) monthly on CURRENT
        for (int m = 0; m < 6; m++) {
            LocalDate d = start.plusDays(5 + m * 30L);
            if (d.isAfter(end)) {
                break;
            }
            BigDecimal amt = new BigDecimal("380000");
            hdfcBal = hdfcBal.add(amt);
            txns.add(txn(d, amt, "CREDIT", "NEFT CR CUSTOMER RECEIPT ACME TRADERS", "NEFT",
                    hdfcBal, "SIM-CR-NEFT-" + m, "HDFC|5678", seq++));
            BigDecimal upi = new BigDecimal("45000");
            hdfcBal = hdfcBal.add(upi);
            txns.add(txn(d.plusDays(10), upi, "CREDIT", "UPI CR CUSTOMER PAYMENT", "UPI",
                    hdfcBal, "SIM-CR-UPI-" + m, "HDFC|5678", seq++));
        }

        // Supplier payments
        for (int m = 0; m < 5; m++) {
            LocalDate d = start.plusDays(12 + m * 30L);
            if (d.isAfter(end)) {
                break;
            }
            BigDecimal amt = new BigDecimal("120000");
            hdfcBal = hdfcBal.subtract(amt);
            txns.add(txn(d, amt, "DEBIT", "NEFT SUPPLIER PAYMENT STEEL VENDOR", "NEFT",
                    hdfcBal, "SIM-SUP-" + m, "HDFC|5678", seq++));
        }

        // One cash deposit on SAVINGS
        LocalDate cashDate = start.plusDays(40);
        BigDecimal cash = new BigDecimal("50000");
        sbiBal = sbiBal.add(cash);
        txns.add(txn(cashDate, cash, "CREDIT", "CASH DEPOSIT BY CASH", "CASH",
                sbiBal, "SIM-CASH-1", "SBI|1234", seq++));

        // One cheque return narration
        LocalDate chqDate = start.plusDays(70);
        BigDecimal chq = new BigDecimal("25000");
        hdfcBal = hdfcBal.subtract(chq);
        txns.add(txn(chqDate, chq, "DEBIT", "CHEQUE RETURN INSUFFICIENT FUNDS CHQ 445566", "CHEQUE",
                hdfcBal, "SIM-CHQ-RET-1", "HDFC|5678", seq++));

        // One self-transfer
        LocalDate selfDate = start.plusDays(90);
        BigDecimal selfAmt = new BigDecimal("100000");
        hdfcBal = hdfcBal.subtract(selfAmt);
        txns.add(txn(selfDate, selfAmt, "DEBIT", "NEFT SELF TRANSFER OWN A/C", "NEFT",
                hdfcBal, "SIM-SELF-1", "HDFC|5678", seq++));
        sbiBal = sbiBal.add(selfAmt);
        txns.add(txn(selfDate, selfAmt, "CREDIT", "NEFT SELF TRANSFER FROM OWN A/C", "NEFT",
                sbiBal, "SIM-SELF-2", "SBI|1234", seq++));

        // One loan disbursement credit
        LocalDate loanDate = start.plusDays(110);
        BigDecimal loan = new BigDecimal("500000");
        hdfcBal = hdfcBal.add(loan);
        txns.add(txn(loanDate, loan, "CREDIT", "LOAN DISBURSEMENT TERM LOAN CREDIT", "NEFT",
                hdfcBal, "SIM-LOAN-DISB-1", "HDFC|5678", seq++));

        // A few interest / bank charge / GST for classifier coverage
        LocalDate intDate = start.plusDays(50);
        BigDecimal interest = new BigDecimal("1200");
        sbiBal = sbiBal.add(interest);
        txns.add(txn(intDate, interest, "CREDIT", "INTEREST CREDIT SB INT", "INTEREST",
                sbiBal, "SIM-INT-1", "SBI|1234", seq++));

        LocalDate chargeDate = start.plusDays(55);
        BigDecimal charge = new BigDecimal("300");
        hdfcBal = hdfcBal.subtract(charge);
        txns.add(txn(chargeDate, charge, "DEBIT", "BANK CHARGE SMS CHARGE", "BANK_CHARGE",
                hdfcBal, "SIM-CHG-1", "HDFC|5678", seq++));

        LocalDate gstDate = start.plusDays(100);
        BigDecimal gst = new BigDecimal("18000");
        hdfcBal = hdfcBal.subtract(gst);
        txns.add(txn(gstDate, gst, "DEBIT", "GST PAYMENT CGST SGST", "NEFT",
                hdfcBal, "SIM-GST-1", "HDFC|5678", seq++));

        return txns;
    }

    private static Map<String, Object> txn(
            LocalDate date, BigDecimal amount, String type, String narration, String mode,
            BigDecimal balance, String txnId, String accountKey, int seq) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("amount", amount);
        m.put("type", type);
        m.put("narration", narration);
        m.put("mode", mode);
        m.put("balance", balance);
        m.put("txnId", txnId);
        m.put("accountKey", accountKey);
        m.put("seq", seq);
        return m;
    }

    public static Map<String, Object> parseSetuFiPayload(JsonNode root) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        collectAccounts(root, accounts);

        List<Map<String, Object>> transactions = new ArrayList<>();
        collectTransactions(root, transactions, null);

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
        if (!transactions.isEmpty()) {
            summary.put("transactions", transactions);
        }
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

    /**
     * Collect Transactions/transactions arrays from FI JSON when present — do not invent.
     */
    private static void collectTransactions(
            JsonNode node, List<Map<String, Object>> out, String accountKeyHint) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String resolvedKey = accountKeyHint;
            if (looksLikeAccount(node)) {
                String bank = firstText(node, "bank", "bankName", "fipName", "linkedAccRef");
                String masked = firstText(node, "maskedAccNumber", "accountNumber");
                if (masked != null && masked.length() >= 4) {
                    resolvedKey = bank + "|" + masked.substring(masked.length() - 4);
                }
            }
            final String localKey = resolvedKey;
            for (String key : List.of("Transactions", "transactions", "Transaction", "txnList")) {
                if (node.has(key) && node.get(key).isArray()) {
                    for (JsonNode t : node.get(key)) {
                        Map<String, Object> parsed = toTransactionSummary(t, localKey);
                        if (parsed != null) {
                            out.add(parsed);
                        }
                    }
                }
            }
            node.fields().forEachRemaining(entry ->
                    collectTransactions(entry.getValue(), out, localKey));
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                collectTransactions(element, out, accountKeyHint);
            }
        }
    }

    private static Map<String, Object> toTransactionSummary(JsonNode node, String accountKey) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String amountText = firstText(node, "amount", "txnAmount", "transactionAmount");
        if ((amountText == null || amountText.isEmpty()) && !node.has("amount")) {
            // Still allow numeric amount field
            if (!node.has("txnAmount") && !node.has("transactionAmount")) {
                return null;
            }
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("date", firstText(node, "date", "transactionDate", "valueDate", "txnDate"));
        t.put("amount", readDecimal(node, "amount", "txnAmount", "transactionAmount"));
        String type = firstText(node, "type", "transactionType", "creditDebitIndicator", "modeOfTxn");
        if (type != null && (type.contains("DEBIT") || type.equals("D") || type.equals("DR"))) {
            t.put("type", "DEBIT");
        } else if (type != null && (type.contains("CREDIT") || type.equals("C") || type.equals("CR"))) {
            t.put("type", "CREDIT");
        } else {
            // Infer from amount sign if present
            BigDecimal amt = readDecimal(node, "amount", "txnAmount", "transactionAmount");
            t.put("type", amt != null && amt.signum() < 0 ? "DEBIT" : "CREDIT");
            if (amt != null && amt.signum() < 0) {
                t.put("amount", amt.abs());
            }
        }
        t.put("narration", firstText(node, "narration", "description", "txnDesc", "remarks"));
        t.put("mode", firstText(node, "mode", "paymentMode", "type"));
        BigDecimal bal = readDecimal(node, "balance", "currentBalance", "balanceAfterTransaction");
        if (bal != null) {
            t.put("balance", bal);
        }
        String txnId = firstText(node, "txnId", "transactionId", "id");
        if (txnId != null && !txnId.isEmpty()) {
            t.put("txnId", txnId);
        }
        String utr = firstText(node, "utr", "reference", "refNo");
        if (utr != null && !utr.isEmpty()) {
            t.put("utr", utr);
        }
        if (accountKey != null) {
            t.put("accountKey", accountKey);
        }
        return t;
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
        account.put("type", textOr(node, "DEPOSIT", "type", "fitype", "accountType", "accountSubType", "summary"));
        account.put("bank", textOr(node, "UNKNOWN", "bank", "bankName", "fipName", "linkedAccRef"));
        account.put("balance", readLong(node, "balance", "currentBalance", "currentBalanceAmount"));
        account.put("avgMonthlyBalance", readLong(node, "avgMonthlyBalance", "averageBalance"));
        account.put("txnCount6Months", readInt(node, "txnCount6Months", "transactionCount"));
        account.put("avgMonthlyInflow", readLong(node, "avgMonthlyInflow", "monthlyInflow"));
        account.put("avgMonthlyOutflow", readLong(node, "avgMonthlyOutflow", "monthlyOutflow"));
        account.put("bounceCount6Months", readInt(node, "bounceCount6Months", "bounceCount"));
        String masked = firstText(node, "maskedAccNumber", "accountNumber");
        if (masked != null && !masked.isEmpty()) {
            account.put("accountNumber", masked);
            if (masked.length() >= 4) {
                account.put("accountKey", account.get("bank") + "|" + masked.substring(masked.length() - 4));
            }
        }
        return account;
    }

    private static long readLong(JsonNode node, String... keys) {
        BigDecimal d = readDecimal(node, keys);
        return d != null ? d.longValue() : 0L;
    }

    private static BigDecimal readDecimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.decimalValue();
                }
                if (value.isTextual()) {
                    try {
                        return new BigDecimal(value.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // try next key
                    }
                }
            }
        }
        return null;
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
        return "";
    }

    private static String textOr(JsonNode node, String defaultValue, String... keys) {
        String v = firstText(node, keys);
        return v.isEmpty() ? defaultValue : v;
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
