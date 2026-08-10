package com.los.plp.controller;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.dto.PlpSyncResultResponse;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "PLP Sync Retry", description = "Manual retry of failed PLP synchronizations")
public class PlpSyncRetryController {

    private final PlpAnchorSyncService plpAnchorSyncService;
    private final PlpProgramSyncService plpProgramSyncService;
    private final PlpSubProgramSyncService plpSubProgramSyncService;
    private final PlpBorrowerSyncService plpBorrowerSyncService;
    private final PlpSanctionSyncOrchestrator plpSanctionSyncOrchestrator;
    private final LoanApplicationRepository loanApplicationRepository;

    @PostMapping("/api/v1/anchors/{anchorId}/plp-sync/retry")
    @Operation(summary = "Retry PLP anchor sync")
    public ResponseEntity<PlpSyncResultResponse> retryAnchor(@PathVariable UUID anchorId) {
        AnchorMaster anchor = plpAnchorSyncService.sync(anchorId);
        return ResponseEntity.ok(toResponse(anchor.getId(), anchor.getPlpAnchorSyncStatus(),
                anchor.getPlpAnchorSyncError(), anchor.getPlpAnchorSyncedAt(), anchor.getPlpAnchorId()));
    }

    @PostMapping("/api/v1/programs/{programId}/plp-sync/retry")
    @Operation(summary = "Retry PLP program sync")
    public ResponseEntity<PlpSyncResultResponse> retryProgram(@PathVariable UUID programId) {
        ProgramMaster program = plpProgramSyncService.sync(programId);
        return ResponseEntity.ok(toResponse(program.getId(), program.getPlpProgramSyncStatus(),
                program.getPlpProgramSyncError(), program.getPlpProgramSyncedAt(), program.getPlpProgramId()));
    }

    @PostMapping("/api/v1/sub-programs/{subProgramId}/plp-sync/retry")
    @Operation(summary = "Retry PLP sub-program sync")
    public ResponseEntity<PlpSyncResultResponse> retrySubProgram(@PathVariable UUID subProgramId) {
        SubProgramMaster subProgram = plpSubProgramSyncService.sync(subProgramId);
        return ResponseEntity.ok(toResponse(subProgram.getId(), subProgram.getPlpSubProgramSyncStatus(),
                subProgram.getPlpSubProgramSyncError(), subProgram.getPlpSubProgramSyncedAt(),
                subProgram.getPlpSubProgramId()));
    }

    @PostMapping("/api/v1/borrowers/{borrowerId}/plp-sync/retry")
    @Operation(summary = "Retry PLP borrower sync for latest application of customer")
    public ResponseEntity<PlpSyncResultResponse> retryBorrower(@PathVariable UUID borrowerId) {
        LoanApplication app = loanApplicationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("No application found for customer: " + borrowerId));
        app = plpBorrowerSyncService.syncForCustomer(borrowerId, app.getId());
        return ResponseEntity.ok(toResponse(app.getId(), app.getPlpProgramSyncStatus(),
                app.getPlpProgramSyncError(), app.getPlpProgramSyncedAt(), app.getPlpBorrowerId()));
    }

    @PostMapping("/api/v1/applications/{applicationId}/plp-sync/retry")
    @Operation(summary = "Retry full PLP sanction sync for application")
    public ResponseEntity<PlpSyncResultResponse> retryApplication(@PathVariable UUID applicationId) {
        LoanApplication app = plpSanctionSyncOrchestrator.syncAfterSanction(applicationId);
        return ResponseEntity.ok(toResponse(app.getId(), app.getPlpProgramSyncStatus(),
                app.getPlpProgramSyncError(), app.getPlpProgramSyncedAt(), app.getPlpBorrowerProgramMappingId()));
    }

    private static PlpSyncResultResponse toResponse(
            UUID id,
            PlpSyncStatus status,
            String error,
            java.time.Instant syncedAt,
            UUID plpId) {
        return PlpSyncResultResponse.builder()
                .id(id)
                .syncStatus(status)
                .syncError(error)
                .syncedAt(syncedAt)
                .plpId(plpId)
                .build();
    }
}
