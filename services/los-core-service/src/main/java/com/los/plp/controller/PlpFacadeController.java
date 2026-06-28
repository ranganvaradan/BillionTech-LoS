package com.los.plp.controller;

import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.loan.ILoanApplicationService;
import com.los.plp.model.dto.*;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plp")
@RequiredArgsConstructor
@Tag(name = "PLP Facade", description = "Invoice discounting PLP program management and sync")
public class PlpFacadeController {

    private final PlpProgramSetupService plpProgramSetupService;
    private final PlpProgramQueryService plpProgramQueryService;
    private final PlpApplicationLinkService plpApplicationLinkService;
    private final PlpAnchorSyncService plpAnchorSyncService;
    private final PlpProgramSyncService plpProgramSyncService;
    private final PlpSubProgramSyncService plpSubProgramSyncService;
    private final PlpBorrowerSyncService plpBorrowerSyncService;
    private final PlpSanctionSyncOrchestrator plpSanctionSyncOrchestrator;
    private final AnchorMasterService anchorMasterService;
    private final ILoanApplicationService loanApplicationService;
    private final LoanApplicationRepository loanApplicationRepository;

    @PostMapping("/programs")
    @Operation(summary = "Create PLP program and sub-program for an anchor")
    public ResponseEntity<PlpProgramSetupResponse> createProgram(
            @Valid @RequestBody CreatePlpProgramRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(plpProgramSetupService.createProgramForAnchor(request));
    }

    @GetMapping("/programs")
    @Operation(summary = "List all PLP programs with anchor info and borrower counts")
    public ResponseEntity<List<PlpProgramSummaryResponse>> listPrograms() {
        return ResponseEntity.ok(plpProgramQueryService.listPrograms());
    }

    @GetMapping("/programs/selectable")
    @Operation(summary = "List programs available for borrower selection (fully synced)")
    public ResponseEntity<List<PlpProgramSummaryResponse>> listSelectablePrograms() {
        return ResponseEntity.ok(plpProgramQueryService.listSelectablePrograms());
    }

    @GetMapping("/programs/{programId}")
    @Operation(summary = "Get PLP program detail")
    public ResponseEntity<PlpProgramDetailResponse> getProgram(@PathVariable UUID programId) {
        return ResponseEntity.ok(plpProgramQueryService.getProgramDetail(programId));
    }

    @GetMapping("/sub-programs/{subProgramId}/summary")
    @Operation(summary = "Summary of anchor/program/sub-program linked to a borrower application")
    public ResponseEntity<PlpLinkedSubProgramSummaryResponse> getSubProgramSummary(
            @PathVariable UUID subProgramId) {
        return ResponseEntity.ok(plpProgramQueryService.getLinkedSubProgramSummary(subProgramId));
    }

    @GetMapping("/programs/anchor/{anchorId}")
    @Operation(summary = "List programs for a specific anchor")
    public ResponseEntity<List<PlpProgramSummaryResponse>> listProgramsForAnchor(
            @PathVariable UUID anchorId,
            @RequestParam(defaultValue = "false") boolean syncedOnly) {
        return ResponseEntity.ok(plpProgramQueryService.listProgramsForAnchor(anchorId, syncedOnly));
    }

    @GetMapping("/anchors/synced")
    @Operation(summary = "List anchors successfully synced to PLP")
    public ResponseEntity<List<PlpSyncedAnchorResponse>> listSyncedAnchors() {
        List<PlpSyncedAnchorResponse> anchors = anchorMasterService.listSyncedToPlp().stream()
                .map(a -> PlpSyncedAnchorResponse.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .code(a.getCode())
                        .plpAnchorId(a.getPlpAnchorId() != null ? a.getPlpAnchorId().toString() : null)
                        .sourceAnchorApplicationId(a.getSourceAnchorApplicationId())
                        .build())
                .toList();
        return ResponseEntity.ok(anchors);
    }

    @GetMapping("/anchors/{anchorId}/borrowers")
    @Operation(summary = "List borrower applications linked to anchor programs")
    public ResponseEntity<List<PlpAnchorBorrowerResponse>> listAnchorBorrowers(@PathVariable UUID anchorId) {
        return ResponseEntity.ok(plpProgramQueryService.listBorrowersForAnchor(anchorId));
    }

    @PostMapping("/applications/{applicationId}/link-program")
    @Operation(summary = "Link invoice discounting application to a PLP sub-program")
    public ResponseEntity<ApplicationResponse> linkProgram(
            @PathVariable UUID applicationId,
            @Valid @RequestBody LinkProgramRequest request) {
        plpApplicationLinkService.linkProgram(applicationId, request);
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @PostMapping("/retry/anchor/{anchorId}")
    public ResponseEntity<PlpSyncResultResponse> retryAnchor(@PathVariable UUID anchorId) {
        AnchorMaster anchor = plpAnchorSyncService.sync(anchorId);
        if (anchor == null) {
            throw new IllegalArgumentException("Anchor not found: " + anchorId);
        }
        return ResponseEntity.ok(toResponse(anchor.getId(), anchor.getPlpAnchorSyncStatus(),
                anchor.getPlpAnchorSyncError(), anchor.getPlpAnchorSyncedAt(), anchor.getPlpAnchorId()));
    }

    @PostMapping("/retry/program/{programId}")
    public ResponseEntity<PlpSyncResultResponse> retryProgram(@PathVariable UUID programId) {
        ProgramMaster program = plpProgramSyncService.sync(programId);
        return ResponseEntity.ok(toResponse(program.getId(), program.getPlpProgramSyncStatus(),
                program.getPlpProgramSyncError(), program.getPlpProgramSyncedAt(), program.getPlpProgramId()));
    }

    @PostMapping("/retry/sub-program/{programId}")
    @Operation(summary = "Retry sub-program sync; path param is sub-program id")
    public ResponseEntity<PlpSyncResultResponse> retrySubProgram(@PathVariable UUID programId) {
        SubProgramMaster subProgram = plpSubProgramSyncService.sync(programId);
        return ResponseEntity.ok(toResponse(subProgram.getId(), subProgram.getPlpSubProgramSyncStatus(),
                subProgram.getPlpSubProgramSyncError(), subProgram.getPlpSubProgramSyncedAt(),
                subProgram.getPlpSubProgramId()));
    }

    @PostMapping("/retry/borrower/{applicationId}")
    public ResponseEntity<PlpSyncResultResponse> retryBorrower(@PathVariable UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        app = plpBorrowerSyncService.syncBorrowerStep(app);
        return ResponseEntity.ok(toResponse(app.getId(), app.getPlpBorrowerSyncStatus(),
                app.getPlpProgramSyncError(), app.getPlpBorrowerSyncedAt(), app.getPlpBorrowerId()));
    }

    @PostMapping("/retry/application/{applicationId}")
    public ResponseEntity<PlpSyncResultResponse> retryApplication(@PathVariable UUID applicationId) {
        LoanApplication app = plpSanctionSyncOrchestrator.syncAfterSanction(applicationId);
        return ResponseEntity.ok(toResponse(app.getId(), app.getPlpProgramSyncStatus(),
                app.getPlpProgramSyncError(), app.getPlpProgramSyncedAt(), app.getPlpBorrowerProgramMappingId()));
    }

    private static PlpSyncResultResponse toResponse(
            UUID id,
            com.los.plp.model.enums.PlpSyncStatus status,
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
