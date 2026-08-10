package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a LibreOffice/soffice executable for headless DOCX→PDF conversion.
 */
@Slf4j
@Component
public class LibreOfficeExecutableResolver {

    private static final List<String> COMMON_CANDIDATES = List.of(
            "libreoffice",
            "soffice",
            "/usr/bin/libreoffice",
            "/usr/bin/soffice",
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe");

    private final EdiKfsProperties ediKfsProperties;

    public LibreOfficeExecutableResolver(EdiKfsProperties ediKfsProperties) {
        this.ediKfsProperties = ediKfsProperties;
    }

    /** First executable that responds to {@code --version}, or null. */
    public String resolveAvailable() {
        for (String candidate : candidates()) {
            if (respondsToVersion(candidate)) {
                log.info("EDI KFS using LibreOffice executable: {}", candidate);
                return candidate;
            }
        }
        return null;
    }

    public List<String> candidates() {
        Set<String> ordered = new LinkedHashSet<>();
        String configured = ediKfsProperties.getLibreOfficePath();
        if (configured != null && !configured.isBlank()) {
            ordered.add(configured.trim());
        }
        ordered.addAll(COMMON_CANDIDATES);
        return new ArrayList<>(ordered);
    }

    private static boolean respondsToVersion(String executable) {
        if (executable == null || executable.isBlank()) {
            return false;
        }
        if (executable.contains("/") || executable.contains("\\")) {
            if (!Files.isRegularFile(Path.of(executable))) {
                return false;
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            log.debug("LibreOffice candidate not usable: {} — {}", executable, e.getMessage());
            return false;
        }
    }
}
