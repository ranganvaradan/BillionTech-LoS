package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdiKfsDocxPdfService {

    private final EdiKfsProperties ediKfsProperties;
    private final EdiKfsLoanDetector ediKfsLoanDetector;
    private final EdiKfsTemplateContextBuilder contextBuilder;
    private final EdiSanctionLetterDocxMerger docxMerger;
    private final DocxToPdfConverterFactory converterFactory;
    private final ResourceLoader resourceLoader;

    public boolean shouldUseEdiTemplate(LoanApplication app) {
        return ediKfsLoanDetector.isEdiLoan(app);
    }

    public byte[] generatePdf(KfsDocument kfs, LoanApplication app) throws IOException {
        EdiKfsTemplateContext ctx = contextBuilder.build(app, kfs);
        log.info("EDI KFS building PDF for application {} — {} Encore schedule rows",
                kfs.getApplicationId(), ctx.scheduleRows() != null ? ctx.scheduleRows().size() : 0);
        byte[] templateBytes = loadTemplateBytes();
        byte[] docxBytes = docxMerger.merge(templateBytes, ctx);

        Path tempDir = Files.createTempDirectory("edi-kfs-");
        Path docxPath = tempDir.resolve("kfs.docx");
        try {
            Files.write(docxPath, docxBytes);
            DocxToPdfConverter converter = converterFactory.resolve();
            byte[] pdf = converter.convert(docxPath);
            log.info("EDI KFS PDF generated for application {} — {}KB", kfs.getApplicationId(), pdf.length / 1024);
            return pdf;
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private byte[] loadTemplateBytes() throws IOException {
        String location = ediKfsProperties.getTemplate();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("EDI KFS template not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static void deleteQuietly(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best effort temp cleanup
                        }
                    });
                }
            }
        } catch (IOException ignored) {
            // best effort
        }
    }
}
