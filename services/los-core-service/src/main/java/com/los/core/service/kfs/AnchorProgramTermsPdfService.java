package com.los.core.service.kfs;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.loan.ApplicationPartyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class AnchorProgramTermsPdfService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZoneId.of("Asia/Kolkata"));
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(0, 51, 102));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0, 51, 102));
    private static final Font SUBHEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(51, 51, 51));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(80, 80, 80));

    public byte[] generate(KfsDocument kfs, LoanApplication app) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 48, 42);
            PdfWriter.getInstance(document, baos);
            document.open();

            String anchorName = app != null ? ApplicationPartyResolver.resolveDisplayName(app) : "Anchor";
            Map<String, Object> details = extractProgramDetails(kfs);

            // —— Page 1: header + program/commercial tables ——
            Paragraph title = new Paragraph("ANCHOR PROGRAM TERMS & CONDITIONS", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6f);
            document.add(title);

            Paragraph meta = new Paragraph(
                    "Date: " + DATE_FMT.format(Instant.now())
                            + "  |  Application: " + (app != null ? app.getApplicationNumber() : "—")
                            + "  |  Anchor: " + anchorName,
                    VALUE_FONT);
            meta.setSpacingAfter(10f);
            document.add(meta);

            document.add(sectionHeader("1. Program Summary"));
            document.add(programTable(details, anchorName));
            gap(document, 6f);

            document.add(sectionHeader("2. Commercial & Operational Parameters"));
            document.add(commercialTable(details));

            document.newPage();

            // —— Page 2: overview, obligations, T&C ——
            document.add(sectionHeader("3. Facility Overview"));
            document.add(body(
                    "This Anchor Program Facility Agreement is between the Anchor named above and the Lender "
                            + "to establish a supply-chain finance program under which enrolled borrowers may obtain "
                            + "invoice discounting facilities against invoices accepted by the Anchor. The program "
                            + "operates within the limits, rates, and tenure in Section 1 and is subject to utilization "
                            + "monitoring and eligibility compliance."));
            gap(document, 4f);

            document.add(sectionHeader("4. Anchor Obligations"));
            addBullet(document, "Honour accepted invoices; cooperate with verification and audit requests.");
            addBullet(document, "Notify the Lender of material changes in business, credit profile, or payment behaviour.");
            addBullet(document, "Ensure invoice data on the platform is accurate and not disputed at submission.");
            addBullet(document, "Maintain invoice acceptance / rejection within agreed SLAs.");
            addBullet(document, "Participate in periodic program reviews and limit re-assessments.");
            gap(document, 6f);

            document.add(sectionHeader("5. Terms and Conditions"));

            document.add(subHeader("5.1 Program Limits & Utilization"));
            document.add(body(
                    "Disbursements are subject to umbrella program limit, per-borrower caps, sub-program limits, "
                            + "and available headroom at drawdown. The Lender may suspend new disbursements if utilization "
                            + "exceeds thresholds or the Anchor fails ongoing obligations."));

            document.add(subHeader("5.2 Interest, Fees & Tenure"));
            document.add(body(
                    "Interest accrues at the program rate on a reducing-balance basis unless stated otherwise. "
                            + "Processing fees, penal interest, and grace periods apply per product policy at disbursement. "
                            + "Maximum tenure per draw shall not exceed program tenure unless extended in writing."));

            document.add(subHeader("5.3 Invoice Acceptance & Discounting"));
            document.add(body(
                    "Only invoices meeting eligibility (minimum amount, age, due-date, margin) may be discounted. "
                            + "Anchor acceptance confirms genuineness of supply. Auto-accept / auto-discounting, if enabled, "
                            + "operates per configured operational parameters."));

            document.add(subHeader("5.4 Representations & Electronic Execution"));
            document.add(body(
                    "The Anchor represents authority to enter this Agreement and confirms review of program terms. "
                            + "This document is electronically signed by the authorised signatory. Upon eSign completion, "
                            + "the facility is deemed accepted and onboarding may proceed subject to compliance checks."));

            document.add(subHeader("5.5 Amendments & Termination"));
            document.add(body(
                    "The Lender may amend rates, fees, limits, or parameters with reasonable notice, and may pause or "
                            + "close the program for breach, regulatory requirement, or credit deterioration. Outstanding "
                            + "facilities remain governed by their sanction terms until fully repaid."));

            Object conditions = kfs.getAdditionalTerms() != null
                    ? kfs.getAdditionalTerms().get("conditionsText") : null;
            if (conditions != null && !String.valueOf(conditions).isBlank()) {
                gap(document, 4f);
                document.add(sectionHeader("6. Additional Conditions"));
                document.add(body(String.valueOf(conditions)));
            }

            gap(document, 8f);
            Paragraph footer = new Paragraph("— End of Anchor Program Terms —", SMALL_FONT);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Anchor program terms PDF failed: " + e.getMessage(), e);
        }
    }

    private static Paragraph sectionHeader(String text) {
        Paragraph p = new Paragraph(text, HEADER_FONT);
        p.setSpacingBefore(2f);
        p.setSpacingAfter(4f);
        return p;
    }

    private static Paragraph subHeader(String text) {
        Paragraph p = new Paragraph(text, SUBHEADER_FONT);
        p.setSpacingBefore(3f);
        p.setSpacingAfter(2f);
        return p;
    }

    private static Paragraph body(String text) {
        Paragraph p = new Paragraph(text, VALUE_FONT);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(11f);
        p.setSpacingAfter(2f);
        return p;
    }

    private static void gap(Document document, float pts) throws DocumentException {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(pts);
        document.add(p);
    }

    private static void addBullet(Document document, String text) throws DocumentException {
        Paragraph p = new Paragraph("• " + text, VALUE_FONT);
        p.setLeading(11f);
        p.setSpacingAfter(1f);
        p.setIndentationLeft(8f);
        document.add(p);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractProgramDetails(KfsDocument kfs) {
        if (kfs.getAdditionalTerms() == null) {
            return Map.of();
        }
        Object raw = kfs.getAdditionalTerms().get("programDetails");
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static PdfPTable programTable(Map<String, Object> details, String anchorName) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(2f);
        addRow(table, "Anchor", anchorName);
        addRow(table, "Program name", str(details.get("programName")));
        addRow(table, "Program code", str(details.get("programCode")));
        addRow(table, "Product type", str(details.get("productType")));
        addRow(table, "Program limit (INR)", str(details.get("programLimit")));
        addRow(table, "Max borrower limit", str(details.get("maxBorrowerLimit")));
        addRow(table, "Interest rate (p.a.)", str(details.get("interestRate")));
        addRow(table, "Tenure (days)", str(details.get("tenureDays")));
        addRow(table, "Validity period", str(details.get("validity")));
        addRow(table, "Flow type", str(details.get("flowType")));
        return table;
    }

    private static PdfPTable commercialTable(Map<String, Object> details) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        addRow(table, "Margin / haircut (%)", str(details.get("marginPercent")));
        addRow(table, "Processing fee", str(details.get("processingFee")));
        addRow(table, "Penal rate", str(details.get("penalRate")));
        addRow(table, "Grace period (days)", str(details.get("gracePeriodDays")));
        addRow(table, "Sanction type", str(details.get("sanctionType")));
        addRow(table, "Auto discounting", str(details.get("autoDiscounting")));
        addRow(table, "Auto accept invoices", str(details.get("autoAcceptInvoices")));
        addRow(table, "LMS integration", str(details.get("lmsEntryIn")));
        return table;
    }

    private static void addRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, VALUE_FONT));
        PdfPCell v = new PdfPCell(new Phrase(value != null && !value.isBlank() ? value : "—", VALUE_FONT));
        l.setBorderWidth(0.5f);
        v.setBorderWidth(0.5f);
        l.setPadding(3);
        v.setPadding(3);
        table.addCell(l);
        table.addCell(v);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
