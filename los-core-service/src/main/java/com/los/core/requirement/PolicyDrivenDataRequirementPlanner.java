package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * W4 — Policy Parameter Inventory → RequirementPlan.
 * Plans requirements only. Does not execute sources, OCR, KYC, Policy, or Scorecard.
 */
@Component
@RequiredArgsConstructor
public class PolicyDrivenDataRequirementPlanner implements DataRequirementPlanner {

    private final PolicyRequirementInventoryBuilder inventoryBuilder;
    private final FulfilmentPathResolver fulfilmentPathResolver;
    private final CanonicalFactLookupService factLookupService;
    private final RequirementPlanRepository planRepository;
    private final RequirementCompletenessEvaluator completenessEvaluator;

    @Override
    @Transactional
    public RequirementPlanEntity plan(PlanInputs inputs) {
        if (inputs == null || inputs.applicationId() == null) {
            throw new BusinessRuleException("applicationId is required for planning");
        }
        if (inputs.policyDocumentId() == null
                && !(inputs.inventoryHints() != null && inputs.inventoryHints().containsKey("policyInventory"))) {
            throw new BusinessRuleException("policyDocumentId is required (or policyInventory hint for controlled tests)");
        }

        Map<String, Object> hints = new LinkedHashMap<>(
                inputs.inventoryHints() != null ? inputs.inventoryHints() : Map.of());

        Map<String, Object> rawInventory = inventoryBuilder.buildRaw(inputs.policyDocumentId(), hints);
        List<PolicyParameterRequirement> requirements = inventoryBuilder.toRequirements(rawInventory);

        List<PlannedItem> planned = new ArrayList<>();
        int order = 0;
        for (PolicyParameterRequirement req : requirements) {
            Optional<ExistingCanonicalFact> fact = factLookupService.find(
                    inputs.applicationId(), req.canonicalParameterId(), hints);
            FulfilmentPathResolver.Resolution resolution = fulfilmentPathResolver.resolve(req, fact, hints);
            planned.add(new PlannedItem(req, resolution, order++));
        }

        // Document grouping: one pendingDocumentGroup shared across parameter items (no duplicate uploads)
        groupDocuments(planned);

        String semanticHash = computeSemanticHash(
                inputs.applicationId(),
                inputs.policyDocumentId(),
                inputs.workflowId(),
                inputs.workflowVersion(),
                rawInventory,
                planned);

        // Idempotency: return existing semantically identical ACTIVE/DRAFT plan
        if (!Boolean.TRUE.equals(hints.get("forceNewPlan")) && inputs.replanFromPlanId() == null) {
            List<RequirementPlanEntity> existing = planRepository.findByApplicationIdWithItems(inputs.applicationId());
            for (RequirementPlanEntity p : existing) {
                Object prev = p.getMetadata() != null ? p.getMetadata().get("semanticHash") : null;
                if (semanticHash.equals(String.valueOf(prev))
                        && (p.getStatus() == RequirementPlanStatus.ACTIVE
                        || p.getStatus() == RequirementPlanStatus.DRAFT)) {
                    return p;
                }
            }
        }

        RequirementPlanEntity prior = null;
        if (inputs.replanFromPlanId() != null) {
            prior = planRepository.findByIdWithItems(inputs.replanFromPlanId()).orElse(null);
        } else if (Boolean.TRUE.equals(hints.get("replanLatest"))) {
            prior = planRepository.findByApplicationIdWithItems(inputs.applicationId()).stream()
                    .max(Comparator.comparingInt(RequirementPlanEntity::getPlanVersion))
                    .orElse(null);
        }

        int nextVersion = 1;
        if (prior != null) {
            nextVersion = prior.getPlanVersion() + 1;
            if (prior.getStatus() == RequirementPlanStatus.ACTIVE) {
                prior.setStatus(RequirementPlanStatus.SUPERSEDED);
                planRepository.save(prior);
            }
        } else {
            nextVersion = planRepository.findByApplicationIdOrderByPlanVersionDesc(inputs.applicationId()).stream()
                    .map(RequirementPlanEntity::getPlanVersion)
                    .findFirst()
                    .orElse(0) + 1;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("w4", true);
        metadata.put("planner", "PolicyDrivenDataRequirementPlanner");
        metadata.put("derivedFrom", rawInventory.getOrDefault("derivedFrom", "PERSISTED_POLICY_GRAPH"));
        metadata.put("graphId", rawInventory.get("graphId"));
        metadata.put("graphHash", rawInventory.get("graphHash"));
        metadata.put("inventoryState", rawInventory.get("inventoryState"));
        metadata.put("unresolvedOperandCount", rawInventory.get("unresolvedOperandCount"));
        metadata.put("semanticHash", semanticHash);
        metadata.put("workflowVersion", inputs.workflowVersion());
        metadata.put("noSourceExecution", true);
        if (rawInventory.get("scorecardParametersRejectedOutsidePolicy") != null) {
            metadata.put("scorecardParametersRejectedOutsidePolicy",
                    rawInventory.get("scorecardParametersRejectedOutsidePolicy"));
        }

        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .applicationId(inputs.applicationId())
                .customerCategoryId(inputs.customerCategoryId())
                .policyDocumentId(inputs.policyDocumentId())
                .policyApplicabilityId(inputs.policyApplicabilityId())
                .workflowId(inputs.workflowId())
                .planVersion(nextVersion)
                .status(RequirementPlanStatus.ACTIVE)
                .metadata(metadata)
                .build();

        for (PlannedItem pi : planned) {
            RequirementItemEntity item = toEntity(pi);
            reconcileFromPrior(item, prior);
            plan.addItem(item);
        }

        plan.setPlanHash(RequirementPlanService.computePlanHash(plan));
        RequirementDtos.CompletenessResult completeness = completenessEvaluator.evaluate(plan);
        Map<String, Object> completenessMap = new LinkedHashMap<>();
        completenessMap.put("status", completeness.status().name());
        completenessMap.put("reasons", completeness.reasons());
        completenessMap.put("customerUnresolvedCount", completeness.customerUnresolvedCount());
        completenessMap.put("requiredNotReadyCount", completeness.requiredNotReadyCount());
        completenessMap.put("blockedCount", completeness.blockedCount());
        metadata.put("completenessPreview", completenessMap);
        plan.setMetadata(metadata);
        return planRepository.save(plan);
    }

    private void groupDocuments(List<PlannedItem> planned) {
        Map<String, List<PlannedItem>> byGroup = new LinkedHashMap<>();
        for (PlannedItem pi : planned) {
            String g = pi.resolution.documentGroup();
            if (g == null || g.isBlank()) continue;
            if (pi.resolution.preferredMode() != FulfilmentMode.DOCUMENT_UPLOAD
                    && !pi.resolution.allowedModes().contains(FulfilmentMode.DOCUMENT_UPLOAD)) {
                continue;
            }
            byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(pi);
        }
        for (Map.Entry<String, List<PlannedItem>> e : byGroup.entrySet()) {
            List<String> linked = e.getValue().stream()
                    .map(p -> p.req.canonicalParameterId())
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            for (PlannedItem pi : e.getValue()) {
                pi.resolution.sourceHints().put("pendingDocumentGroup", e.getKey());
                pi.resolution.sourceHints().put("documentRequirement", e.getKey());
                pi.resolution.sourceHints().put("linkedCanonicalParameterIds", linked);
                pi.resolution.explanation().put("documentRequirement", e.getKey());
                pi.resolution.explanation().put("linkedCanonicalParameterIds", linked);
                pi.resolution.explanation().put("documentGroupSize", linked.size());
            }
        }
    }

    private RequirementItemEntity toEntity(PlannedItem pi) {
        FulfilmentPathResolver.Resolution r = pi.resolution;
        PolicyParameterRequirement req = pi.req;
        String itemKey = req.logicalKey();
        Map<String, Object> hints = new LinkedHashMap<>(r.sourceHints());
        hints.put("whyRequired", r.whyRequired());
        hints.put("explanation", r.explanation());
        if (r.alternativeSources() != null && !r.alternativeSources().isEmpty()) {
            hints.put("alternativeSources", r.alternativeSources());
        }
        return RequirementItemEntity.builder()
                .itemKey(itemKey)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(r.requirementClass())
                .phase(RequirementPhase.PRE_UNDERWRITING_DATA)
                .canonicalParameterId(req.canonicalParameterId())
                .businessName(req.businessName() != null ? req.businessName() : req.canonicalParameterId())
                .required(req.required() || req.unresolved())
                .customerFulfilmentState(r.customerFulfilmentState())
                .dataReadinessState(r.dataReadinessState())
                .sourceAcquisitionState(r.sourceAcquisitionState())
                .allowedFulfilmentModes(new ArrayList<>(r.allowedModes()))
                .policyRuleRefs(new ArrayList<>(req.ruleReferences()))
                .sourceHints(hints)
                .provenance(new LinkedHashMap<>(r.provenance()))
                .sortOrder(pi.sortOrder)
                .build();
    }

    /**
     * Controlled replan: preserve PROVIDED document refs / fulfilment; refresh readiness from new resolution
     * when prior was already PROVIDED or READY.
     */
    private void reconcileFromPrior(RequirementItemEntity item, RequirementPlanEntity prior) {
        if (prior == null || prior.getItems() == null) {
            return;
        }
        String key = item.getCanonicalParameterId() != null ? item.getCanonicalParameterId() : item.getItemKey();
        for (RequirementItemEntity old : prior.getItems()) {
            String oldKey = old.getCanonicalParameterId() != null ? old.getCanonicalParameterId() : old.getItemKey();
            if (!key.equals(oldKey)) {
                continue;
            }
            // Preserve customer PROVIDED + documentRef so we do not re-request same upload
            if (old.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                    || old.getCustomerFulfilmentState() == CustomerFulfilmentState.WAIVED) {
                item.setCustomerFulfilmentState(old.getCustomerFulfilmentState());
                item.setDocumentRef(old.getDocumentRef());
                item.setEvidenceRef(old.getEvidenceRef());
                item.setFulfilmentModeUsed(old.getFulfilmentModeUsed());
                item.setProvidedAt(old.getProvidedAt());
                // Keep readiness from prior when still in-flight or already progressed
                if (old.getDataReadinessState() != null
                        && old.getDataReadinessState() != DataReadinessState.NOT_AVAILABLE) {
                    item.setDataReadinessState(old.getDataReadinessState());
                }
            }
            if (old.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY) {
                item.setDataReadinessState(DataReadinessState.READY_FOR_POLICY);
                if (old.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                        || old.getCustomerFulfilmentState() == CustomerFulfilmentState.WAIVED) {
                    item.setCustomerFulfilmentState(old.getCustomerFulfilmentState());
                    item.setRequirementClass(RequirementClass.ALREADY_AVAILABLE);
                } else {
                    item.setCustomerFulfilmentState(CustomerFulfilmentState.NOT_APPLICABLE);
                    item.setRequirementClass(RequirementClass.ALREADY_AVAILABLE);
                }
            }
            // Merge provenance
            Map<String, Object> prov = new LinkedHashMap<>(item.getProvenance() != null ? item.getProvenance() : Map.of());
            prov.put("replannedFromItemId", old.getId() != null ? old.getId().toString() : null);
            prov.put("replannedFromPlanId", prior.getId() != null ? prior.getId().toString() : null);
            if (old.getProvenance() != null) {
                prov.put("priorProvenance", old.getProvenance());
            }
            item.setProvenance(prov);
            break;
        }
    }

    static String computeSemanticHash(
            UUID applicationId,
            UUID policyDocumentId,
            UUID workflowId,
            String workflowVersion,
            Map<String, Object> inventory,
            List<PlannedItem> planned) {
        StringBuilder sb = new StringBuilder();
        sb.append(applicationId).append('|')
                .append(policyDocumentId).append('|')
                .append(workflowId).append('|')
                .append(workflowVersion).append('|')
                .append(inventory.get("graphHash")).append('|');
        List<PlannedItem> sorted = new ArrayList<>(planned);
        sorted.sort(Comparator.comparing(p -> p.req.logicalKey()));
        for (PlannedItem p : sorted) {
            FulfilmentPathResolver.Resolution r = p.resolution;
            sb.append(p.req.logicalKey()).append(':')
                    .append(r.requirementClass()).append(':')
                    .append(r.preferredMode()).append(':')
                    .append(r.customerFulfilmentState()).append(':')
                    .append(r.dataReadinessState()).append(':')
                    .append(r.documentGroup()).append(';');
        }
        // Include existing-fact readiness keys so fact changes invalidate hash
        Object facts = inventory.get("parameters"); // facts affect planned states already
        sb.append(facts != null ? planned.size() : 0);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    record PlannedItem(PolicyParameterRequirement req, FulfilmentPathResolver.Resolution resolution, int sortOrder) {}
}
