package com.los.core.controller;

import com.los.core.model.dto.request.CamUpdateRequest;
import com.los.core.model.dto.request.ReviewNotesRequest;
import com.los.core.model.dto.response.CamResponse;
import com.los.core.service.cam.CreditAppraisalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/cam")
@RequiredArgsConstructor
@Tag(name = "Credit Appraisal Memo (CAM)", description = "CAM retrieval, edits, and PDF")
public class CreditAppraisalController {

    private final CreditAppraisalService creditAppraisalService;

    @GetMapping
    @Operation(summary = "Get structured CAM (no raw server JSON; sections are object maps)")
    public ResponseEntity<CamResponse> get(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(creditAppraisalService.getCam(applicationId));
    }

    @PutMapping
    @Operation(summary = "Update CAM remarks and recommendation")
    public ResponseEntity<CamResponse> update(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CamUpdateRequest request) {
        return ResponseEntity.ok(creditAppraisalService.updateCam(applicationId, request, parseUserId(userId)));
    }

    @PostMapping("/submit")
    @Operation(summary = "Officer submits CAM to credit manager (DRAFT/SENT_BACK/REJECTED → SUBMITTED)")
    public ResponseEntity<CamResponse> submit(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(creditAppraisalService.submitCam(applicationId, parseUserId(userId)));
    }

    @PostMapping("/send-back")
    @Operation(summary = "Manager returns CAM to officer (SUBMITTED or APPROVED → SENT_BACK for revision)")
    public ResponseEntity<CamResponse> sendBack(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) ReviewNotesRequest body) {
        String remarks = null;
        if (body != null) {
            remarks = body.getNotes() != null ? body.getNotes() : body.getRemarks();
        }
        return ResponseEntity.ok(creditAppraisalService.sendBackCam(applicationId, parseUserId(userId), remarks));
    }

    @PostMapping("/reject")
    @Operation(summary = "Manager rejects the memorandum at review (SUBMITTED → REJECTED; application stays CAM_READY)")
    public ResponseEntity<CamResponse> rejectMemorandum(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(creditAppraisalService.rejectMemorandum(applicationId, parseUserId(userId)));
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download CAM as PDF (generated on demand from current data)")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID applicationId) {
        byte[] pdf = creditAppraisalService.renderCamPdf(applicationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"CAM-" + applicationId + ".pdf\"")
                .body(pdf);
    }

    private static UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return UUID.fromString(userId.trim());
    }
}
