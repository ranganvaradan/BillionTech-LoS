package com.los.core.controller;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.service.loan.ApplicationExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-2.9: Application cloning/renewal.
 * BR-2.10: Bulk application processing.
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Application Extensions", description = "Clone, renew, and bulk process applications")
public class ApplicationExtensionController {

    private final ApplicationExtensionService extensionService;

    @PostMapping("/{applicationId}/clone")
    @Operation(summary = "BR-2.9: Clone an application for renewal")
    public ResponseEntity<LoanApplication> cloneApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extensionService.cloneApplication(applicationId));
    }

    @PostMapping("/bulk/transition")
    @Operation(summary = "BR-2.10: Bulk status transition")
    public ResponseEntity<Map<String, Object>> bulkTransition(
            @RequestBody List<UUID> applicationIds,
            @RequestParam ApplicationStatus newStatus,
            @RequestParam(required = false) String remarks) {
        return ResponseEntity.ok(extensionService.bulkTransition(applicationIds, newStatus, remarks));
    }

    @PostMapping("/bulk/assign")
    @Operation(summary = "BR-2.10: Bulk assign applications")
    public ResponseEntity<Map<String, Object>> bulkAssign(
            @RequestBody List<UUID> applicationIds,
            @RequestParam UUID assigneeId) {
        return ResponseEntity.ok(extensionService.bulkAssign(applicationIds, assigneeId));
    }
}
