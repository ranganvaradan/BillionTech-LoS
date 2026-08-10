package com.los.core.service.kfs.edi;

import java.io.IOException;
import java.nio.file.Path;

/** Converts a filled DOCX file to PDF bytes. */
public interface DocxToPdfConverter {

    byte[] convert(Path docxFile) throws IOException;

    /** Whether this converter is configured and available on the host. */
    boolean isAvailable();
}
