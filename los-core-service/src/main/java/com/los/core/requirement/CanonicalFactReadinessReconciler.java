package com.los.core.requirement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Source success ≠ data ready. Reconciles RequirementItem readiness from usable canonical facts.
 */
@Service
@RequiredArgsConstructor
public class CanonicalFactReadinessReconciler {

    private final CanonicalFactLookupService factLookupService;
    private final RequirementItemTransitionService transitionService;

    @Transactional
    public RequirementItemEntity reconcile(
            UUID planId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome,
            String actor) {

        String param = item.getCanonicalParameterId();
        boolean usable = false;

        if (outcome != null && outcome.factReadiness() != null && param != null
                && outcome.factReadiness().containsKey(param)) {
            usable = Boolean.TRUE.equals(outcome.factReadiness().get(param));
        } else if (param != null && item.getPlan() != null) {
            Optional<ExistingCanonicalFact> fact = factLookupService.find(
                    item.getPlan().getApplicationId(), param, Map.of());
            usable = fact.isPresent() && fact.get().readyForPolicy();
        }

        // Overlay from sourceHints.extractedParameters / forced readiness (tests + partial OCR)
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
            if (outcome != null) {
                prov.put("lastSourceStatus", outcome.status() != null ? outcome.status().name() : null);
                prov.put("providerHttpSuccess", outcome.providerHttpSuccess());
            }
            item.setProvenance(prov);
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.READY_FOR_POLICY, actor,
                    "Usable canonical fact present");
        }

        // Provider succeeded but fact missing → DATA_INSUFFICIENT (never invent 0/PASS)
        if (outcome != null && outcome.status() == SourceAcquisitionState.SUCCEEDED
                && outcome.providerHttpSuccess()) {
            return transitionService.advanceReadiness(
                    planId, item.getId(), DataReadinessState.DATA_INSUFFICIENT, actor,
                    "Source SUCCEEDED but usable canonical fact absent — no default invented");
        }

        if (outcome != null && "EXTRACTION_FAILED".equals(
                String.valueOf(outcome.resultSummary() != null
                        ? outcome.resultSummary().get("documentOutcome") : null))) {
            // Keep PROVIDED; mark extraction failed via readiness FAILED — not REUPLOAD
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
