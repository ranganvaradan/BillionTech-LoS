package com.los.core.service.kfs.edi;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EdiSanctionLetterDocxMergerTemplateTest {

    @Test
    void merge_with90Rows_appendsAllRowsToKfsTable() throws Exception {
        byte[] template = loadTemplate();
        List<EdiKfsScheduleRow> rows = new ArrayList<>();
        for (int i = 1; i <= 90; i++) {
            rows.add(new EdiKfsScheduleRow(
                    i, "2026-07-01", bd("600"), bd("50"), bd("550"), bd(String.valueOf(50000 - i * 550))));
        }
        byte[] merged = new EdiSanctionLetterDocxMerger().merge(template, sampleContext(rows));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(merged))) {
            XWPFTable kfsSchedule = doc.getTables().get(6);
            assertTrue(kfsSchedule.getRows().size() >= 92, "expected title+header+90 data rows");
        }
    }

    @Test
    void merge_populatesKfsRepaymentScheduleTable() throws Exception {
        byte[] template = loadTemplate();
        EdiKfsTemplateContext ctx = sampleContext(List.of(
                new EdiKfsScheduleRow(1, "2026-07-01", bd("600"), bd("50"), bd("550"), bd("49450")),
                new EdiKfsScheduleRow(2, "2026-07-02", bd("600"), bd("49"), bd("551"), bd("48899"))));

        byte[] merged = new EdiSanctionLetterDocxMerger().merge(template, ctx);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(merged))) {
            XWPFTable kfsSchedule = doc.getTables().get(6);
            assertTrue(kfsSchedule.getRows().size() > 2, "expected header + data rows, got " + kfsSchedule.getRows().size());
            int lastIdx = kfsSchedule.getRows().size() - 1;
            StringBuilder lastRowText = new StringBuilder();
            for (var cell : kfsSchedule.getRow(lastIdx).getTableCells()) {
                lastRowText.append(cell.getText());
            }
            assertTrue(lastRowText.toString().contains("600") || lastRowText.toString().contains("Rs."),
                    "last row should contain instalment: " + lastRowText);

            XWPFTable summarySchedule = doc.getTables().get(8);
            assertTrue(summarySchedule.getRows().size() > 2, "summary schedule rows=" + summarySchedule.getRows().size());
        }
    }

    private static byte[] loadTemplate() throws Exception {
        try (var in = EdiSanctionLetterDocxMergerTemplateTest.class.getResourceAsStream(
                "/kfs/templates/BL_SanctionLetter_Template_V3.docx")) {
            return in.readAllBytes();
        }
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static EdiKfsTemplateContext sampleContext(List<EdiKfsScheduleRow> schedule) {
        return new EdiKfsTemplateContext(
                "01-Jul-2026",
                "Test Borrower",
                "Chennai",
                "Rs.50000",
                "Rs.50000",
                "50000",
                "fifty thousand",
                "12",
                "90",
                "01-Oct-2026",
                "LOS-TEST-1",
                "04-Jul-2026",
                "2026-07-02",
                "Daily",
                "Day",
                "Rs.5000",
                "Rs.50000",
                "Rs.55000",
                "20.5",
                "reducing",
                "90",
                "Rs.600",
                "32.5",
                schedule);
    }
}
