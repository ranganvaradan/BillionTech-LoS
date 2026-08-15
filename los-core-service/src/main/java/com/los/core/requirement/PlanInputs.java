package com.los.core.requirement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inputs for future Policy inventory → plan generation (W4).
 * W3 only defines the contract; {@link DataRequirementPlanner} does not generate from Policy yet.
 */
public record PlanInputs(
        UUID applicationId,
        UUID policyDocumentId,
        UUID workflowId,
        Map<String, Object> inventoryHints
) {
    public PlanInputs {
        if (inventoryHints == null) {
            inventoryHints = Map.of();
        }
    }

    public static PlanInputs of(UUID applicationId, UUID policyDocumentId, UUID workflowId) {
        return new PlanInputs(applicationId, policyDocumentId, workflowId, Map.of());
    }
}
