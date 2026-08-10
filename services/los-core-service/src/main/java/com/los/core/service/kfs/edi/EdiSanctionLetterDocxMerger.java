package com.los.core.service.kfs.edi;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the BL sanction / KFS Word template (legacy {@code editIndividualSanctionFile} parity).
 */
@Component
public class EdiSanctionLetterDocxMerger {

    public byte[] merge(InputStream templateStream, EdiKfsTemplateContext ctx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(templateStream)) {
            Map<String, String> tokens = buildTokenMap(ctx);
            XWPFTable repaymentSchedulesTable = null;
            XWPFTable repaymentScheduleFormTable = null;

            for (XWPFTable tbl : doc.getTables()) {
                if (!tbl.getRows().isEmpty()) {
                    String header = tableTitle(tbl);
                    if (header.contains("Repayment Schedules")) {
                        repaymentSchedulesTable = tbl;
                    } else if ("Repayment Schedule".equals(header)) {
                        repaymentScheduleFormTable = tbl;
                    }
                }
                replaceInTable(tbl, tokens);
            }

            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, tokens);
            }

            appendScheduleTable(repaymentSchedulesTable, ctx.scheduleRows(), true);
            appendScheduleTable(repaymentScheduleFormTable, ctx.scheduleRows(), false);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    public byte[] merge(byte[] templateBytes, EdiKfsTemplateContext ctx) throws IOException {
        return merge(new ByteArrayInputStream(templateBytes), ctx);
    }

    private static String tableTitle(XWPFTable tbl) {
        return tbl.getRow(0).getCell(0).getText().replace('\u00A0', ' ').trim();
    }

    private static Map<String, String> buildTokenMap(EdiKfsTemplateContext ctx) {
        Map<String, String> m = new HashMap<>();
        m.put("NewDate", ctx.currentDate());
        m.put("<Name>", ctx.fullName());
        m.put("<address>", ctx.address());
        m.put("ExpiryDate", ctx.expiryDate());
        m.put("<Limit>", ctx.limitRs());
        m.put("<Tenor>", ctx.tenor());
        m.put("InterestRate", ctx.interestRate());
        m.put("AppRefNo", ctx.appRefNo());
        m.put("LoanAmount", ctx.loanAmountRs());
        m.put("LoanAmtText", ctx.loanAmtText());
        m.put("KFSExpDate", ctx.kfsExpDate());
        m.put("RepDate", ctx.firstRepaymentDate());
        m.put("TypeOfInstalment", ctx.typeOfInstalment());
        m.put("TotalInterest", ctx.totalInterestRs());
        m.put("NetDisburseAmount", ctx.netDisburseAmountRs());
        m.put("TotalAmount", ctx.totalAmountRs());
        m.put("AnnualPercentage", ctx.annualPercentage());
        m.put("ReducingBalanceBasics", ctx.reducingBalanceBasics());
        m.put("RepaymentFrequency", ctx.repaymentFrequency());
        m.put("NoOfInstalments", ctx.noOfInstalments());
        m.put("AmountOfEachInstalment", ctx.amountOfEachInstalmentRs());
        m.put("PenalInterest", ctx.penalInterest());
        m.put("NetInrRate", ctx.interestRate());
        return m;
    }

    private static void replaceInTable(XWPFTable tbl, Map<String, String> tokens) {
        for (XWPFTableRow row : tbl.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, tokens);
                }
            }
        }
    }

    private static void replaceInParagraph(XWPFParagraph p, Map<String, String> tokens) {
        for (XWPFRun r : p.getRuns()) {
            String text = runText(r);
            if (text == null || text.isEmpty()) {
                continue;
            }
            String updated = applyTokens(text, tokens);
            if (!updated.equals(text)) {
                r.setText(updated, 0);
            }
        }
    }

    private static String applyTokens(String text, Map<String, String> tokens) {
        String result = text;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            if (result.contains(e.getKey())) {
                result = result.replace(e.getKey(), e.getValue() != null ? e.getValue() : "");
            }
        }
        return result;
    }

    private static String runText(XWPFRun r) {
        try {
            return r.getText(0);
        } catch (Exception e) {
            return r.text();
        }
    }

    /**
     * Appends Encore repayment rows — same column order and formatting as bl-core
     * {@code EmSignInfoServiceFacadeImpl#editIndividualSanctionFile}.
     */
    private static void appendScheduleTable(XWPFTable table, List<EdiKfsScheduleRow> rows, boolean summaryLayout) {
        if (table == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (EdiKfsScheduleRow scheduleRow : rows) {
            XWPFTableRow dataRow = table.createRow();
            for (int i = 0; i < 5; i++) {
                XWPFTableCell cell = dataRow.getCell(i);
                if (cell == null) {
                    cell = dataRow.createCell();
                }

                XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                        ? cell.addParagraph()
                        : cell.getParagraphs().get(0);
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                clearRuns(paragraph);

                XWPFRun run = paragraph.createRun();
                run.setFontFamily("Calibri");
                run.setFontSize(summaryLayout ? 10 : 8);
                run.setText(formatScheduleCell(scheduleRow, i, summaryLayout));
            }
        }
    }

    private static void clearRuns(XWPFParagraph paragraph) {
        int runs = paragraph.getRuns().size();
        for (int i = runs - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
    }

    private static String formatScheduleCell(EdiKfsScheduleRow row, int column, boolean summaryLayout) {
        if (summaryLayout) {
            return switch (column) {
                case 0 -> row.demandDate();
                case 1 -> rs(row.installmentAmount());
                case 2 -> rs(row.normalInterestAmount());
                case 3 -> rs(row.principalAmount());
                case 4 -> rs(row.balance());
                default -> "";
            };
        }
        return switch (column) {
            case 0 -> String.valueOf(row.demandNumber());
            case 1 -> rs(row.balance());
            case 2 -> rs(row.normalInterestAmount());
            case 3 -> rs(row.principalAmount());
            case 4 -> rs(row.installmentAmount());
            default -> "";
        };
    }

    /** bl-core uses {@code "Rs. " + amount} (note space after period). */
    private static String rs(BigDecimal amount) {
        if (amount == null) {
            return "Rs. 0";
        }
        String plain = amount.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return "Rs. " + plain;
    }
}
