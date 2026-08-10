package com.los.core.service.kfs.edi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Optional Aspose.Words converter — active only when {@code com.aspose.words.Document} is on the classpath
 * and {@code los.kfs.edi.pdf-converter=aspose}.
 */
@Slf4j
@Component
public class AsposeDocxToPdfConverter implements DocxToPdfConverter {

    @Override
    public byte[] convert(Path docxFile) throws IOException {
        if (!isAvailable()) {
            throw new IOException("Aspose.Words is not on the classpath — add aspose-words dependency or use libreoffice");
        }
        try {
            Class<?> documentClass = Class.forName("com.aspose.words.Document");
            Object doc = documentClass.getConstructor(String.class).newInstance(docxFile.toAbsolutePath().toString());
            Class<?> saveFormat = Class.forName("com.aspose.words.SaveFormat");
            int pdfFormat = saveFormat.getField("PDF").getInt(null);
            Path pdfPath = docxFile.resolveSibling(stripExtension(docxFile.getFileName().toString()) + ".pdf");
            documentClass.getMethod("save", String.class, int.class).invoke(doc, pdfPath.toString(), pdfFormat);
            return java.nio.file.Files.readAllBytes(pdfPath);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Aspose conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.aspose.words.Document");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
