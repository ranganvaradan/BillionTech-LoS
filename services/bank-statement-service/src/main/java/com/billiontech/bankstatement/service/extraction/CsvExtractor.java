package com.billiontech.bankstatement.service.extraction;

import com.billiontech.bankstatement.exception.StatementProcessingException;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvExtractor {

    private final BankFormatDetector bankFormatDetector;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    public ParsedStatement extract(InputStream inputStream) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                throw new StatementProcessingException("CSV file is empty");
            }

            StringBuilder allText = new StringBuilder();
            for (String[] row : allRows) {
                allText.append(String.join(" ", row)).append("\n");
            }

            BankFormatDetector.DetectedBank detectedBank = bankFormatDetector.detect(allText.toString());

            ParsedStatement statement = ParsedStatement.builder()
                    .bankCode(detectedBank.getBankCode())
                    .bankName(detectedBank.getBankName())
                    .ifscCode(detectedBank.getIfscCode())
                    .transactions(new ArrayList<>())
                    .build();

            int headerIdx = findHeaderRow(allRows);
            if (headerIdx < 0) {
                log.warn("Could not find header row in CSV");
                return statement;
            }

            Map<String, Integer> columnMap = mapColumns(allRows.get(headerIdx));
            List<ParsedTransaction> transactions = new ArrayList<>();

            for (int i = headerIdx + 1; i < allRows.size(); i++) {
                ParsedTransaction txn = parseRow(allRows.get(i), columnMap);
                if (txn != null) {
                    transactions.add(txn);
                }
            }

            statement.setTransactions(transactions);

            if (!transactions.isEmpty()) {
                statement.setStatementFromDate(transactions.get(0).getTransactionDate());
                statement.setStatementToDate(transactions.get(transactions.size() - 1).getTransactionDate());
                ParsedTransaction last = transactions.get(transactions.size() - 1);
                if (last.getRunningBalance() != null) {
                    statement.setClosingBalance(last.getRunningBalance());
                }
            }

            return statement;
        } catch (StatementProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementProcessingException("Failed to extract CSV: " + e.getMessage(), e);
        }
    }

    private int findHeaderRow(List<String[]> rows) {
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String rowText = String.join(" ", rows.get(i)).toUpperCase();
            if (rowText.contains("DATE") && (rowText.contains("DEBIT") || rowText.contains("AMOUNT") || rowText.contains("NARRATION"))) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(String[] headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String header = headerRow[i].toUpperCase().trim();
            if (header.contains("DATE") && !header.contains("VALUE")) {
                map.putIfAbsent("DATE", i);
            } else if (header.contains("VALUE") && header.contains("DATE")) {
                map.put("VALUE_DATE", i);
            } else if (header.contains("NARRATION") || header.contains("DESCRIPTION") || header.contains("PARTICULARS")) {
                map.put("NARRATION", i);
            } else if (header.contains("DEBIT") || header.contains("WITHDRAWAL")) {
                map.put("DEBIT", i);
            } else if (header.contains("CREDIT") || header.contains("DEPOSIT")) {
                map.put("CREDIT", i);
            } else if (header.contains("BALANCE")) {
                map.putIfAbsent("BALANCE", i);
            } else if (header.contains("REF") || header.contains("CHQ")) {
                map.put("REFERENCE", i);
            }
        }
        return map;
    }

    private ParsedTransaction parseRow(String[] cells, Map<String, Integer> columnMap) {
        try {
            Integer dateIdx = columnMap.get("DATE");
            if (dateIdx == null || dateIdx >= cells.length) return null;

            LocalDate txnDate = parseDate(cells[dateIdx].trim());
            if (txnDate == null) return null;

            ParsedTransaction.ParsedTransactionBuilder builder = ParsedTransaction.builder()
                    .transactionDate(txnDate)
                    .valueDate(txnDate);

            Integer valueDateIdx = columnMap.get("VALUE_DATE");
            if (valueDateIdx != null && valueDateIdx < cells.length) {
                LocalDate vd = parseDate(cells[valueDateIdx].trim());
                if (vd != null) builder.valueDate(vd);
            }

            Integer narIdx = columnMap.get("NARRATION");
            if (narIdx != null && narIdx < cells.length) {
                builder.narration(cells[narIdx].trim());
            }

            Integer refIdx = columnMap.get("REFERENCE");
            if (refIdx != null && refIdx < cells.length) {
                builder.referenceNumber(cells[refIdx].trim());
            }

            Integer debitIdx = columnMap.get("DEBIT");
            if (debitIdx != null && debitIdx < cells.length) {
                builder.debitAmount(parseBigDecimal(cells[debitIdx]));
            }

            Integer creditIdx = columnMap.get("CREDIT");
            if (creditIdx != null && creditIdx < cells.length) {
                builder.creditAmount(parseBigDecimal(cells[creditIdx]));
            }

            Integer balIdx = columnMap.get("BALANCE");
            if (balIdx != null && balIdx < cells.length) {
                builder.runningBalance(parseBigDecimal(cells[balIdx]));
            }

            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr.trim(), fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
