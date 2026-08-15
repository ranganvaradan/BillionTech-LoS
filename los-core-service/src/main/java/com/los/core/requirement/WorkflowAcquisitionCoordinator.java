package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single application-level Workflow acquisition coordinator (W6).
 * RequirementPlan determines WHAT; this coordinator determines WHEN/HOW preferred paths execute.
 * Does not run Policy or Scorecard. Does not execute alternative sources in parallel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAcquisitionCoordinator {

    private final RequirementPlanRepository planRepository;
    private final RequirementItemRepository itemRepository;
    private final RequirementAcquisitionAttemptRepository attemptRepository;
    private final RequirementItemTransitionService transitionService;
    private final AcquisitionExecutorRegistry executorRegistry;
    private final CanonicalFactReadinessReconciler readinessReconciler;
    private final DataCompletenessGate dataCompletenessGate;

    @Transactional
    public AcquisitionDtos.OrchestrationResult orchestrate(UUID planId, String actor) {
        return orchestrate(new AcquisitionDtos.OrchestrationRequest(planId, null, actor, false));
    }

    @Transactional
    public AcquisitionDtos.OrchestrationResult orchestrate(AcquisitionDtos.OrchestrationRequest request) {
        if (request == null || request.planId() == null) {
            throw new BusinessRuleException("planId is required");
        }
        String actor = request.actor() != null ? request.actor() : "W6_COORDINATOR";
        RequirementPlanEntity plan = planRepository.findByIdWithItems(request.planId())
                .orElseThrow(() -> new BusinessRuleException("RequirementPlan not found: " + request.planId()));

        UUID applicationId = plan.getApplicationId();
        int planVersion = plan.getPlanVersion();

        Set<String> readyParams = new HashSet<>();
        for (RequirementItemEntity item : plan.getItems()) {
            if (item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY) {
                if (item.getCanonicalParameterId() != null) {
                    readyParams.add(item.getCanonicalParameterId());
                }
                readyParams.add(item.getItemKey());
            }
        }

        List<String> executed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> deferred = new ArrayList<>();
        List<AcquisitionDtos.SourceAttemptView> attemptViews = new ArrayList<>();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("workflowOrchestrator", "W6_WorkflowAcquisitionCoordinator");
        diagnostics.put("policyAutoExecution", false);
        diagnostics.put("scorecardAutoExecution", false);
        diagnostics.put("registeredExecutors", executorRegistry.diagnostics());
        diagnostics.put("planVersion", planVersion);
        diagnostics.put("policyDocumentId", plan.getPolicyDocumentId() != null
                ? plan.getPolicyDocumentId().toString() : null);
        diagnostics.put("workflowId", plan.getWorkflowId() != null
                ? plan.getWorkflowId().toString() : null);

        // Customer wait must NOT block unrelated automatic acquisitions
        List<RequirementItemEntity> candidates = new ArrayList<>();
        for (RequirementItemEntity item : plan.getItems()) {
            FulfilmentMode mode = AcquisitionSourceResolver.effectiveMode(item);
            if (mode == FulfilmentMode.DIRECT_INPUT
                    || (mode == FulfilmentMode.DOCUMENT_UPLOAD
                    && item.getCustomerFulfilmentState() != CustomerFulfilmentState.PROVIDED)
                    || mode == FulfilmentMode.MANUAL_REVIEW) {
                skipped.add(item.getItemKey() + ":" + (mode != null ? mode.name() : "NONE"));
                continue;
            }
            if ("NO_FULFILMENT_PATH".equals(String.valueOf(
                    item.getSourceHints() != null ? item.getSourceHints().get("blockingReason") : null))) {
                skipped.add(item.getItemKey() + ":NO_FULFILMENT_PATH");
                continue;
            }
            if (AcquisitionSourceResolver.isPlatformExecutable(item)) {
                candidates.add(item);
            } else {
                skipped.add(item.getItemKey() + ":not-executable");
            }
        }

        AcquisitionDependencyGraph.Schedule schedule =
                AcquisitionDependencyGraph.schedule(candidates, readyParams);
        for (RequirementItemEntity d : schedule.deferred()) {
            deferred.add(d.getItemKey());
            // Mark waiting for dependency without inventing values
            if (d.getSourceAcquisitionState() == SourceAcquisitionState.NOT_STARTED
                    || d.getSourceAcquisitionState() == SourceAcquisitionState.WAITING
                    || d.getSourceAcquisitionState() == null) {
                transitionService.advanceSource(plan.getId(), d.getId(),
                        SourceAcquisitionState.WAITING, actor, "Waiting on derivation/source dependencies");
            }
        }

        diagnostics.put("waveCount", schedule.waves().size());

        List<List<String>> waveKeys = new ArrayList<>();
        for (AcquisitionDependencyGraph.Wave wave : schedule.waves()) {
            // Same wave = dependency-independent; may run concurrently. Persist sequentially
            // for JPA safety; wave membership proves parallel capability.
            List<String> keys = new ArrayList<>();
            for (RequirementItemEntity item : wave.items()) {
                keys.add(item.getItemKey());
                ItemExecResult r = executeOne(plan, item, applicationId, planVersion, actor, request.dryRun());
                applyResult(plan, item, r, actor);
                if (r.executed()) {
                    executed.add(item.getItemKey() + ":" + r.sourceKey());
                } else if (r.skippedReason() != null) {
                    skipped.add(item.getItemKey() + ":" + r.skippedReason());
                }
                if (r.attemptView() != null) {
                    attemptViews.add(r.attemptView());
                }
                RequirementItemEntity refreshed = itemRepository.findByIdAndPlanId(item.getId(), plan.getId())
                        .orElse(item);
                if (refreshed.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY) {
                    if (refreshed.getCanonicalParameterId() != null) {
                        readyParams.add(refreshed.getCanonicalParameterId());
                    }
                    readyParams.add(refreshed.getItemKey());
                }
            }
            waveKeys.add(keys);
        }
        diagnostics.put("parallelWaves", waveKeys);
        diagnostics.put("parallelCapable", waveKeys.stream().anyMatch(w -> w.size() > 1));

        // Reload plan for gate
        RequirementPlanEntity fresh = planRepository.findByIdWithItems(plan.getId()).orElse(plan);
        AcquisitionDtos.GateResult gate = dataCompletenessGate.evaluate(fresh);
        diagnostics.put("gateStatus", gate.status().name());
        diagnostics.put("counters", gate.counters());

        // Persist gate snapshot on plan metadata (readiness event only — no Policy run)
        Map<String, Object> meta = fresh.getMetadata() != null
                ? new LinkedHashMap<>(fresh.getMetadata()) : new LinkedHashMap<>();
        meta.put("dataCompletenessGate", gate.status().name());
        meta.put("dataCompletenessGateAt", Instant.now().toString());
        meta.put("dataCompletenessReasons", gate.reasons());
        meta.put("w6Orchestration", Map.of(
                "executed", executed,
                "skipped", skipped,
                "deferred", deferred,
                "policyAutoExecuted", false,
                "scorecardAutoExecuted", false));
        fresh.setMetadata(meta);
        if (fresh.getStatus() == RequirementPlanStatus.DRAFT) {
            fresh.setStatus(RequirementPlanStatus.ACTIVE);
        }
        planRepository.save(fresh);

        return new AcquisitionDtos.OrchestrationResult(
                fresh.getId(),
                applicationId,
                planVersion,
                gate,
                List.copyOf(executed),
                List.copyOf(skipped),
                List.copyOf(deferred),
                List.copyOf(attemptViews),
                diagnostics);
    }

    private ItemExecResult executeOne(
            RequirementPlanEntity plan,
            RequirementItemEntity item,
            UUID applicationId,
            int planVersion,
            String actor,
            boolean dryRun) {

        String sourceKey = AcquisitionSourceResolver.preferredSourceKey(item);
        if (sourceKey == null) {
            return ItemExecResult.skipped("no-preferred-source");
        }

        // Only preferred source — never alternative simultaneously
        String executionKey = RequirementAcquisitionAttemptEntity.buildExecutionKey(
                applicationId, planVersion, item.getItemKey(), sourceKey);

        Optional<RequirementAcquisitionAttemptEntity> existing = attemptRepository.findByExecutionKey(executionKey);
        if (existing.isPresent()) {
            RequirementAcquisitionAttemptEntity prior = existing.get();
            SourceAcquisitionState st = prior.getStatus();
            if (st == SourceAcquisitionState.SUCCEEDED
                    || st == SourceAcquisitionState.IN_PROGRESS
                    || st == SourceAcquisitionState.QUEUED
                    || st == SourceAcquisitionState.CUSTOMER_ACTION_REQUIRED
                    || st == SourceAcquisitionState.CUSTOMER_FALLBACK
                    || st == SourceAcquisitionState.FAILED_TERMINAL
                    || st == SourceAcquisitionState.UNAVAILABLE) {
                // Idempotent: do not re-call Bureau/AA/GST/OCR
                return new ItemExecResult(false, sourceKey, null,
                        "idempotent-skip:" + st.name(),
                        toView(prior, item.getItemKey()),
                        prior);
            }
            if (st == SourceAcquisitionState.FAILED_RETRYABLE && prior.getAttemptNumber() >= 3) {
                return new ItemExecResult(false, sourceKey, null, "retry-exhausted",
                        toView(prior, item.getItemKey()), prior);
            }
        }

        Optional<AcquisitionExecutorPort> port = executorRegistry.find(sourceKey);
        if (port.isEmpty()) {
            return ItemExecResult.skipped("no-executor:" + sourceKey);
        }

        RequirementAcquisitionAttemptEntity attempt = existing.orElseGet(() ->
                RequirementAcquisitionAttemptEntity.builder()
                        .planId(plan.getId())
                        .itemId(item.getId())
                        .applicationId(applicationId)
                        .planVersion(planVersion)
                        .canonicalParameterId(item.getCanonicalParameterId())
                        .sourceKey(sourceKey)
                        .fulfilmentMode(AcquisitionSourceResolver.effectiveMode(item) != null
                                ? AcquisitionSourceResolver.effectiveMode(item).name() : "AUTOMATIC_SOURCE")
                        .executionKey(executionKey)
                        .status(SourceAcquisitionState.QUEUED)
                        .attemptNumber(1)
                        .build());
        if (existing.isPresent() && attempt.getStatus() == SourceAcquisitionState.FAILED_RETRYABLE) {
            attempt.setAttemptNumber(attempt.getAttemptNumber() + 1);
        }
        attempt.setStatus(SourceAcquisitionState.IN_PROGRESS);
        attempt.setStartedAt(Instant.now());
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("whyCalled", "RequirementItem preferred source from W4 RequirementPlan");
        prov.put("requirementItemKey", item.getItemKey());
        prov.put("canonicalParameterId", item.getCanonicalParameterId());
        prov.put("policyRuleRefs", item.getPolicyRuleRefs());
        prov.put("policyDocumentId", plan.getPolicyDocumentId() != null
                ? plan.getPolicyDocumentId().toString() : null);
        prov.put("workflowId", plan.getWorkflowId() != null ? plan.getWorkflowId().toString() : null);
        prov.put("planVersion", planVersion);
        prov.put("orchestratedBy", "W6_WorkflowAcquisitionCoordinator");
        attempt.setProvenance(prov);
        // Persist before call for idempotency under concurrent coordinators
        attempt = attemptRepository.save(attempt);

        AcquisitionDtos.ExecutorOutcome outcome = port.get().execute(
                new AcquisitionExecutorPort.ExecutionContext(
                        applicationId, plan.getId(), planVersion, item, sourceKey, actor, dryRun));

        return new ItemExecResult(true, sourceKey, outcome, null, null, attempt);
    }

    private void applyResult(
            RequirementPlanEntity plan,
            RequirementItemEntity item,
            ItemExecResult r,
            String actor) {

        if (r.attempt() == null) {
            return;
        }
        RequirementAcquisitionAttemptEntity attempt = r.attempt();
        AcquisitionDtos.ExecutorOutcome outcome = r.outcome();

        if (outcome == null) {
            // Idempotent skip — refresh item source from attempt if needed
            if (attempt.getStatus() != null
                    && item.getSourceAcquisitionState() != attempt.getStatus()) {
                transitionService.advanceSource(plan.getId(), item.getId(),
                        attempt.getStatus(), actor, "Idempotent acquisition replay");
            }
            return;
        }

        attempt.setStatus(outcome.status());
        attempt.setProviderRef(outcome.providerRef());
        attempt.setExternalRef(outcome.externalRef());
        attempt.setFailureClass(outcome.failureClass());
        attempt.setFailureReason(outcome.failureReason());
        attempt.setResultSummary(outcome.resultSummary() != null
                ? new LinkedHashMap<>(outcome.resultSummary()) : new LinkedHashMap<>());
        attempt.setFinishedAt(Instant.now());

        // Explicit fallback: AA FAILED_TERMINAL → CUSTOMER_ACTION_REQUIRED (bank statement)
        if (outcome.status() == SourceAcquisitionState.FAILED_TERMINAL
                || outcome.status() == SourceAcquisitionState.UNAVAILABLE) {
            Optional<String> fallback = AcquisitionSourceResolver.explicitFallbackSourceKey(item);
            if (fallback.isPresent()) {
                attempt.setPreviousSourceKey(r.sourceKey());
                attempt.setFallbackSourceKey(fallback.get());
                attempt.setStatus(SourceAcquisitionState.CUSTOMER_ACTION_REQUIRED);
                attempt.getResultSummary().put("fallbackSelected", fallback.get());
                attempt.getResultSummary().put("fallbackAt", Instant.now().toString());
                attempt.getResultSummary().put("failureReason", outcome.failureReason());

                transitionService.advanceSource(plan.getId(), item.getId(),
                        SourceAcquisitionState.CUSTOMER_ACTION_REQUIRED, actor,
                        "Explicit fallback after " + r.sourceKey() + " terminal: " + fallback.get());

                // Open customer document path without fabricating banking facts
                if (item.getSourceHints() == null) {
                    item.setSourceHints(new LinkedHashMap<>());
                }
                item.getSourceHints().put("activeSourceKey", fallback.get());
                item.getSourceHints().put("preferredMode", FulfilmentMode.DOCUMENT_UPLOAD.name());
                item.getSourceHints().put("chosenMode", FulfilmentMode.DOCUMENT_UPLOAD.name());
                item.getSourceHints().put("fallbackFrom", r.sourceKey());
                item.getSourceHints().put("fallbackReason", outcome.failureReason());
                if (!item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
                    List<FulfilmentMode> modes = new ArrayList<>(
                            item.getAllowedFulfilmentModes() != null ? item.getAllowedFulfilmentModes() : List.of());
                    if (!modes.contains(FulfilmentMode.DOCUMENT_UPLOAD)) {
                        modes.add(FulfilmentMode.DOCUMENT_UPLOAD);
                        item.setAllowedFulfilmentModes(modes);
                    }
                }
                if (item.getCustomerFulfilmentState() == CustomerFulfilmentState.NOT_APPLICABLE) {
                    item.setCustomerFulfilmentState(CustomerFulfilmentState.REQUIRED);
                }
                itemRepository.save(item);
                attemptRepository.save(attempt);
                readinessReconciler.reconcile(plan.getId(), item, outcome, actor);
                return;
            }
        }

        attemptRepository.save(attempt);

        SourceAcquisitionState toApply = outcome.status();
        if (toApply == SourceAcquisitionState.NOT_REQUIRED) {
            return;
        }
        transitionService.advanceSource(plan.getId(), item.getId(), toApply, actor,
                outcome.failureReason() != null ? outcome.failureReason() : "W6 source execution");

        // EXTRACTION_FAILED: keep PROVIDED (transition service markExtractionFailed)
        if (outcome.resultSummary() != null
                && "EXTRACTION_FAILED".equals(String.valueOf(outcome.resultSummary().get("documentOutcome")))) {
            transitionService.markExtractionFailed(plan.getId(), item.getId(), actor,
                    outcome.failureReason());
            return;
        }

        RequirementItemEntity updated = itemRepository.findByIdAndPlanId(item.getId(), plan.getId())
                .orElse(item);
        readinessReconciler.reconcile(plan.getId(), updated, outcome, actor);
    }

    @Transactional(readOnly = true)
    public AcquisitionDtos.GateResult gateStatus(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("RequirementPlan not found: " + planId));
        return dataCompletenessGate.evaluate(plan);
    }

    @Transactional(readOnly = true)
    public List<AcquisitionDtos.SourceAttemptView> listAttempts(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("RequirementPlan not found: " + planId));
        Map<UUID, String> keys = new LinkedHashMap<>();
        plan.getItems().forEach(i -> keys.put(i.getId(), i.getItemKey()));
        List<AcquisitionDtos.SourceAttemptView> out = new ArrayList<>();
        for (RequirementAcquisitionAttemptEntity a : attemptRepository.findByPlanIdOrderByCreatedAtAsc(planId)) {
            out.add(toView(a, keys.getOrDefault(a.getItemId(), a.getItemId().toString())));
        }
        return out;
    }

    private static AcquisitionDtos.SourceAttemptView toView(
            RequirementAcquisitionAttemptEntity a, String itemKey) {
        return new AcquisitionDtos.SourceAttemptView(
                a.getId(), a.getItemId(), itemKey, a.getSourceKey(), a.getStatus(),
                a.getFailureClass(), a.getFailureReason(),
                a.getPreviousSourceKey(), a.getFallbackSourceKey(),
                a.getResultSummary(), a.getStartedAt(), a.getFinishedAt());
    }

    private record ItemExecResult(
            boolean executed,
            String sourceKey,
            AcquisitionDtos.ExecutorOutcome outcome,
            String skippedReason,
            AcquisitionDtos.SourceAttemptView attemptView,
            RequirementAcquisitionAttemptEntity attempt
    ) {
        static ItemExecResult skipped(String reason) {
            return new ItemExecResult(false, null, null, reason, null, null);
        }
    }
}
