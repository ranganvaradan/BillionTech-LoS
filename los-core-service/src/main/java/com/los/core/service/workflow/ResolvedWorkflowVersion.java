package com.los.core.service.workflow;

import com.los.core.model.entity.WorkflowConfig;

import java.time.Instant;
import java.util.UUID;

/**
 * W1 — stable resolved Workflow Version for one application.
 * {@code workflowId} is {@code workflow_configs.id} (row = version identity today).
 */
public record ResolvedWorkflowVersion(
        UUID workflowId,
        int workflowVersion,
        WorkflowResolutionSource resolutionSource,
        Instant resolvedAt,
        String contentHash,
        boolean definitionMutatedSinceResolve,
        WorkflowConfig config
) {
    public UUID id() {
        return workflowId;
    }
}
