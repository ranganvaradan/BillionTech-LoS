package com.los.core.model.dto.response;

import java.util.UUID;

/**
 * Mirrors notification-service payload for workflow step notification template pickers.
 */
public record WorkflowEventTemplateMappingResponse(
        UUID id,
        String workflowEvent,
        String channel,
        String templateCode,
        boolean defaultMapping,
        boolean active,
        int sortOrder) {
}
