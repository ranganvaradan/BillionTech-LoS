package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Source success ≠ data ready. For GACAT canonical IDs, readiness is decided only by
 * {@link com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService}
 * (VALUE_AVAILABLE). Adapter {@code factReadiness} booleans are diagnostic only for GACAT paths.
 */
@Service
@RequiredArgsConstructor
public class CanonicalFactReadinessReconciler {

    private final CanonicalFactLookupService factLookupService;
    private final RequirementItemTransitionService transitionService;
    private final W6EvaluationContextFactory evaluationContextFactory;
    private final W6CanonicalParameterExecutor parameterExecutor;

    @Transactional
    public RequirementItemEntity reconcile(
            UUID planId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome,
            String actor) {

        String param = item.getCanonicalParameterId();
        Optional<String> gacatId = W6EvaluationContextFactory.resolveGacatId(param);

        if (gacatId.isPresent() && shouldAttemptSpine(outcome, item)) {
            return reconcileViaSpine(planId, item, outcome, actor, gacatId.get());
        }

        return reconcileLegacyFixture(planId, item, outcome, actor, param);
    }

    private boolean shouldAttemptSpine(AcquisitionDtos.ExecutorOutcome outcome, RequirementItemEntity item) {
        if (outcome != null && outcome.status() == SourceAcquisitionState.SUCCEEDED) {
            return true;
        }
        if (outcome != null && outcome.status() != null && outcome.status().isInFlight()) {
            return false;
        }
        // Re-evaluate when derivation/hints already carry inputs (deps acquired earlier)
        if (item.getSourceHints() != null) {
            if (Boolean.TRUE.equals(item.getSourceHints().get("derivationReady"))
                    || item.getSourceHints().get("derivedValue") != null
                    || item.getSourceHints().get("inputs") instanceof Map<?, ?>) {
                return true;
            }
        }
        return outcome == null;
    }

    private RequirementItemEntity reconcileViaSpine(
            UUID planId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome,
            String actor,
            String canonicalId) {

        UUID applicationId = item.getPlan() != null ? item.getPlan().getApplicationId() : null;
        EvaluationContext ctx = evaluationContextFactory.build(applicationId, item, outcome);
        W6CanonicalParameterExecutor.ParameterExecutionView view =
                parameterExecutor.execute(canonicalId, ctx);

        Map<String, Object> prov = new LinkedHashMap<>(
                item.getProvenance() != null ? item.getProvenance() : Map.of());
        prov.put("readinessReconciledBy", "W6_CanonicalFactReadinessReconciler");
        prov.put("executionAuthority", "CanonicalParameterExecutionService");
        prov.put("canonicalParameterId", canonicalId);
        prov.put("spineExecutionStatus", view.status() == null ? null : view.status().name());
        prov.put("spineProducerId", view.producerId());
        prov.put("spineProducerType", view.producerType());
        prov.put("spineExactProducerPath", view.exactProducerPath());
        prov.put("spineProvenance", view.provenance());
        prov.put("spineValue", view.value());
        prov.put("requirementSatisfied", view.requirementSatisfied());
        if (outcome != null) {
            prov.put("lastSourceStatus", outcome.status() != null ? outcome.status().name() : null);
            prov.put("providerHttpSuccess", outcome.providerHttpSuccess());
            prov.put("sourceAcquired", outcome.status() == SourceAcquisitionState.SUCCEEDED
                    && outcome.providerHttpSuccess());
        }
        if (!view.requirementSatisfied()
                && outcome != null
                && outcome.status() == SourceAcquisitionState.SUCCEEDED
                && outcome.providerHttpSuccess()) {
            prov.put("acquisitionVsParameter", "SOURCE_ACQUIRED_BUT_PARAMETER_NOT_RESOLVED");
        }
        item.setProvenance(prov);

        if (view.requirementSatisfied()) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.READY_FOR_POLICY, actor,
                    "Spine VALUE_AVAILABLE for " + canonicalId);
        }

        if (outcome != null && outcome.status() != null && outcome.status().isInFlight()) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.PROCESSING, actor,
                    "Acquisition in progress");
        }

        if (outcome != null && "EXTRACTION_FAILED".equals(
                String.valueOf(outcome.resultSummary() != null
                        ? outcome.resultSummary().get("documentOutcome") : null))) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.FAILED, actor,
                    "EXTRACTION_FAILED — customer re-upload not automatic");
        }

        String reason = classifyUnresolved(view);
        return transitionService.advanceReadiness(
                planId, item.getId(), DataReadinessState.DATA_INSUFFICIENT, actor, reason);
    }

    private static String classifyUnresolved(W6CanonicalParameterExecutor.ParameterExecutionView view) {
        ExecutionStatus st = view.status();
        if (st == ExecutionStatus.NOT_EXECUTABLE || st == ExecutionStatus.CALCULATION_NOT_DEFINED) {
            return "ACQUIRED_BUT_NOT_EXECUTABLE: " + (view.reason() != null ? view.reason() : st.name());
        }
        if (st == ExecutionStatus.DEPENDENCY_NOT_AVAILABLE) {
            return "DEPENDENCY_MISSING: " + (view.reason() != null ? view.reason() : st.name());
        }
        if (st == ExecutionStatus.INPUT_REQUIRED) {
            return "INPUT_REQUIRED: " + (view.reason() != null ? view.reason() : st.name());
        }
        if (st == ExecutionStatus.DATA_NOT_AVAILABLE) {
            return "SOURCE_ACQUIRED_BUT_PARAMETER_NOT_RESOLVED: " + (view.reason() != null ? view.reason() : st.name());
        }
        if (st == ExecutionStatus.ERROR) {
            return "FAILED: " + (view.reason() != null ? view.reason() : st.name());
        }
        return "Required parameter not resolved via spine: "
                + (st != null ? st.name() : "unknown")
                + (view.reason() != null ? " — " + view.reason() : "");
    }

    private RequirementItemEntity reconcileLegacyFixture(
            UUID planId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome,
            String actor,
            String param) {

        boolean usable = false;

        if (outcome != null && outcome.factReadiness() != null && param != null
                && outcome.factReadiness().containsKey(param)) {
            usable = Boolean.TRUE.equals(outcome.factReadiness().get(param));
        } else if (param != null && item.getPlan() != null) {
            Optional<ExistingCanonicalFact> fact = factLookupService.find(
                    item.getPlan().getApplicationId(), param, Map.of());
            usable = fact.isPresent() && fact.get().readyForPolicy();
        }

        if (!usable && item.getSourceHints() != null) {
            Object extracted = item.getSourceHints().get("extractedParameters");
            if (extracted instanceof Map<?, ?> m && param != null && m.get(param) != null) {
                usable = true;
            }
            if (Boolean.TRUE.equals(item.getSourceHints().get("canonicalFactReady"))) {
                usable = true;
            }
        }

        if (usable) {
            Map<String, Object> prov = new LinkedHashMap<>(
                    item.getProvenance() != null ? item.getProvenance() : Map.of());
            prov.put("readinessReconciledBy", "W6_CanonicalFactReadinessReconciler");
            prov.put("legacyFixturePath", true);
            if (outcome != null) {
                prov.put("lastSourceStatus", outcome.status() != null ? outcome.status().name() : null);
                prov.put("providerHttpSuccess", outcome.providerHttpSuccess());
            }
            item.setProvenance(prov);
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.READY_FOR_POLICY, actor,
                    "Usable canonical fact present");
        }

        if (outcome != null && outcome.status() == SourceAcquisitionState.SUCCEEDED
                && outcome.providerHttpSuccess()) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.DATA_INSUFFICIENT, actor,
                    "Source SUCCEEDED but usable canonical fact absent — no default invented");
        }

        if (outcome != null && "EXTRACTION_FAILED".equals(
                String.valueOf(outcome.resultSummary() != null
                        ? outcome.resultSummary().get("documentOutcome") : null))) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.FAILED, actor,
                    "EXTRACTION_FAILED — customer re-upload not automatic");
        }

        if (outcome != null && outcome.status() != null && outcome.status().isInFlight()) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.PROCESSING, actor,
                    "Acquisition in progress");
        }

        return item;
    }
}
