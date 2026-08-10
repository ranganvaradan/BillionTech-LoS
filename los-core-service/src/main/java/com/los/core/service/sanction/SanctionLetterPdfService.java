package com.los.core.service.sanction;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * In-memory PDF for sanction in principle / sanction letter.
 */
@Service
@RequiredArgsConstructor
public class SanctionLetterPdfService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final KfsPdfGenerationService kfsPdfGenerationService;

    public byte[] render(LoanApplication app, SanctionRecord r) {
        if (InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
            return kfsPdfGenerationService.generateInvoiceDiscountingTermsPdfForApplication(app.getId());
        }
        return renderStandardSanction(app, r);
    }

    /** @deprecated use {@link KfsPdfGenerationService#generateInvoiceDiscountingTermsPdfForApplication} */
    @Deprecated
    public byte[] renderInvoiceDiscountingBorrowerTerms(LoanApplication app, SanctionRecord r) {
        return render(app, r);
    }

    private byte[] renderStandardSanction(LoanApplication app, SanctionRecord r) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();
            Font title = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0, 51, 102));
            Font h = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font body = new Font(Font.HELVETICA, 9, Font.NORMAL);

            document.add(new Paragraph("Sanction / approval in principle", title));
            document.add(Chunk.NEWLINE);

            String instant = (r.getCreatedAt() != null
                    ? r.getCreatedAt().atZone(ZoneId.systemDefault())
                    : Instant.now().atZone(ZoneId.systemDefault()))
                    .format(FMT);
            document.add(new Paragraph("Application: " + app.getApplicationNumber() + " · " + instant, body));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Terms (sanction)", h));
            document.add(bullet(body, "Approved amount: " + money(r.getApprovedAmount())));
            document.add(bullet(body, "Tenure: " + r.getApprovedTenure() + " months"));
            document.add(bullet(body, "Interest rate: " + pct(r.getInterestRate()) + " p.a."));
            if (r.getProcessingFee() != null) {
                document.add(bullet(body, "Processing fee: " + money(r.getProcessingFee())));
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Special conditions", h));
            String cond = r.getConditionsText() != null && !r.getConditionsText().isBlank()
                    ? r.getConditionsText() : "—";
            document.add(new Paragraph(cond, body));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Remarks", h));
            String rem = r.getRemarks() != null && !r.getRemarks().isBlank() ? r.getRemarks() : "—";
            document.add(new Paragraph(rem, body));
            document.add(Chunk.NEWLINE);

            String by = r.getApprovedBy() != null && !r.getApprovedBy().isBlank()
                    ? r.getApprovedBy() : "—";
            document.add(new Paragraph("Approved by: " + by, h));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "Sanction letter PDF failed: " + e.getMessage(), "SANCTION_PDF_FAILED", "RETRY", null);
        }
    }

    private static Paragraph bullet(Font f, String text) throws DocumentException {
        return new Paragraph("• " + text, f);
    }

    private static String money(BigDecimal a) {
        if (a == null) {
            return "—";
        }
        return a.toPlainString();
    }

    private static String pct(BigDecimal p) {
        if (p == null) {
            return "—";
        }
        return p.toPlainString() + "%";
    }
}
