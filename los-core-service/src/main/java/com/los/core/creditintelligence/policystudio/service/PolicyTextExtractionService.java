package com.los.core.creditintelligence.policystudio.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal text extraction for Policy Studio uploads — feeds existing processUpload(sourceText).
 */
@Slf4j
@Service
public class PolicyTextExtractionService {

    public static final long MAX_BYTES = 12L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "docx", "txt");

    public record ExtractedText(String text, String documentType, String originalFileName, String contentType) {}

    public ExtractedText extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw business(HttpStatus.BAD_REQUEST,
                    "The uploaded file is empty. Choose a PDF, Word, or text policy document.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw business(HttpStatus.PAYLOAD_TOO_LARGE,
                    "This file is too large (max 12 MB). Upload a smaller policy document or split the policy.");
        }
        String original = file.getOriginalFilename() == null ? "policy.txt" : file.getOriginalFilename().trim();
        String ext = extension(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw business(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Please upload a PDF, Word (.docx), or text (.txt) policy document.");
        }
        try {
            byte[] bytes = file.getBytes();
            String text = switch (ext) {
                case "txt" -> new String(bytes, StandardCharsets.UTF_8);
                case "docx" -> extractDocx(bytes);
                case "pdf" -> extractPdf(bytes);
                default -> throw business(HttpStatus.BAD_REQUEST, "Unsupported file type.");
            };
            text = text == null ? "" : text.strip();
            if (text.isBlank()) {
                throw business(HttpStatus.BAD_REQUEST,
                        "No readable text was found in this document. Try a text-based PDF or Word file (not a scanned image).");
            }
            String type = ext.equals("txt") ? "TXT" : ext.toUpperCase(Locale.ROOT);
            log.info("policy-studio text extracted fileName={} type={} chars={}",
                    sanitizeName(original), type, text.length());
            return new ExtractedText(text, type, original, file.getContentType());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("policy-studio extract failed fileName={} reason={}",
                    sanitizeName(original), e.getClass().getSimpleName());
            throw business(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not read this document. Please try another PDF, Word, or text file.");
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeName(String name) {
        return name == null ? "" : name.replaceAll("[\\r\\n\\t]", "_");
    }

    private static ResponseStatusException business(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
