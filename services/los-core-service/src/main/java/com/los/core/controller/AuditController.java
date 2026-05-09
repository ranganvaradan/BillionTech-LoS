package com.los.core.controller;

import com.los.core.model.entity.AuditEvent;
import com.los.core.service.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail for application events")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get audit trail for an application")
    public ResponseEntity<Page<AuditEvent>> getAuditTrail(
            @PathVariable UUID applicationId, Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditTrail(applicationId, pageable));
    }
}
