package com.billiontech.bankstatement.service.extraction;

import com.billiontech.bankstatement.exception.StatementProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfExtractor {

    private final BankFormatDetector bankFormatDetector;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([\\d,]+\\.\\d{2})");

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
            "(?:A/?c|Account)\\s*(?:No|Number|#)?\\s*[.:]*\\s*([\\dXx*]+[\\d]{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IFSC_PATTERN = Pattern.compile(
            "(?:IFSC|IFS)\\s*(?:Code)?\\s*[.:]*\\s*([A-Z]{4}0[A-Z0-9]{6})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:Name|Account Holder|Customer Name)\\s*[.:]*\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BRANCH_PATTERN = Pattern.compile(
            "(?:Branch)\\s*[.:]*\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\s*(?:to|-)\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    public ParsedStatement extract(InputStream inputStream, String password) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            PDDocument document;
            if (password != null && !password.isBlank()) {
                document = Loader.loadPDF(bytes, password);
            } else {
                document = Loader.loadPDF(bytes);
            }

            try (document) {
                PDFTextStripper stripper = new PDFTextStripper();
                String fullText = stripper.getText(document);
                return parseText(fullText);
            }
        } catch (StatementProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementProcessingException("Failed to extract PDF: " + e.getMessage(), e);
        }
    }

    ParsedStatement parseText(String fullText) {
        String[] lines = fullText.split("\\r?\\n");

        BankFormatDetector.DetectedBank detectedBank = bankFormatDetector.detect(fullText);

        ParsedStatement statement = ParsedStatement.builder()
                .bankCode(detectedBank.getBankCode())
                .bankName(detectedBank.getBankName())
                .ifscCode(detectedBank.getIfscCode())
                .transactions(new ArrayList<>())
                .build();

        extractMetadata(lines, statement);

        List<ParsedTransaction> transactions = extractTransactions(lines);
        reconcileAmounts(transactions);
        statement.setTransactions(transactions);

        if (!transactions.isEmpty()) {
            if (statement.getStatementFromDate() == null) {
                statement.setStatementFromDate(transactions.get(0).getTransactionDate());
            }
            if (statement.getStatementToDate() == null) {
                statement.setStatementToDate(transactions.get(transactions.size() - 1).getTransactionDate());
            }
            if (statement.getOpeningBalance() == null && transactions.get(0).getRunningBalance() != null) {
                BigDecimal firstBalance = transactions.get(0).getRunningBalance();
                BigDecimal firstCredit = Optional.ofNullable(transactions.get(0).getCreditAmount()).orElse(BigDecimal.ZERO);
                BigDecimal firstDebit = Optional.ofNullable(transactions.get(0).getDebitAmount()).orElse(BigDecimal.ZERO);
                statement.setOpeningBalance(firstBalance.subtract(firstCredit).add(firstDebit));
            }
            if (statement.getClosingBalance() == null) {
                ParsedTransaction lastTxn = transactions.get(transactions.size() - 1);
                if (lastTxn.getRunningBalance() != null) {
                    statement.setClosingBalance(lastTxn.getRunningBalance());
                }
            }
        }

        return statement;
    }

    /**
     * For 2-amount lines (amount + balance), use running balance progression
     * to determine whether the amount is a debit or credit.
     */
    private void reconcileAmounts(List<ParsedTransaction> transactions) {
        for (int i = 0; i < transactions.size(); i++) {
            ParsedTransaction txn = transactions.get(i);
            // Only process transactions where credit is null (2-amount lines pending reconciliation)
            if (txn.getCreditAmount() != null || txn.getDebitAmount() == null || txn.getRunningBalance() == null) {
                continue;
            }
            BigDecimal amount = txn.getDebitAmount();
            BigDecimal prevBalance = null;
            if (i > 0 && transactions.get(i - 1).getRunningBalance() != null) {
                prevBalance = transactions.get(i - 1).getRunningBalance();
            }
            if (prevBalance != null) {
                // If balance increased, this is a credit; if decreased, a debit
                BigDecimal balanceDiff = txn.getRunningBalance().subtract(prevBalance);
                if (balanceDiff.compareTo(BigDecimal.ZERO) > 0) {
                    txn.setCreditAmount(amount);
                    txn.setDebitAmount(BigDecimal.ZERO);
                } else {
                    txn.setCreditAmount(BigDecimal.ZERO);
                }
            } else {
                // No previous balance to compare — default to debit
                txn.setCreditAmount(BigDecimal.ZERO);
            }
        }
    }

    private void extractMetadata(String[] lines, ParsedStatement statement) {
        int headerLines = Math.min(30, lines.length);
        StringBuilder headerText = new StringBuilder();
        for (int i = 0; i < headerLines; i++) {
            headerText.append(lines[i]).append("\n");
        }
        String header = headerText.toString();

        Matcher acMatcher = ACCOUNT_NUMBER_PATTERN.matcher(header);
        if (acMatcher.find()) {
            statement.setAccountNumber(maskAccountNumber(acMatcher.group(1).trim()));
        }

        if (statement.getIfscCode() == null) {
            Matcher ifscMatcher = IFSC_PATTERN.matcher(header);
            if (ifscMatcher.find()) {
                statement.setIfscCode(ifscMatcher.group(1).trim());
            }
        }

        Matcher nameMatcher = NAME_PATTERN.matcher(header);
        if (nameMatcher.find()) {
            statement.setAccountHolderName(nameMatcher.group(1).trim());
        }

        Matcher branchMatcher = BRANCH_PATTERN.matcher(header);
        if (branchMatcher.find()) {
            statement.setBranchName(branchMatcher.group(1).trim());
        }

        Matcher dateRangeMatcher = DATE_RANGE_PATTERN.matcher(header);
        if (dateRangeMatcher.find()) {
            statement.setStatementFromDate(parseDate(dateRangeMatcher.group(1)));
            statement.setStatementToDate(parseDate(dateRangeMatcher.group(2)));
        }
    }

    List<ParsedTransaction> extractTransactions(String[] lines) {
        List<ParsedTransaction> transactions = new ArrayList<>();
        boolean inTransactionSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (isHeaderLine(trimmed)) {
                inTransactionSection = true;
                continue;
            }

            if (inTransactionSection || containsDateAtStart(trimmed)) {
                inTransactionSection = true;
                ParsedTransaction txn = parseLine(trimmed);
                if (txn != null) {
                    transactions.add(txn);
                }
            }
        }

        return transactions;
    }

    private boolean isHeaderLine(String line) {
        String upper = line.toUpperCase();
        return (upper.contains("DATE") && upper.contains("DESCRIPTION") && (upper.contains("DEBIT") || upper.contains("WITHDRAWAL")))
                || (upper.contains("DATE") && upper.contains("NARRATION") && (upper.contains("DEBIT") || upper.contains("AMOUNT")))
                || (upper.contains("TXN DATE") || upper.contains("TRANSACTION DATE"))
                        && (upper.contains("BALANCE") || upper.contains("AMOUNT"));
    }

    private boolean containsDateAtStart(String line) {
        return line.matches("^\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}.*")
                || line.matches("^\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{2,4}.*");
    }

    private ParsedTransaction parseLine(String line) {
        try {
            String[] parts = line.split("\\s{2,}|\\t");
            if (parts.length < 3) return null;

            LocalDate txnDate = parseDate(parts[0].trim());
            if (txnDate == null) return null;

            ParsedTransaction.ParsedTransactionBuilder builder = ParsedTransaction.builder()
                    .transactionDate(txnDate);

            // Try to find narration and amounts
            List<BigDecimal> amounts = new ArrayList<>();
            StringBuilder narration = new StringBuilder();

            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                BigDecimal amount = parseAmount(part);
                if (amount != null) {
                    amounts.add(amount);
                } else if (!part.isEmpty()) {
                    if (narration.length() > 0) narration.append(" ");
                    narration.append(part);
                }
            }

            builder.narration(narration.toString());

            // Assign amounts based on position
            if (amounts.size() >= 3) {
                // date | narration | debit | credit | balance
                builder.debitAmount(amounts.get(0));
                builder.creditAmount(amounts.get(1));
                builder.runningBalance(amounts.get(2));
            } else if (amounts.size() == 2) {
                // date | narration | amount | balance — direction determined in post-processing
                builder.runningBalance(amounts.get(1));
                // Temporarily store the amount; debit vs credit resolved by reconcileAmounts()
                builder.debitAmount(amounts.get(0));
                builder.creditAmount(null);
            } else if (amounts.size() == 1) {
                builder.runningBalance(amounts.get(0));
            }

            // Try to parse value date from narration if it contains a second date
            String narText = narration.toString();
            Matcher dateMatcher = Pattern.compile("(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})").matcher(narText);
            if (dateMatcher.find()) {
                LocalDate valueDate = parseDate(dateMatcher.group(1));
                if (valueDate != null) {
                    builder.valueDate(valueDate);
                }
            }
            if (builder.build().getValueDate() == null) {
                builder.valueDate(txnDate);
            }

            return builder.build();
        } catch (Exception e) {
            log.trace("Could not parse line: {}", line);
            return null;
        }
    }

    LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return null;
        amountStr = amountStr.trim().replace(",", "").replace("₹", "").replace("Rs.", "").replace("Rs", "").trim();
        if (amountStr.isEmpty() || amountStr.equals("-")) return null;
        try {
            return new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            Matcher m = AMOUNT_PATTERN.matcher(amountStr);
            if (m.find()) {
                try {
                    return new BigDecimal(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            return null;
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        String lastFour = accountNumber.substring(accountNumber.length() - 4);
        return "XXXX" + lastFour;
    }
}
