package com.los.notification.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record WorkflowEventTemplateMappingResponse(
        UUID id,
        String workflowEvent,
        String channel,
        String templateCode,
        boolean defaultMapping,
        boolean active,
        int sortOrder) {
}
