package com.los.core.controller;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.BorrowerApplicationDetailResponse;
import com.los.core.model.dto.response.BorrowerApplicationStatusResponse;
import com.los.core.model.dto.response.BorrowerApplicationSummaryResponse;
import com.los.core.model.dto.response.BorrowerDocumentItemResponse;
import com.los.core.model.dto.response.BorrowerKfsSummaryResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.borrower.BorrowerApplicationStatusService;
import com.los.core.service.borrower.BorrowerPortalService;
import com.los.core.service.document.IDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Borrower self-service: list/detail require {@code X-User-Id} + {@code BORROWER} role. Legacy
 * {@code GET /{id}/status} stays available for public tracking links.
 */
@RestController
@RequestMapping("/api/v1/borrower/applications")
@RequiredArgsConstructor
@Tag(name = "Borrower", description = "Borrower-safe application APIs (no credit internals)")
public class BorrowerApplicationController {

    private final LoanApplicationRepository applicationRepository;
    private final IDocumentService documentService;
    private final BorrowerApplicationStatusService borrowerApplicationStatusService;
    private final BorrowerPortalService borrowerPortalService;

    @GetMapping
    @Operation(summary = "List my applications (borrower only)")
    public ResponseEntity<Page<BorrowerApplicationSummaryResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.listApplications(uid, pageable));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Application detail with lifecycle timeline (borrower only)")
    public ResponseEntity<BorrowerApplicationDetailResponse> detail(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.applicationDetail(uid, applicationId, true));
    }

    @GetMapping("/{applicationId}/status")
    @Operation(summary = "Borrower-safe status (legacy public tracking — no PII lock)")
    public ResponseEntity<BorrowerApplicationStatusResponse> status(@PathVariable UUID applicationId) {
        LoanApplication app = applicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        int docCount = documentService.getDocuments(applicationId).size();
        boolean checklist = documentService.isDocumentChecklistComplete(applicationId);
        return ResponseEntity.ok(borrowerApplicationStatusService.build(app, checklist, docCount));
    }

    @GetMapping("/{applicationId}/documents")
    @Operation(summary = "List KYC and signed documents (borrower-safe, view-only)")
    public ResponseEntity<List<BorrowerDocumentItemResponse>> documents(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.listDocumentsSafe(uid, applicationId));
    }

    @GetMapping("/{applicationId}/documents/{documentId}/content")
    @Operation(summary = "Preview an uploaded document (inline, borrower only)")
    public ResponseEntity<byte[]> documentContent(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        BorrowerPortalService.BorrowerDocumentContent content =
                borrowerPortalService.previewUploadDocument(uid, applicationId, documentId);
        return inlineDocumentResponse(content);
    }

    @GetMapping("/{applicationId}/documents/esign/{esignRequestId}/content")
    @Operation(summary = "Preview a signed eSign PDF (inline, borrower only)")
    public ResponseEntity<byte[]> esignDocumentContent(
            @PathVariable UUID applicationId,
            @PathVariable UUID esignRequestId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        BorrowerPortalService.BorrowerDocumentContent content =
                borrowerPortalService.previewEsignDocument(uid, applicationId, esignRequestId);
        return inlineDocumentResponse(content);
    }

    private static ResponseEntity<byte[]> inlineDocumentResponse(BorrowerPortalService.BorrowerDocumentContent content) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(content.bytes());
    }

    @GetMapping("/{applicationId}/kfs")
    @Operation(summary = "KFS summary for borrower (after sanction)")
    public ResponseEntity<BorrowerKfsSummaryResponse> kfs(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        return ResponseEntity.ok(borrowerPortalService.kfsForBorrower(uid, applicationId));
    }

    @GetMapping("/{applicationId}/terms/pdf")
    @Operation(summary = "Download invoice discounting sanction terms PDF (borrower onboarding)")
    public ResponseEntity<byte[]> termsPdf(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        byte[] pdf = borrowerPortalService.invoiceDiscountingTermsPdf(uid, applicationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-discounting-terms-" + applicationId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @DeleteMapping("/{applicationId}/draft")
    @Operation(
            summary = "Delete a draft or consent-pending application",
            description = "Only the owning borrower may delete, and only while status is DRAFT or CONSENT_PENDING (not yet in KYC).")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerPortalService.requireBorrower(role);
        borrowerPortalService.deleteUnsubmittedApplication(uid, applicationId);
        return ResponseEntity.noContent().build();
    }
}
