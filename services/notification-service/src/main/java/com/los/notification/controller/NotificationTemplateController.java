package com.los.notification.controller;

import com.los.notification.dto.NotificationTemplatePreviewRequest;
import com.los.notification.dto.NotificationTemplateUpsertRequest;
import com.los.notification.entity.NotificationTemplate;
import com.los.notification.service.NotificationTemplateService;
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
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
@Tag(name = "Notification Templates", description = "Manage dynamic notification templates")
public class NotificationTemplateController {

    private final NotificationTemplateService notificationTemplateService;

    @GetMapping
    @Operation(summary = "List notification templates")
    public ResponseEntity<List<NotificationTemplate>> listTemplates() {
        return ResponseEntity.ok(notificationTemplateService.listTemplates());
    }

    @PostMapping
    @Operation(summary = "Create notification template")
    public ResponseEntity<NotificationTemplate> createTemplate(@RequestBody NotificationTemplateUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationTemplateService.createTemplate(request));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Update notification template")
    public ResponseEntity<NotificationTemplate> updateTemplate(
            @PathVariable UUID templateId,
            @RequestBody NotificationTemplateUpsertRequest request) {
        return ResponseEntity.ok(notificationTemplateService.updateTemplate(templateId, request));
    }

    @PostMapping("/{templateId}/toggle")
    @Operation(summary = "Enable/disable notification template")
    public ResponseEntity<NotificationTemplate> toggleTemplate(
            @PathVariable UUID templateId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(notificationTemplateService.toggleTemplate(templateId, active));
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview rendered template")
    public ResponseEntity<Map<String, String>> previewTemplate(@RequestBody NotificationTemplatePreviewRequest request) {
        return ResponseEntity.ok(notificationTemplateService.previewTemplate(request));
    }
}
