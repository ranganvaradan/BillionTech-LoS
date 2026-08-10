package com.los.core.controller;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.SanctionResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.sanction.SanctionLetterPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/sanction")
@RequiredArgsConstructor
@Tag(name = "Sanction (read)", description = "Latest sanction record and sanction letter PDF")
public class SanctionDocumentController {

    private final SanctionRecordRepository sanctionRecordRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SanctionLetterPdfService sanctionLetterPdfService;

    @GetMapping
    @Operation(summary = "Get latest sanction record for the application")
    public ResponseEntity<SanctionResponse> getLatest(@PathVariable UUID applicationId) {
        SanctionRecord r = sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("No sanction found for this application"));
        return ResponseEntity.ok(toResponse(r));
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download sanction letter PDF (on demand)")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        SanctionRecord r = sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("No sanction found for this application"));
        byte[] pdf = sanctionLetterPdfService.render(app, r);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Sanction-" + applicationId + ".pdf\"")
                .body(pdf);
    }

    private static SanctionResponse toResponse(SanctionRecord r) {
        return SanctionResponse.builder()
                .id(r.getId())
                .applicationId(r.getApplicationId())
                .approvedAmount(r.getApprovedAmount())
                .approvedTenure(r.getApprovedTenure())
                .interestRate(r.getInterestRate())
                .processingFee(r.getProcessingFee())
                .conditionsText(r.getConditionsText())
                .remarks(r.getRemarks())
                .approvedBy(r.getApprovedBy())
                .sanctionPdfPath(r.getSanctionPdfPath())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
