package com.los.core.controller;

import com.los.core.model.dto.response.WorkflowEventTemplateMappingResponse;
import com.los.core.service.integration.NotificationServiceCatalogClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Proxies workflow notification template mappings from notification-service for the SPA
 * ({@code vite} proxies {@code /api} to los-core only).
 */
@RestController
@RequestMapping("/api/v1/workflow-event-template-mappings")
@RequiredArgsConstructor
@Tag(name = "Workflow notification catalog", description = "Template mappings proxied from notification-service")
public class WorkflowEventTemplateMappingCatalogController {

    private final NotificationServiceCatalogClient notificationServiceCatalogClient;

    @GetMapping
    @Operation(summary = "List workflow event → template mappings (from notification-service)")
    public ResponseEntity<List<WorkflowEventTemplateMappingResponse>> list(
            @RequestParam(required = false) String workflowEvent,
            @RequestParam(required = false) String channel) {
        return ResponseEntity.ok(
                notificationServiceCatalogClient.listWorkflowEventTemplateMappings(workflowEvent, channel));
    }
}
