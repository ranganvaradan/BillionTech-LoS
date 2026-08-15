package com.los.core.requirement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W6 acquisition orchestration DTOs.
 */
public final class AcquisitionDtos {

    private AcquisitionDtos() {}

    public record OrchestrationRequest(
            UUID planId,
            UUID applicationId,
            String actor,
            boolean dryRun
    ) {}

    public record SourceAttemptView(
            UUID attemptId,
            UUID itemId,
            String itemKey,
            String sourceKey,
            SourceAcquisitionState status,
            String failureClass,
            String failureReason,
            String previousSourceKey,
            String fallbackSourceKey,
            Map<String, Object> resultSummary,
            java.time.Instant startedAt,
            java.time.Instant finishedAt
    ) {}

    public record OrchestrationCounters(
            int automaticTotal,
            int automaticCompleted,
            int automaticProcessing,
            int automaticFailed,
            int customerTotal,
            int customerProvided,
            int customerOutstanding,
            int derivedTotal,
            int derivedReady,
            int derivedWaiting,
            int blocked,
            int manualReview
    ) {}

    public record GateResult(
            DataCompletenessGateStatus status,
            List<String> reasons,
            OrchestrationCounters counters,
            boolean policyAutoExecuted,
            boolean scorecardAutoExecuted
    ) {}

    public record OrchestrationResult(
            UUID planId,
            UUID applicationId,
            int planVersion,
            GateResult gate,
            List<String> executedSources,
            List<String> skippedSources,
            List<String> deferredForDependency,
            List<SourceAttemptView> attempts,
            Map<String, Object> diagnostics
    ) {}

    public record ExecutorOutcome(
            SourceAcquisitionState status,
            String providerRef,
            String externalRef,
            String failureClass,
            String failureReason,
            Map<String, Object> resultSummary,
            /** Canonical facts claimed written/available after this execution (parameterId → ready). */
            Map<String, Boolean> factReadiness,
            boolean providerHttpSuccess
    ) {
        public static ExecutorOutcome succeeded(String provider, Map<String, Boolean> facts, Map<String, Object> summary) {
            return new ExecutorOutcome(
                    SourceAcquisitionState.SUCCEEDED, provider, null, null, null,
                    summary != null ? summary : Map.of(),
                    facts != null ? facts : Map.of(),
                    true);
        }

        public static ExecutorOutcome retryable(String provider, String reason, Map<String, Object> summary) {
            return new ExecutorOutcome(
                    SourceAcquisitionState.FAILED_RETRYABLE, provider, null, "RETRYABLE", reason,
                    summary != null ? summary : Map.of(), Map.of(), false);
        }

        public static ExecutorOutcome terminal(String provider, String reason, Map<String, Object> summary) {
            return new ExecutorOutcome(
                    SourceAcquisitionState.FAILED_TERMINAL, provider, null, "TERMINAL", reason,
                    summary != null ? summary : Map.of(), Map.of(), false);
        }

        public static ExecutorOutcome unavailable(String provider, String reason) {
            return new ExecutorOutcome(
                    SourceAcquisitionState.UNAVAILABLE, provider, null, "UNAVAILABLE", reason,
                    Map.of(), Map.of(), false);
        }

        public static ExecutorOutcome skipped(String reason) {
            return new ExecutorOutcome(
                    SourceAcquisitionState.NOT_REQUIRED, null, null, "SKIPPED", reason,
                    Map.of(), Map.of(), false);
        }
    }
}
