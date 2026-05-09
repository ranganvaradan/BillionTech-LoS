package com.los.core.service.document;

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

    @Override
    public DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file, KycStepType kycStepType) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "document";
        }
        String storageKey = String.format("%s/%s/%s_%s",
                applicationId, documentType, UUID.randomUUID(), originalFileName);

        try {
            byte[] fileBytes = file.getBytes();
            String checksum = computeSha256(fileBytes);
            String contentType = normalizeContentType(file.getContentType(), originalFileName);
            documentBlobStore.putObject(storageKey, fileBytes, fileBytes.length, contentType);

            Document document = Document.builder()
                    .applicationId(applicationId)
                    .documentType(documentType)
                    .kycStepType(kycStepType)
                    .fileName(originalFileName)
                    .storageKey(storageKey)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .build();

            document = documentRepository.save(document);
            log.info("Document uploaded: {} for application {}", documentType, applicationId);

            auditService.logEvent(applicationId, "DOCUMENT", "UPLOADED",
                    null, null,
                    Map.of("documentType", documentType, "fileName", originalFileName),
                    "Document uploaded: " + documentType);

            return toResponse(document);
        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage());
            throw new RuntimeException("Document upload failed: " + e.getMessage(), e);
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
