package com.los.core.service.kfs;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * KFS PDF Generation Service — generates Key Fact Statement PDFs
 * compliant with RBI DLD 2025 Master Direction guidelines.
 *
 * Requirements (BR-10):
 * - Must include: sanctioned amount, interest rate, APR, tenure, EMI, total cost
 * - Cooling-off period disclosure (72 hours default)
 * - Grievance mechanism details
 * - LSP/DLA disclosure
 * - Additional terms and charges
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KfsPdfGenerationService {

    private final LoanApplicationRepository applicationRepository;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final KfsDocumentRepository kfsDocumentRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0, 51, 102));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0, 51, 102));
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font DISCLAIMER_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));

    /**
     * Generate a KFS PDF document from a KfsDocument entity.
     *
     * @param kfs The KFS document entity with all loan terms
     * @return byte array containing the PDF
     */
    public byte[] generateKfsPdf(KfsDocument kfs) {
        if (isInvoiceDiscountingTermsDocument(kfs)) {
            LoanApplication app = applicationRepository.findById(kfs.getApplicationId()).orElse(null);
            return generateInvoiceDiscountingTermsPdf(kfs, app);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new KfsPageEventHelper());

            document.open();

            // Title
            Paragraph title = new Paragraph("KEY FACT STATEMENT (KFS)", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                    "As per RBI Master Direction on Digital Lending (DL) 2025", SMALL_FONT);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15);
            document.add(subtitle);

            // Date and version
            Paragraph meta = new Paragraph(
                    "Date: " + DATE_FMT.format(Instant.now()) + "  |  Version: " + kfs.getVersion(),
                    SMALL_FONT);
            meta.setAlignment(Element.ALIGN_RIGHT);
            meta.setSpacingAfter(10);
            document.add(meta);

            // Section 1: Loan Details
            document.add(sectionHeader("1. Loan Details"));
            document.add(loanDetailsTable(kfs));
            document.add(Chunk.NEWLINE);

            // Section 2: Cost Details
            document.add(sectionHeader("2. Cost of Loan"));
            document.add(costDetailsTable(kfs));
            document.add(Chunk.NEWLINE);

            // Section 3: Charges and Fees
            document.add(sectionHeader("3. Charges and Fees"));
            document.add(chargesTable(kfs));
            document.add(Chunk.NEWLINE);

            // Section 4: Cooling-Off Period
            document.add(sectionHeader("4. Cooling-Off Period / Look-Up Period"));
            document.add(coolingOffParagraph(kfs));
            document.add(Chunk.NEWLINE);

            // Section 5: Grievance Redressal Mechanism
            document.add(sectionHeader("5. Grievance Redressal Mechanism"));
            document.add(grievanceParagraph(kfs));
            document.add(Chunk.NEWLINE);

            // Section 6: LSP/DLA Disclosure
            document.add(sectionHeader("6. LSP / DLA Disclosure"));
            document.add(lspDisclosureParagraph(kfs));
            document.add(Chunk.NEWLINE);

            // Section 7: Additional Terms
            document.add(sectionHeader("7. Additional Terms and Conditions"));
            document.add(additionalTermsParagraph(kfs));
            document.add(Chunk.NEWLINE);

            // Disclaimer
            document.add(disclaimerParagraph());

            // Signature block
            document.add(Chunk.NEWLINE);
            document.add(signatureBlock());

            document.close();

            log.info("KFS PDF generated for application {} — version={}, size={}KB",
                    kfs.getApplicationId(), kfs.getVersion(), baos.size() / 1024);

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate KFS PDF for application {}: {}",
                    kfs.getApplicationId(), e.getMessage(), e);
            throw new RuntimeException("KFS PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Two-page sanction terms PDF for invoice discounting borrowers — same layout, margins, and
     * signature block placement as the standard KFS so EmSigner templates match.
     */
    public byte[] generateInvoiceDiscountingTermsPdf(KfsDocument kfs, LoanApplication app) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new KfsPageEventHelper());

            document.open();

            String partyName = app != null ? ApplicationPartyResolver.resolveDisplayName(app) : "Borrower";
            String appNo = app != null ? app.getApplicationNumber() : String.valueOf(kfs.getApplicationId());

            Paragraph title = new Paragraph("KEY FACT STATEMENT (KFS)", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                    "Invoice Discounting — Sanction Terms & Conditions (Program facility)", SMALL_FONT);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(10);
            document.add(subtitle);

            Paragraph meta = new Paragraph(
                    "Date: " + DATE_FMT.format(Instant.now()) + "  |  Application: " + appNo
                            + "  |  Version: " + kfs.getVersion(),
                    SMALL_FONT);
            meta.setAlignment(Element.ALIGN_RIGHT);
            meta.setSpacingAfter(10);
            document.add(meta);

            document.add(sectionHeader("1. Sanction / Facility Details"));
            document.add(invoiceDiscountingFacilityTable(kfs, partyName));
            document.add(Chunk.NEWLINE);

            document.add(sectionHeader("2. Cost of Facility"));
            document.add(invoiceDiscountingCostTable(kfs));
            document.add(Chunk.NEWLINE);

            document.add(sectionHeader("3. Charges and Fees"));
            document.add(chargesTable(kfs));
            document.add(Chunk.NEWLINE);

            document.add(sectionHeader("4. Cooling-Off Period / Look-Up Period"));
            document.add(coolingOffParagraph(kfs));
            document.add(Chunk.NEWLINE);

            document.newPage();

            document.add(sectionHeader("5. Grievance Redressal Mechanism"));
            document.add(grievanceParagraph(kfs));
            document.add(Chunk.NEWLINE);

            document.add(sectionHeader("6. LSP / DLA Disclosure"));
            document.add(lspDisclosureParagraph(kfs));
            document.add(Chunk.NEWLINE);

            document.add(sectionHeader("7. Sanction Terms and Conditions"));
            document.add(invoiceDiscountingTermsParagraph(kfs));
            document.add(Chunk.NEWLINE);

            document.add(disclaimerParagraph());
            document.add(invoiceDiscountingFacilityNote());

            document.add(Chunk.NEWLINE);
            document.add(signatureBlock());

            document.close();

            log.info("Invoice discounting terms PDF for application {} — version={}, pages=2, size={}KB",
                    kfs.getApplicationId(), kfs.getVersion(), baos.size() / 1024);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice discounting terms PDF for {}: {}",
                    kfs.getApplicationId(), e.getMessage(), e);
            throw new RuntimeException("Invoice discounting terms PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback when eSign runs before a KFS row exists (legacy SANCTIONED-only applications).
     */
    public byte[] generateInvoiceDiscountingTermsPdfForApplication(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
        if (!InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
            throw new RuntimeException("Not an invoice discounting borrower application");
        }
        return kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .map(k -> generateInvoiceDiscountingTermsPdf(k, app))
                .orElseGet(() -> {
                    SanctionRecord rec = sanctionRecordRepository
                            .findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                            .orElseThrow(() -> new RuntimeException("No sanction record for application " + applicationId));
                    KfsDocument probe = KfsDocument.builder()
                            .applicationId(applicationId)
                            .version("v1")
                            .sanctionedAmount(rec.getApprovedAmount())
                            .interestRate(rec.getInterestRate())
                            .apr(rec.getInterestRate())
                            .tenureMonths(rec.getApprovedTenure() != null ? rec.getApprovedTenure() : 12)
                            .emiAmount(BigDecimal.ZERO)
                            .totalInterest(BigDecimal.ZERO)
                            .totalRepayment(rec.getApprovedAmount())
                            .processingFee(rec.getProcessingFee())
                            .coolingOffHours(72)
                            .grievanceMechanism(null)
                            .lspDisclosure(null)
                            .additionalTerms(Map.of(
                                    "documentKind", KfsService.DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS,
                                    "conditionsText", rec.getConditionsText() != null ? rec.getConditionsText() : "",
                                    "remarks", rec.getRemarks() != null ? rec.getRemarks() : ""))
                            .build();
                    return generateInvoiceDiscountingTermsPdf(probe, app);
                });
    }

    private boolean isInvoiceDiscountingTermsDocument(KfsDocument kfs) {
        if (kfs.getAdditionalTerms() == null) {
            return false;
        }
        Object kind = kfs.getAdditionalTerms().get("documentKind");
        return KfsService.DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS.equals(String.valueOf(kind));
    }

    private PdfPTable invoiceDiscountingFacilityTable(KfsDocument kfs, String partyName) throws DocumentException {
        PdfPTable table = createKeyValueTable();
        addRow(table, "Borrower / Purchaser", partyName);
        addRow(table, "Sanctioned facility limit", formatCurrency(kfs.getSanctionedAmount()));
        addRow(table, "Discount / interest rate (p.a.)", kfs.getInterestRate() != null
                ? kfs.getInterestRate().toPlainString() + "%" : "N/A");
        addRow(table, "Facility validity (months)", kfs.getTenureMonths() + " months (program reference)");
        addRow(table, "Repayment model", "Per invoice / program terms — not a term loan EMI");
        addRow(table, "Document type", "Invoice discounting sanction terms (no LMS loan account)");
        return table;
    }

    private PdfPTable invoiceDiscountingCostTable(KfsDocument kfs) throws DocumentException {
        PdfPTable table = createKeyValueTable();
        addRow(table, "Annual Percentage Rate (APR)", kfs.getApr() != null
                ? kfs.getApr().toPlainString() + "%" : "N/A");
        addRow(table, "Processing fee", formatCurrency(kfs.getProcessingFee()));
        addRow(table, "Total cost of credit (fees)", formatCurrency(kfs.getTotalCostOfCredit()));
        addRow(table, "Sanctioned limit available", formatCurrency(kfs.getSanctionedAmount()));
        return table;
    }

    private Paragraph invoiceDiscountingTermsParagraph(KfsDocument kfs) {
        Map<String, Object> terms = kfs.getAdditionalTerms();
        String conditions = terms != null && terms.get("conditionsText") != null
                ? String.valueOf(terms.get("conditionsText")).trim() : "";
        String remarks = terms != null && terms.get("remarks") != null
                ? String.valueOf(terms.get("remarks")).trim() : "";
        String approvedBy = terms != null && terms.get("approvedBy") != null
                ? String.valueOf(terms.get("approvedBy")).trim() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("1. This document records the sanction terms between the anchor program and the borrower ")
                .append("for invoice discounting. It is not a loan account statement.\n");
        sb.append("2. Drawdowns are against eligible invoices within the sanctioned limit and program rules.\n");
        sb.append("3. The borrower confirms having read and understood all terms disclosed herein.\n");
        if (!conditions.isBlank()) {
            sb.append("4. Special conditions: ").append(conditions).append('\n');
        } else {
            sb.append("4. Standard program terms and anchor-to-borrower conditions apply.\n");
        }
        if (!remarks.isBlank()) {
            sb.append("5. Remarks: ").append(remarks).append('\n');
        }
        if (!approvedBy.isBlank()) {
            sb.append("6. Sanctioned by: ").append(approvedBy).append('\n');
        }
        return new Paragraph(sb.toString(), VALUE_FONT);
    }

    private Paragraph invoiceDiscountingFacilityNote() {
        return new Paragraph(
                "NOTE: This facility is operated under invoice discounting program rules. No individual LMS loan "
                        + "is created at sanction; limits and terms govern subsequent invoice financing.",
                SMALL_FONT);
    }

    private Paragraph sectionHeader(String text) {
        Paragraph header = new Paragraph(text, HEADER_FONT);
        header.setSpacingBefore(8);
        header.setSpacingAfter(5);
        return header;
    }

    private PdfPTable loanDetailsTable(KfsDocument kfs) throws DocumentException {
        PdfPTable table = createKeyValueTable();

        addRow(table, "Sanctioned Amount", formatCurrency(kfs.getSanctionedAmount()));
        addRow(table, "Interest Rate (p.a.)", kfs.getInterestRate() != null
                ? kfs.getInterestRate().toPlainString() + "% (reducing balance)" : "N/A");
        addRow(table, "Tenure", kfs.getTenureMonths() + " months");
        addRow(table, "EMI Amount", formatCurrency(kfs.getEmiAmount()));
        addRow(table, "Repayment Frequency", "Monthly");
        addRow(table, "Interest Computation", "Daily reducing balance");

        return table;
    }

    private PdfPTable costDetailsTable(KfsDocument kfs) throws DocumentException {
        PdfPTable table = createKeyValueTable();

        addRow(table, "Annual Percentage Rate (APR)", kfs.getApr() != null
                ? kfs.getApr().toPlainString() + "%" : "N/A");
        addRow(table, "Total Interest Payable", formatCurrency(kfs.getTotalInterest()));
        addRow(table, "Total Repayment Amount", formatCurrency(kfs.getTotalRepayment()));
        addRow(table, "Total Cost of Credit", formatCurrency(kfs.getTotalCostOfCredit()));

        // Net disbursement = sanctioned - processing fee - stamp duty - insurance - other
        BigDecimal netDisbursement = kfs.getSanctionedAmount();
        if (kfs.getProcessingFee() != null) netDisbursement = netDisbursement.subtract(kfs.getProcessingFee());
        if (kfs.getStampDuty() != null) netDisbursement = netDisbursement.subtract(kfs.getStampDuty());
        if (kfs.getInsurancePremium() != null) netDisbursement = netDisbursement.subtract(kfs.getInsurancePremium());
        if (kfs.getOtherCharges() != null) netDisbursement = netDisbursement.subtract(kfs.getOtherCharges());
        addRow(table, "Net Disbursement to Borrower", formatCurrency(netDisbursement));

        return table;
    }

    private PdfPTable chargesTable(KfsDocument kfs) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{40, 30, 30});

        addHeaderCell(table, "Charge Type");
        addHeaderCell(table, "Amount / Rate");
        addHeaderCell(table, "When Applicable");

        addChargeRow(table, "Processing Fee", kfs.getProcessingFee(), "At disbursement");
        addChargeRow(table, "Stamp Duty", kfs.getStampDuty(), "At agreement execution");
        addChargeRow(table, "Insurance Premium", kfs.getInsurancePremium(), "At disbursement");
        addChargeRow(table, "Other Charges", kfs.getOtherCharges(), "As applicable");

        return table;
    }

    private Paragraph coolingOffParagraph(KfsDocument kfs) {
        int coolingOffHours = kfs.getCoolingOffHours() != null && kfs.getCoolingOffHours() > 0
                ? kfs.getCoolingOffHours() : 72;
        int coolingOffDays = coolingOffHours / 24;

        String text = String.format(
                "As per RBI guidelines, you have a cooling-off / look-up period of %d hours (%d days) " +
                "from the date of acknowledgement of this KFS. During this period, you may choose to " +
                "exit the loan without any penalty or additional charges. If you decide to exit within " +
                "the cooling-off period, the principal amount disbursed (if any) along with proportionate " +
                "interest for the period the loan was outstanding shall be recovered. No penalty or " +
                "other charges shall be levied for such exit.",
                coolingOffHours, coolingOffDays
        );

        return new Paragraph(text, VALUE_FONT);
    }

    private Paragraph grievanceParagraph(KfsDocument kfs) {
        String grievanceDetails = kfs.getGrievanceMechanism();
        if (grievanceDetails == null || grievanceDetails.isBlank()) {
            grievanceDetails = "For any grievances related to this loan, please contact:\n" +
                    "- Grievance Redressal Officer: [To be notified]\n" +
                    "- Email: grievance@lender.com\n" +
                    "- Toll-free number: 1800-XXX-XXXX\n" +
                    "- RBI Sachet Portal: https://sachet.rbi.org.in";
        }

        Paragraph p = new Paragraph();
        p.add(new Chunk(grievanceDetails, VALUE_FONT));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(
                "If your grievance is not resolved within 30 days, you may escalate to the " +
                "Reserve Bank – Integrated Ombudsman Scheme (RB-IOS) at https://cms.rbi.org.in.",
                SMALL_FONT));
        return p;
    }

    private Paragraph lspDisclosureParagraph(KfsDocument kfs) {
        String lspDetails = kfs.getLspDisclosure();
        if (lspDetails == null || lspDetails.isBlank()) {
            lspDetails = "This loan is facilitated through a Lending Service Provider (LSP) / " +
                    "Digital Lending App (DLA). The regulated entity (RE) responsible for this loan " +
                    "product is the lender named above. The LSP acts as an agent of the RE for " +
                    "loan origination purposes only.";
        }

        return new Paragraph(lspDetails, VALUE_FONT);
    }

    private Paragraph additionalTermsParagraph(KfsDocument kfs) {
        Map<String, Object> terms = kfs.getAdditionalTerms();
        String termsText;
        if (terms != null && !terms.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Map.Entry<String, Object> entry : terms.entrySet()) {
                sb.append(i++).append(". ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n");
            }
            termsText = sb.toString();
        } else {
            termsText = "1. The borrower confirms having read and understood all terms.\n" +
                    "2. Interest rate is subject to reset as per lender's policy for floating rate loans.\n" +
                    "3. Loan disbursement shall be directly to the borrower's bank account.\n" +
                    "4. Borrower's personal data will be processed as per the lender's privacy policy.\n" +
                    "5. This KFS is an integral part of the loan agreement.";
        }

        return new Paragraph(termsText, VALUE_FONT);
    }

    private Paragraph disclaimerParagraph() {
        return new Paragraph(
                "DISCLAIMER: This Key Fact Statement is provided in compliance with the Reserve Bank of India " +
                "Master Direction on Digital Lending dated September 2, 2022, as amended. The borrower is " +
                "advised to read all terms carefully before signing. By signing this KFS, the borrower " +
                "acknowledges receipt and understanding of all loan terms, costs, and charges disclosed herein.",
                DISCLAIMER_FONT);
    }

    private PdfPTable signatureBlock() throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(20);

        PdfPCell borrowerCell = new PdfPCell();
        borrowerCell.setBorder(Rectangle.NO_BORDER);
        borrowerCell.addElement(new Paragraph("_____________________________", VALUE_FONT));
        borrowerCell.addElement(new Paragraph("Borrower Signature / eSign", SMALL_FONT));
        borrowerCell.addElement(new Paragraph("Date: _______________", SMALL_FONT));

        PdfPCell lenderCell = new PdfPCell();
        lenderCell.setBorder(Rectangle.NO_BORDER);
        lenderCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lenderCell.addElement(new Paragraph("_____________________________", VALUE_FONT));
        lenderCell.addElement(new Paragraph("Authorized Signatory (Lender)", SMALL_FONT));
        lenderCell.addElement(new Paragraph("Date: _______________", SMALL_FONT));

        table.addCell(borrowerCell);
        table.addCell(lenderCell);

        return table;
    }

    // ---- Table helpers ----

    private PdfPTable createKeyValueTable() throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{40, 60});
        return table;
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorderColor(new Color(200, 200, 200));
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(new Color(245, 245, 250));

        PdfPCell valueCell = new PdfPCell(new Phrase(value, VALUE_FONT));
        valueCell.setBorderColor(new Color(200, 200, 200));
        valueCell.setPadding(6);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(new Color(0, 51, 102));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addChargeRow(PdfPTable table, String chargeType, BigDecimal amount, String when) {
        PdfPCell typeCell = new PdfPCell(new Phrase(chargeType, VALUE_FONT));
        typeCell.setBorderColor(new Color(200, 200, 200));
        typeCell.setPadding(5);

        PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), VALUE_FONT));
        amountCell.setBorderColor(new Color(200, 200, 200));
        amountCell.setPadding(5);

        PdfPCell whenCell = new PdfPCell(new Phrase(when, SMALL_FONT));
        whenCell.setBorderColor(new Color(200, 200, 200));
        whenCell.setPadding(5);

        table.addCell(typeCell);
        table.addCell(amountCell);
        table.addCell(whenCell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "N/A";
        return "\u20B9 " + String.format("%,.2f", amount);
    }

    /**
     * Page event helper for adding page numbers to each page.
     */
    private static class KfsPageEventHelper extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);
            Phrase footer = new Phrase(
                    "Page " + writer.getPageNumber() + " | Confidential | Generated by LOS Platform v2.0",
                    footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 20, 0);
        }
    }
}
