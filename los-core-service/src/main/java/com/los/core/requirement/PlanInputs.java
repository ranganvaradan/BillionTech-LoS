package com.los.core.requirement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inputs for Policy inventory → RequirementPlan generation (W4).
 * <p>
 * {@code inventoryHints} carries controlled staging/test overlays only — never a parallel Policy authority.
 * Proven Policy parameter inventory remains DP-3 {@code PolicyRuleGraphService.parameterInventory}.
 */
public record PlanInputs(
        UUID applicationId,
        UUID policyDocumentId,
        UUID workflowId,
        UUID customerCategoryId,
        UUID policyApplicabilityId,
        String workflowVersion,
        UUID replanFromPlanId,
        Map<String, Object> inventoryHints
) {
    public PlanInputs {
        if (inventoryHints == null) {
            inventoryHints = Map.of();
        } else {
            inventoryHints = Map.copyOf(new LinkedHashMap<>(inventoryHints));
        }
    }

    public static PlanInputs of(UUID applicationId, UUID policyDocumentId, UUID workflowId) {
        return new PlanInputs(applicationId, policyDocumentId, workflowId, null, null, null, null, Map.of());
    }

    public static PlanInputs of(
            UUID applicationId,
            UUID policyDocumentId,
            UUID workflowId,
            Map<String, Object> inventoryHints) {
        return new PlanInputs(applicationId, policyDocumentId, workflowId, null, null, null, null, inventoryHints);
    }

    public PlanInputs withHints(Map<String, Object> hints) {
        return new PlanInputs(
                applicationId, policyDocumentId, workflowId, customerCategoryId, policyApplicabilityId,
                workflowVersion, replanFromPlanId, hints);
    }
}
