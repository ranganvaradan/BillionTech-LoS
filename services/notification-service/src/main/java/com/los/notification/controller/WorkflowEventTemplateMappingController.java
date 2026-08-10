package com.los.notification.controller;

import com.los.notification.dto.WorkflowEventTemplateMappingResponse;
import com.los.notification.service.WorkflowEventTemplateMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-event-template-mappings")
@RequiredArgsConstructor
@Tag(name = "Workflow notification catalog", description = "Workflow event ↔ template mappings for configuration UIs")
public class WorkflowEventTemplateMappingController {

    private final WorkflowEventTemplateMappingService mappingService;

    @GetMapping
    @Operation(summary = "List workflow event template mappings")
    public ResponseEntity<List<WorkflowEventTemplateMappingResponse>> list(
            @RequestParam(required = false) String workflowEvent,
            @RequestParam(required = false) String channel) {
        return ResponseEntity.ok(mappingService.list(workflowEvent, channel));
    }
}
