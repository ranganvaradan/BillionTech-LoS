package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocxToPdfConverterFactory {

    private final EdiKfsProperties ediKfsProperties;
    private final EmbeddedDocxToPdfConverter embeddedDocxToPdfConverter;
    private final LibreOfficeDocxToPdfConverter libreOfficeDocxToPdfConverter;
    private final AsposeDocxToPdfConverter asposeDocxToPdfConverter;

    public DocxToPdfConverter resolve() throws IOException {
        String mode = ediKfsProperties.getPdfConverter() != null
                ? ediKfsProperties.getPdfConverter().trim().toLowerCase()
                : "embedded";

        if ("aspose".equals(mode)) {
            if (asposeDocxToPdfConverter.isAvailable()) {
                return asposeDocxToPdfConverter;
            }
            throw new IOException("EDI KFS pdf-converter=aspose but Aspose.Words is not available");
        }

        if ("libreoffice".equals(mode)) {
            if (libreOfficeDocxToPdfConverter.isAvailable()) {
                return libreOfficeDocxToPdfConverter;
            }
            log.warn(
                    "EDI KFS pdf-converter=libreoffice but LibreOffice is not installed — using embedded docx4j converter");
            return embeddedDocxToPdfConverter;
        }

        if ("auto".equals(mode)) {
            if (libreOfficeDocxToPdfConverter.isAvailable()) {
                return libreOfficeDocxToPdfConverter;
            }
            return embeddedDocxToPdfConverter;
        }

        // embedded (default) — pure Java, always works
        return embeddedDocxToPdfConverter;
    }
}
