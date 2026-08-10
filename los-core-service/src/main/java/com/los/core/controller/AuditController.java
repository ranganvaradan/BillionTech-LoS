package com.los.core.controller;

import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.entity.AuditEvent;
import com.los.core.model.entity.EntityRecordAudit;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.AuditEventRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.audit.RecordAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail for application and entity events")
public class AuditController {

    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;
    private final RecordAuditService recordAuditService;
    private final ApiAuditLogRepository apiAuditLogRepository;

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get audit trail for an application")
    public ResponseEntity<Page<AuditEvent>> getAuditTrail(
            @PathVariable UUID applicationId, Pageable pageable) {
        return ResponseEntity.ok(auditService.getAuditTrail(applicationId, pageable));
    }

    @GetMapping
    @Operation(summary = "Admin: list application audit events (newest first)")
    public ResponseEntity<Page<AuditEvent>> listAdminAuditEvents(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        if (applicationId != null) {
            return ResponseEntity.ok(auditEventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable));
        }
        return ResponseEntity.ok(auditEventRepository.findAll(pageable));
    }

    @GetMapping("/records")
    @Operation(summary = "Admin: list entity record audits with optional filters")
    public ResponseEntity<Page<EntityRecordAudit>> listRecordAudits(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(recordAuditService.search(entityType, entityId, applicationId, pageable));
    }

    @GetMapping("/api-calls")
    @Operation(summary = "Admin: list integration API request/response audits")
    public ResponseEntity<Page<ApiAuditLog>> listApiAudits(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        if (applicationId != null) {
            return ResponseEntity.ok(apiAuditLogRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable));
        }
        if (provider != null && !provider.isBlank()) {
            return ResponseEntity.ok(
                    apiAuditLogRepository.findByProviderNameIgnoreCaseOrderByCreatedAtDesc(provider.trim(), pageable));
        }
        return ResponseEntity.ok(apiAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable));
    }
}
