package com.los.core.service.document;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.DocumentRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.document.storage.DocumentBlobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements IDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentBlobStore documentBlobStore;
    private final AuditService auditService;

    private static final Set<String> REQUIRED_DOC_TYPES = Set.of(
            "PAN_CARD", "AADHAAR", "BANK_STATEMENT", "PHOTOGRAPH"
    );

    /** Common identity / KYC uploads (PAN, photo ID). */
    private static final Set<String> ALLOWED_IMAGE_OR_PDF_EXT = Set.of("pdf", "jpg", "jpeg", "png");

    @Override
    public DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file, KycStepType kycStepType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(
                    "Upload could not be completed — no file was received.",
                    "DOCUMENT_EMPTY",
                    "UPLOAD_DOCUMENT",
                    Map.of("documentType", documentType == null ? "" : documentType));
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "document";
        }
        String type = documentType == null ? "" : documentType.trim();
        validateUploadFile(type, originalFileName, file.getContentType());

        String storageKey = String.format("%s/%s/%s_%s",
                applicationId, type, UUID.randomUUID(), sanitizeFileName(originalFileName));

        try {
            byte[] fileBytes = file.getBytes();
            String checksum = computeSha256(fileBytes);
            String contentType = normalizeContentType(file.getContentType(), originalFileName);
            documentBlobStore.putObject(storageKey, fileBytes, fileBytes.length, contentType);

            Document document = Document.builder()
                    .applicationId(applicationId)
                    .documentType(type)
                    .kycStepType(kycStepType)
                    .fileName(originalFileName)
                    .storageKey(storageKey)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .build();

            document = documentRepository.save(document);
            log.info("Document uploaded: {} for application {}", type, applicationId);

            auditService.logEvent(applicationId, "DOCUMENT", "UPLOADED",
                    null, null,
                    Map.of("documentType", type, "fileName", originalFileName),
                    "Document uploaded: " + type);

            return toResponse(document);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (AccessDeniedException e) {
            log.error("Document storage access denied for application {}: {}", applicationId, e.getMessage());
            throw new BusinessRuleException(
                    "Document storage unavailable. Please try again or contact support.",
                    "DOCUMENT_STORAGE_UNAVAILABLE",
                    "UPLOAD_DOCUMENT",
                    Map.of("documentType", type));
        } catch (Exception e) {
            if (isAccessDenied(e)) {
                log.error("Document storage access denied for application {}: {}", applicationId, e.getMessage());
                throw new BusinessRuleException(
                        "Document storage unavailable. Please try again or contact support.",
                        "DOCUMENT_STORAGE_UNAVAILABLE",
                        "UPLOAD_DOCUMENT",
                        Map.of("documentType", type));
            }
            log.error("Failed to upload document for application {}: {}", applicationId, e.getMessage());
            throw new BusinessRuleException(
                    "Upload could not be completed. Please try again.",
                    "DOCUMENT_UPLOAD_FAILED",
                    "UPLOAD_DOCUMENT",
                    Map.of("documentType", type));
        }
    }

    private static boolean isAccessDenied(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof AccessDeniedException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("accessdenied")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static void validateUploadFile(String documentType, String fileName, String contentType) {
        String ext = extensionOf(fileName);
        String ct = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        boolean identityDoc = "PAN_CARD".equalsIgnoreCase(documentType)
                || "AADHAAR".equalsIgnoreCase(documentType)
                || "PHOTOGRAPH".equalsIgnoreCase(documentType);
        if (identityDoc) {
            boolean extOk = ALLOWED_IMAGE_OR_PDF_EXT.contains(ext);
            boolean mimeOk = ct.isBlank()
                    || ct.equals("application/octet-stream")
                    || ct.equals("application/pdf")
                    || ct.startsWith("image/");
            if (!extOk || !mimeOk) {
                throw new BusinessRuleException(
                        "Unsupported file type. Upload a PDF, JPG, or PNG.",
                        "DOCUMENT_UNSUPPORTED_TYPE",
                        "UPLOAD_DOCUMENT",
                        Map.of("documentType", documentType, "extension", ext));
            }
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/]+", "_").trim();
        return cleaned.isBlank() ? "document" : cleaned;
    }

    @Override
    public DocumentResponse storeDocumentBytes(
            UUID applicationId,
            String documentType,
            String fileName,
            String contentType,
            byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Document bytes are required");
        }
        String originalFileName = (fileName == null || fileName.isBlank()) ? "document" : fileName.trim();
        String storageKey = String.format("%s/%s/%s_%s",
                applicationId, documentType, UUID.randomUUID(), originalFileName);
        try {
            String checksum = computeSha256(bytes);
            String ct = normalizeContentType(contentType, originalFileName);
            documentBlobStore.putObject(storageKey, bytes, bytes.length, ct);

            Document document = Document.builder()
                    .applicationId(applicationId)
                    .documentType(documentType)
                    .fileName(originalFileName)
                    .storageKey(storageKey)
                    .contentType(ct)
                    .fileSize((long) bytes.length)
                    .checksum(checksum)
                    .build();

            document = documentRepository.save(document);
            log.info("Document stored: {} for application {}", documentType, applicationId);

            auditService.logEvent(applicationId, "DOCUMENT", "UPLOADED",
                    null, null,
                    Map.of("documentType", documentType, "fileName", originalFileName, "source", "SERVICE"),
                    "Document stored: " + documentType);

            return toResponse(document);
        } catch (Exception e) {
            log.error("Failed to store document bytes: {}", e.getMessage());
            throw new RuntimeException("Document store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentResponse> getDocuments(UUID applicationId) {
        return documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public byte[] downloadDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        try (InputStream stream = documentBlobStore.getObject(document.getStorageKey())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download document: {}", e.getMessage());
            throw new RuntimeException("Document download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        try {
            documentBlobStore.removeObject(document.getStorageKey());
        } catch (Exception e) {
            log.warn("Failed to delete from object storage: {}", e.getMessage());
        }

        documentRepository.delete(document);
        log.info("Document deleted: {} from application {}", document.getDocumentType(), document.getApplicationId());
    }

    @Override
    public boolean isDocumentChecklistComplete(UUID applicationId) {
        List<Document> docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        Set<String> uploadedTypes = docs.stream().map(Document::getDocumentType).collect(Collectors.toSet());
        return uploadedTypes.containsAll(REQUIRED_DOC_TYPES);
    }

    private static String normalizeContentType(String uploaded, String originalFileName) {
        String ct = uploaded;
        if (ct == null || ct.isBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(ct.trim())) {
            String guessed = URLConnection.guessContentTypeFromName(originalFileName);
            if (guessed != null && !guessed.isBlank()) {
                ct = guessed;
            }
        }
        if (ct == null || ct.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ct;
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .applicationId(doc.getApplicationId())
                .documentType(doc.getDocumentType())
                .kycStepType(doc.getKycStepType())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .checksum(doc.getChecksum())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
