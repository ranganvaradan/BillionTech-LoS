package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.kfs.edi")
public class EdiKfsProperties {

    /** When false, all KFS PDFs use the standard OpenPDF path. */
    private boolean enabled = true;

    /** Classpath or file path to the BL sanction / KFS Word template. */
    private String template = "classpath:kfs/templates/BL_SanctionLetter_Template_V3.docx";

    /** {@code embedded}, {@code auto}, {@code libreoffice}, or {@code aspose}. */
    private String pdfConverter = "embedded";

    /** Executable for LibreOffice headless conversion ({@code soffice} on PATH or full path). */
    private String libreOfficePath = "libreoffice";

    /** When EDI DOCX/PDF generation fails, use standard OpenPDF instead of failing the request. */
    private boolean fallbackToOpenPdfOnFailure = true;

    /** Optional ISO dates (yyyy-MM-dd) treated as holidays for KFS validity (+3 working days). */
    private List<String> holidays = new ArrayList<>();
}
