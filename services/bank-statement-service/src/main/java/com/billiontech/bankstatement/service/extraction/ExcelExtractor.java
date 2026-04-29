package com.billiontech.bankstatement.service.extraction;

import com.billiontech.bankstatement.exception.StatementProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExcelExtractor {

    private final BankFormatDetector bankFormatDetector;

    public ParsedStatement extract(InputStream inputStream, String fileName) {
        try {
            Workbook workbook;
            if (fileName.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }

            try (workbook) {
                Sheet sheet = workbook.getSheetAt(0);
                return parseSheet(sheet);
            }
        } catch (StatementProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementProcessingException("Failed to extract Excel: " + e.getMessage(), e);
        }
    }

    private ParsedStatement parseSheet(Sheet sheet) {
        StringBuilder allText = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();

        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (Cell cell : row) {
                String value = getCellValueAsString(cell);
                cells.add(value);
                allText.append(value).append(" ");
            }
            rows.add(cells);
            allText.append("\n");
        }

        BankFormatDetector.DetectedBank detectedBank = bankFormatDetector.detect(allText.toString());

        ParsedStatement statement = ParsedStatement.builder()
                .bankCode(detectedBank.getBankCode())
                .bankName(detectedBank.getBankName())
                .ifscCode(detectedBank.getIfscCode())
                .transactions(new ArrayList<>())
                .build();

        // Find header row
        int headerRowIdx = findHeaderRow(rows);
        if (headerRowIdx < 0) {
            log.warn("Could not find header row in Excel statement");
            return statement;
        }

        Map<String, Integer> columnMap = mapColumns(rows.get(headerRowIdx));

        // Extract metadata from rows before header
        extractMetadataFromRows(rows, headerRowIdx, statement);

        // Extract transactions
        List<ParsedTransaction> transactions = new ArrayList<>();
        for (int i = headerRowIdx + 1; i < rows.size(); i++) {
            ParsedTransaction txn = parseRow(rows.get(i), columnMap, sheet.getRow(i));
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
    }

    private int findHeaderRow(List<List<String>> rows) {
        for (int i = 0; i < Math.min(20, rows.size()); i++) {
            String rowText = String.join(" ", rows.get(i)).toUpperCase();
            if ((rowText.contains("DATE") && (rowText.contains("DEBIT") || rowText.contains("WITHDRAWAL") || rowText.contains("AMOUNT")))
                    || (rowText.contains("DATE") && rowText.contains("NARRATION"))
                    || (rowText.contains("DATE") && rowText.contains("DESCRIPTION"))) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(List<String> headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i).toUpperCase().trim();
            if (header.contains("DATE") && !header.contains("VALUE")) {
                map.putIfAbsent("DATE", i);
            } else if (header.contains("VALUE") && header.contains("DATE")) {
                map.put("VALUE_DATE", i);
            } else if (header.contains("NARRATION") || header.contains("DESCRIPTION") || header.contains("PARTICULARS") || header.contains("REMARKS")) {
                map.put("NARRATION", i);
            } else if (header.contains("DEBIT") || header.contains("WITHDRAWAL") || header.contains("DR")) {
                map.put("DEBIT", i);
            } else if (header.contains("CREDIT") || header.contains("DEPOSIT") || header.contains("CR")) {
                map.put("CREDIT", i);
            } else if (header.contains("BALANCE") || header.contains("CLOSING")) {
                map.putIfAbsent("BALANCE", i);
            } else if (header.contains("REF") || header.contains("CHQ") || header.contains("CHEQUE")) {
                map.put("REFERENCE", i);
            }
        }
        return map;
    }

    private void extractMetadataFromRows(List<List<String>> rows, int headerRow, ParsedStatement statement) {
        for (int i = 0; i < headerRow; i++) {
            String rowText = String.join(" ", rows.get(i));
            if (rowText.toUpperCase().contains("ACCOUNT") && rowText.matches(".*\\d{4,}.*")) {
                String accountNum = rowText.replaceAll(".*?(\\d[\\dXx*]+\\d{4}).*", "$1");
                if (accountNum.length() >= 4) {
                    statement.setAccountNumber("XXXX" + accountNum.substring(accountNum.length() - 4));
                }
            }
            if (rowText.toUpperCase().contains("NAME") || rowText.toUpperCase().contains("HOLDER")) {
                String[] parts = rowText.split("[.:]+");
                if (parts.length > 1) {
                    statement.setAccountHolderName(parts[1].trim());
                }
            }
        }
    }

    private ParsedTransaction parseRow(List<String> cells, Map<String, Integer> columnMap, Row excelRow) {
        try {
            Integer dateIdx = columnMap.get("DATE");
            if (dateIdx == null || dateIdx >= cells.size()) return null;

            LocalDate txnDate = parseDateFromCell(excelRow != null ? excelRow.getCell(dateIdx) : null, cells.get(dateIdx));
            if (txnDate == null) return null;

            ParsedTransaction.ParsedTransactionBuilder builder = ParsedTransaction.builder()
                    .transactionDate(txnDate);

            Integer valueDateIdx = columnMap.get("VALUE_DATE");
            if (valueDateIdx != null && valueDateIdx < cells.size()) {
                LocalDate valueDate = parseDateFromCell(excelRow != null ? excelRow.getCell(valueDateIdx) : null, cells.get(valueDateIdx));
                builder.valueDate(valueDate != null ? valueDate : txnDate);
            } else {
                builder.valueDate(txnDate);
            }

            Integer narrationIdx = columnMap.get("NARRATION");
            if (narrationIdx != null && narrationIdx < cells.size()) {
                builder.narration(cells.get(narrationIdx).trim());
            }

            Integer refIdx = columnMap.get("REFERENCE");
            if (refIdx != null && refIdx < cells.size()) {
                builder.referenceNumber(cells.get(refIdx).trim());
            }

            Integer debitIdx = columnMap.get("DEBIT");
            if (debitIdx != null && debitIdx < cells.size()) {
                builder.debitAmount(parseBigDecimal(cells.get(debitIdx)));
            }

            Integer creditIdx = columnMap.get("CREDIT");
            if (creditIdx != null && creditIdx < cells.size()) {
                builder.creditAmount(parseBigDecimal(cells.get(creditIdx)));
            }

            Integer balanceIdx = columnMap.get("BALANCE");
            if (balanceIdx != null && balanceIdx < cells.size()) {
                builder.runningBalance(parseBigDecimal(cells.get(balanceIdx)));
            }

            return builder.build();
        } catch (Exception e) {
            log.trace("Could not parse Excel row: {}", cells);
            return null;
        }
    }

    private LocalDate parseDateFromCell(Cell cell, String textValue) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            if (date != null) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        return parseDate(textValue);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();
        try {
            return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {}
        try {
            return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception ignored) {}
        try {
            return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (Exception ignored) {}
        try {
            return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception ignored) {}
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

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    yield date != null ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString() : "";
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }
}
