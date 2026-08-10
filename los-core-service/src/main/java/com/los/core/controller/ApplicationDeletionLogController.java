package com.los.core.controller;

import com.los.core.model.entity.ApplicationDeletionLog;
import com.los.core.repository.ApplicationDeletionLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/application-deletions")
@RequiredArgsConstructor
@Tag(name = "Application deletions", description = "Audit log of deleted borrower applications")
public class ApplicationDeletionLogController {

    private final ApplicationDeletionLogRepository deletionLogRepository;

    @GetMapping
    @Operation(summary = "List application deletion audit entries (newest first)")
    public ResponseEntity<Page<ApplicationDeletionLog>> list(Pageable pageable) {
        return ResponseEntity.ok(deletionLogRepository.findAllByOrderByDeletedAtDesc(pageable));
    }
}
