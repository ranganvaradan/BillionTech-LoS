package com.los.core.controller;

import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.service.document.IDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload, download, and management")
public class DocumentController {

    private final IDocumentService documentService;

    @PostMapping(value = "/{applicationId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for an application")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID applicationId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentService.uploadDocument(applicationId, documentType, file));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "List all documents for an application")
    public ResponseEntity<List<DocumentResponse>> list(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(documentService.getDocuments(applicationId));
    }

    @GetMapping("/download/{documentId}")
    @Operation(summary = "Download a document by ID")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        byte[] data = documentService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{applicationId}/checklist")
    @Operation(summary = "Check if document checklist is complete")
    public ResponseEntity<Map<String, Object>> checklistStatus(@PathVariable UUID applicationId) {
        boolean complete = documentService.isDocumentChecklistComplete(applicationId);
        return ResponseEntity.ok(Map.of("applicationId", applicationId, "checklistComplete", complete));
    }
}
