package com.los.core.service.esign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores provider-downloaded signed PDFs beside general uploads ({@code ./uploads/signed/{applicationId}.pdf}).
 */
@Slf4j
@Service
public class EsignSignedDocumentStorageService {

    private final Path root;

    public EsignSignedDocumentStorageService(
            @Value("${file.upload-dir:${los.file-storage.root:./uploads}}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /**
     * Persists signed bytes and returns an absolute filesystem path recorded in {@code esign_requests.signed_document_url}.
     */
    public String saveSignedPdf(UUID applicationId, byte[] pdfBytes) throws IOException {
        if (applicationId == null) {
            throw new IllegalArgumentException("applicationId is required");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Signed PDF bytes are required");
        }
        Path signedDir = root.resolve("signed");
        Files.createDirectories(signedDir);
        Path dest = signedDir.resolve(applicationId + ".pdf");
        Files.write(dest, pdfBytes);
        Path abs = dest.toAbsolutePath().normalize();
        log.info("[eSign storage] Saved signed PDF for application {} at {}", applicationId, abs);
        return abs.toString();
    }
}
