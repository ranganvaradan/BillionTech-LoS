package com.los.core.service.workflow;

/**
 * How an application's Workflow Version was resolved (W1).
 */
public enum WorkflowResolutionSource {
    /** Client supplied workflowId at create/update and it was validated. */
    EXPLICIT,
    /** Highest active workflow for borrowerType × product × intakeSegment. */
    DEFAULT,
    /** Pre-W1 application already had workflow_id; metadata backfilled. */
    LEGACY_EXISTING,
    /** Explicit data migration (reserved). */
    MIGRATED
}
