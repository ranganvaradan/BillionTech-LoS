package com.los.core.service.kfs.edi;

import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Pure-Java DOCX→PDF via docx4j. Always available — no LibreOffice or Aspose on the host.
 */
@Slf4j
@Component
public class EmbeddedDocxToPdfConverter implements DocxToPdfConverter {

    @Override
    public byte[] convert(Path docxFile) throws IOException {
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docxFile.toFile());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Docx4J.toPDF(pkg, out);
            byte[] pdf = out.toByteArray();
            if (pdf.length == 0) {
                throw new IOException("Embedded DOCX to PDF conversion produced empty output");
            }
            return pdf;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Embedded DOCX to PDF conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
