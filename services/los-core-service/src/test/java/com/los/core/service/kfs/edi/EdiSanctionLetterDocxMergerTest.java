package com.los.core.service.kfs.edi;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EdiSanctionLetterDocxMergerTest {

    @Test
    void merge_replacesPlaceholdersInParagraph() throws Exception {
        byte[] template;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("Hello <Name>, limit <Limit>, ref AppRefNo");
            doc.write(baos);
            template = baos.toByteArray();
        }

        EdiKfsTemplateContext ctx = new EdiKfsTemplateContext(
                "01-Jul-2026",
                "Alice",
                "Coimbatore",
                "Rs.50000",
                "Rs.50000",
                "50000",
                "fifty thousand",
                "18",
                "90",
                "01-Oct-2026",
                "ACC-1",
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
                List.of());

        byte[] merged = new EdiSanctionLetterDocxMerger().merge(template, ctx);

        try (XWPFDocument out = new XWPFDocument(new ByteArrayInputStream(merged))) {
            String text = out.getParagraphs().get(0).getText();
            assertTrue(text.contains("Alice"));
            assertTrue(text.contains("Rs.50000"));
            assertTrue(text.contains("ACC-1"));
            assertTrue(!text.contains("<Name>"));
        }
    }

    @Test
    void merge_appendsRepaymentScheduleRows() throws Exception {
        byte[] template;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var table = doc.createTable(1, 1);
            table.getRow(0).getCell(0).setText("Repayment Schedules");
            doc.write(baos);
            template = baos.toByteArray();
        }

        EdiKfsScheduleRow row = new EdiKfsScheduleRow(
                1,
                "2026-07-01",
                new BigDecimal("600"),
                new BigDecimal("50"),
                new BigDecimal("550"),
                new BigDecimal("49400"));

        EdiKfsTemplateContext ctx = minimalContext(List.of(row));
        byte[] merged = new EdiSanctionLetterDocxMerger().merge(template, ctx);

        try (XWPFDocument out = new XWPFDocument(new ByteArrayInputStream(merged))) {
            assertTrue(out.getTables().get(0).getRows().size() >= 2);
        }
    }

    private static EdiKfsTemplateContext minimalContext(List<EdiKfsScheduleRow> rows) {
        return new EdiKfsTemplateContext(
                "01-Jul-2026", "Bob", "Addr", "Rs.1", "Rs.1", "1", "one", "1", "1", "02-Jul-2026", "R1",
                "04-Jul-2026", "2026-07-01", "Daily", "Day", "Rs.0", "Rs.1", "Rs.1", "1", "rb", "1", "Rs.1", "13",
                rows);
    }
}
