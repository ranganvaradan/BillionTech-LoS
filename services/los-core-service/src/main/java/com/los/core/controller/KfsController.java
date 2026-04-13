package com.los.core.controller;

import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.KfsTemplate;
import com.los.core.service.kfs.KfsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kfs")
@RequiredArgsConstructor
@Tag(name = "KFS (Key Fact Statement)", description = "RBI-compliant KFS generation, acknowledgement, cooling-off, and template management")
public class KfsController {

    private final KfsService kfsService;

    @PostMapping("/generate/{applicationId}")
    @Operation(summary = "Generate KFS for an application after sanction")
    public ResponseEntity<KfsDocument> generateKfs(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> charges) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kfsService.generateKfs(applicationId, charges));
    }

    @PostMapping("/{kfsId}/acknowledge")
    @Operation(summary = "Record KFS acknowledgement — starts cooling-off period")
    public ResponseEntity<KfsDocument> acknowledge(
            @PathVariable UUID kfsId,
            @RequestParam UUID acknowledgedBy) {
        return ResponseEntity.ok(kfsService.acknowledgeKfs(kfsId, acknowledgedBy));
    }

    @GetMapping("/{applicationId}/cooling-off-status")
    @Operation(summary = "Check if cooling-off period has completed")
    public ResponseEntity<Map<String, Object>> coolingOffStatus(@PathVariable UUID applicationId) {
        boolean complete = kfsService.isCoolingOffComplete(applicationId);
        KfsDocument kfs = kfsService.getLatestKfs(applicationId);
        return ResponseEntity.ok(Map.of(
                "coolingOffComplete", complete,
                "status", kfs.getStatus(),
                "coolingOffExpiresAt", kfs.getCoolingOffExpiresAt() != null ? kfs.getCoolingOffExpiresAt().toString() : "N/A",
                "coolingOffHours", kfs.getCoolingOffHours()
        ));
    }

    @PostMapping("/{kfsId}/esign")
    @Operation(summary = "Record eSign on KFS (only after cooling-off period)")
    public ResponseEntity<KfsDocument> esign(
            @PathVariable UUID kfsId,
            @RequestParam String esignTransactionId) {
        return ResponseEntity.ok(kfsService.esignKfs(kfsId, esignTransactionId));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get all KFS versions for an application")
    public ResponseEntity<List<KfsDocument>> getKfsHistory(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(kfsService.getKfsHistory(applicationId));
    }

    @GetMapping("/application/{applicationId}/latest")
    @Operation(summary = "Get latest KFS for an application")
    public ResponseEntity<KfsDocument> getLatest(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(kfsService.getLatestKfs(applicationId));
    }

    // ---- Template Management ----

    @PostMapping("/templates")
    @Operation(summary = "Create a KFS template")
    public ResponseEntity<KfsTemplate> createTemplate(@RequestBody KfsTemplate template) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kfsService.createTemplate(template));
    }

    @PutMapping("/templates/{templateId}")
    @Operation(summary = "Update a KFS template")
    public ResponseEntity<KfsTemplate> updateTemplate(
            @PathVariable UUID templateId,
            @RequestBody KfsTemplate updates) {
        return ResponseEntity.ok(kfsService.updateTemplate(templateId, updates));
    }

    @GetMapping("/templates")
    @Operation(summary = "List KFS templates")
    public ResponseEntity<List<KfsTemplate>> listTemplates(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(kfsService.listTemplates(activeOnly));
    }

    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "Deactivate a KFS template")
    public ResponseEntity<Void> deactivateTemplate(@PathVariable UUID templateId) {
        kfsService.deactivateTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
