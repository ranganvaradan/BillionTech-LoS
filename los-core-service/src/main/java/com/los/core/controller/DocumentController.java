package com.los.core.controller;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.DocumentRepository;
import com.los.core.security.StaffAccessGuard;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.document.storage.DocumentBlobStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload, download, and management")
public class DocumentController {

    private final IDocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentBlobStore documentBlobStore;
    private final StaffAccessGuard staffAccessGuard;

    @PostMapping(value = "/{applicationId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for an application")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID applicationId,
            @RequestParam String documentType,
            @RequestParam(required = false) KycStepType kycStepType,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.assertCanAccessApplication(userId, userRole, applicationId);
        return ResponseEntity.ok(documentService.uploadDocument(applicationId, documentType, file, kycStepType));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "List all documents for an application")
    public ResponseEntity<List<DocumentResponse>> list(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.assertCanAccessApplication(userId, userRole, applicationId);
        return ResponseEntity.ok(documentService.getDocuments(applicationId));
    }

    @GetMapping("/download/{documentId}")
    @Operation(summary = "Download a document by ID")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        Document doc = loadAuthorized(documentId, userId, userRole);
        byte[] data = documentService.downloadDocument(documentId);
        MediaType mediaType = resolveMediaType(doc);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeFileName(doc), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(data);
    }

    @GetMapping("/content/{documentId}")
    @Operation(summary = "Preview document content by ID (inline)")
    public ResponseEntity<byte[]> content(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        Document doc = loadAuthorized(documentId, userId, userRole);
        byte[] data = documentService.downloadDocument(documentId);
        MediaType mediaType = resolveMediaType(doc);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(safeFileName(doc), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(data);
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<Void> delete(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        loadAuthorized(documentId, userId, userRole);
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{applicationId}/checklist")
    @Operation(summary = "Check if document checklist is complete")
    public ResponseEntity<Map<String, Object>> checklistStatus(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.assertCanAccessApplication(userId, userRole, applicationId);
        boolean complete = documentService.isDocumentChecklistComplete(applicationId);
        return ResponseEntity.ok(Map.of("applicationId", applicationId, "checklistComplete", complete));
    }

    private Document loadAuthorized(UUID documentId, String userId, String userRole) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        staffAccessGuard.assertCanAccessDocument(userId, userRole, doc);
        return doc;
    }

    private static String safeFileName(Document doc) {
        String name = doc.getFileName();
        return (name == null || name.isBlank()) ? "document" : name;
    }

    private MediaType resolveMediaType(Document doc) {
        Optional<String> probed = documentBlobStore.probeStoredContentType(doc.getStorageKey());
        if (probed.isPresent()) {
            try {
                return MediaType.parseMediaType(probed.get());
            } catch (Exception ignored) {
                // fall through
            }
        }
        String stored = doc.getContentType();
        if (stored != null && !stored.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(stored.trim())) {
            try {
                return MediaType.parseMediaType(stored);
            } catch (Exception ignored) {
                // fall through
            }
        }
        String fromName = URLConnection.guessContentTypeFromName(doc.getFileName());
        if (fromName != null && !fromName.isBlank()) {
            try {
                return MediaType.parseMediaType(fromName);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
