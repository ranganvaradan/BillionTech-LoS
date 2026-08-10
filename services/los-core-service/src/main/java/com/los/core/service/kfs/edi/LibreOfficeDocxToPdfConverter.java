package com.los.core.service.kfs.edi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LibreOfficeDocxToPdfConverter implements DocxToPdfConverter {

    private static final long CONVERT_TIMEOUT_SECONDS = 120;

    private final LibreOfficeExecutableResolver executableResolver;

    @Override
    public byte[] convert(Path docxFile) throws IOException {
        String executable = resolveExecutable();
        if (executable == null) {
            throw new IOException(
                    "LibreOffice not found — install LibreOffice or set los.kfs.edi.libre-office-path");
        }
        Path outDir = docxFile.getParent();
        if (outDir == null) {
            throw new IOException("DOCX path has no parent directory");
        }
        ProcessBuilder pb = new ProcessBuilder(
                executable,
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                outDir.toAbsolutePath().toString(),
                docxFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(CONVERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice conversion timed out after " + CONVERT_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new IOException("LibreOffice conversion failed (exit " + process.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", e);
        }

        String baseName = stripExtension(docxFile.getFileName().toString());
        Path pdfPath = outDir.resolve(baseName + ".pdf");
        if (!Files.exists(pdfPath)) {
            throw new IOException("LibreOffice did not produce expected PDF: " + pdfPath);
        }
        return Files.readAllBytes(pdfPath);
    }

    @Override
    public boolean isAvailable() {
        return resolveExecutable() != null;
    }

    private String resolveExecutable() {
        return executableResolver.resolveAvailable();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
