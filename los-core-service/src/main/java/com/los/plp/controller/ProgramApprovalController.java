package com.los.plp.controller;

import com.los.plp.model.dto.ProgramApprovalNotesRequest;
import com.los.plp.model.dto.ProgramApprovalResponse;
import com.los.plp.service.ProgramApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plp/programs")
@RequiredArgsConstructor
@Tag(name = "Program Approval", description = "L1/L2 program approval workflow")
public class ProgramApprovalController {

    private final ProgramApprovalService programApprovalService;

    @GetMapping("/approvals/pending")
    @Operation(summary = "List programs pending approval for current user")
    public ResponseEntity<List<ProgramApprovalResponse>> listPending(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(programApprovalService.listPendingForUser(actor));
    }

    @GetMapping("/{programId}/approval")
    public ResponseEntity<ProgramApprovalResponse> getApproval(@PathVariable UUID programId) {
        return ResponseEntity.ok(programApprovalService.getApproval(programId));
    }

    @PostMapping("/{programId}/approval/submit-to-l2")
    public ResponseEntity<ProgramApprovalResponse> submitToL2(
            @PathVariable UUID programId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(programApprovalService.submitToL2(programId, actor));
    }

    @PostMapping("/{programId}/approval/send-back")
    public ResponseEntity<ProgramApprovalResponse> sendBack(
            @PathVariable UUID programId,
            @Valid @RequestBody ProgramApprovalNotesRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(programApprovalService.sendBack(programId, actor, body.getNotes()));
    }

    @PostMapping("/{programId}/approval/approve")
    public ResponseEntity<ProgramApprovalResponse> approve(
            @PathVariable UUID programId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID actor = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(programApprovalService.approve(programId, actor));
    }
}
