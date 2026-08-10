package com.los.core.service.workflow.coordinator;

import com.los.core.service.flow.step.FlowStepType;

import java.util.List;

/**
 * Tuning for {@link WorkflowFlowStepOrderResolver} (no persistence).
 */
public record WorkflowResolutionOptions(
        /** When the active workflow has KYC sub-steps but no BUREAU_PULL row, still align with legacy full flow (bureau after KYC). */
        boolean addBureauWhenMissingFromConfig,
        /**
         * When {@code steps} is empty, use this sequence (e.g. integration tests or bad data),
         * defaulting to KYC then BUREAU to match the historical happy path.
         */
        List<String> emptyConfigFallback) {

    public static WorkflowResolutionOptions defaults() {
        return new WorkflowResolutionOptions(
                true,
                List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL)
        );
    }
}
