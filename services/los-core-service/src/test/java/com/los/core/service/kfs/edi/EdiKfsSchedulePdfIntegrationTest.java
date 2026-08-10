package com.los.core.service.kfs.edi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EdiKfsSchedulePdfIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void mergedDocxWithSchedule_producesLargerPdfThanHeadersOnly() throws Exception {
        byte[] template;
        try (var in = getClass().getResourceAsStream("/kfs/templates/BL_SanctionLetter_Template_V3.docx")) {
            template = in.readAllBytes();
        }

        EdiKfsTemplateContext emptySchedule = context(List.of());
        EdiKfsTemplateContext withSchedule = context(List.of(
                new EdiKfsScheduleRow(1, "2026-07-01", bd("600"), bd("50"), bd("550"), bd("49450")),
                new EdiKfsScheduleRow(2, "2026-07-02", bd("600"), bd("49"), bd("551"), bd("48899"))));

        EdiSanctionLetterDocxMerger merger = new EdiSanctionLetterDocxMerger();
        EmbeddedDocxToPdfConverter converter = new EmbeddedDocxToPdfConverter();

        Path docxEmpty = tempDir.resolve("empty.docx");
        Path docxFull = tempDir.resolve("full.docx");
        Files.write(docxEmpty, merger.merge(template, emptySchedule));
        Files.write(docxFull, merger.merge(template, withSchedule));

        byte[] pdfEmpty = converter.convert(docxEmpty);
        byte[] pdfFull = converter.convert(docxFull);

        assertTrue(pdfFull.length > pdfEmpty.length + 500,
                "PDF with schedule rows should be larger (empty=" + pdfEmpty.length + ", full=" + pdfFull.length + ")");
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static EdiKfsTemplateContext context(List<EdiKfsScheduleRow> schedule) {
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
