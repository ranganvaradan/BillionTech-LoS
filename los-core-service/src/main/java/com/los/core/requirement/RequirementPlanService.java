package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequirementPlanService {

    private final RequirementPlanRepository planRepository;
    private final RequirementCompletenessEvaluator completenessEvaluator;
    private final DataRequirementPlanner dataRequirementPlanner;

    @Transactional
    public RequirementDtos.PlanResponse createPlan(RequirementDtos.CreatePlanRequest request) {
        if (request == null || request.applicationId() == null) {
            throw new BusinessRuleException("applicationId is required");
        }
        List<RequirementDtos.ItemSpec> specs = request.items() != null ? request.items() : List.of();

        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .applicationId(request.applicationId())
                .customerCategoryId(request.customerCategoryId())
                .policyDocumentId(request.policyDocumentId())
                .policyApplicabilityId(request.policyApplicabilityId())
                .workflowId(request.workflowId())
                .planVersion(request.planVersion() != null ? request.planVersion() : 1)
                .status(request.status() != null ? request.status() : RequirementPlanStatus.DRAFT)
                .metadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>())
                .build();

        int order = 0;
        for (RequirementDtos.ItemSpec spec : specs) {
            if (spec == null || spec.itemKey() == null || spec.itemKey().isBlank()) {
                throw new BusinessRuleException("Each item requires itemKey");
            }
            if (spec.requirementType() == null || spec.requirementClass() == null) {
                throw new BusinessRuleException("item " + spec.itemKey() + " requires type and class");
            }
            RequirementItemEntity item = RequirementItemEntity.builder()
                    .itemKey(spec.itemKey())
                    .requirementType(spec.requirementType())
                    .requirementClass(spec.requirementClass())
                    .phase(spec.phase())
                    .canonicalParameterId(spec.canonicalParameterId())
                    .businessName(spec.businessName())
                    .required(spec.required() == null || spec.required())
                    .customerFulfilmentState(spec.customerFulfilmentState() != null
                            ? spec.customerFulfilmentState() : CustomerFulfilmentState.REQUIRED)
                    .dataReadinessState(spec.dataReadinessState() != null
                            ? spec.dataReadinessState() : DataReadinessState.NOT_AVAILABLE)
                    .sourceAcquisitionState(spec.sourceAcquisitionState() != null
                            ? spec.sourceAcquisitionState() : SourceAcquisitionState.NOT_STARTED)
                    .allowedFulfilmentModes(spec.allowedFulfilmentModes() != null
                            ? new ArrayList<>(spec.allowedFulfilmentModes()) : new ArrayList<>())
                    .policyRuleRefs(spec.policyRuleRefs() != null
                            ? new ArrayList<>(spec.policyRuleRefs()) : new ArrayList<>())
                    .sourceHints(spec.sourceHints() != null
                            ? new LinkedHashMap<>(spec.sourceHints()) : new LinkedHashMap<>())
                    .provenance(spec.provenance() != null
                            ? new LinkedHashMap<>(spec.provenance()) : new LinkedHashMap<>())
                    .sortOrder(spec.sortOrder() != null ? spec.sortOrder() : order)
                    .documentRef(spec.documentRef())
                    .evidenceRef(spec.evidenceRef())
                    .build();
            plan.addItem(item);
            order++;
        }

        plan.setPlanHash(computePlanHash(plan));
        RequirementPlanEntity saved = planRepository.save(plan);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RequirementDtos.PlanResponse getPlan(UUID id) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessRuleException("Requirement plan not found: " + id));
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<RequirementDtos.PlanResponse> listByApplication(UUID applicationId) {
        return planRepository.findByApplicationIdWithItems(applicationId).stream()
                .sorted(Comparator.comparingInt(RequirementPlanEntity::getPlanVersion).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RequirementDtos.PlanResponse planFromPolicy(RequirementDtos.PlanFromPolicyRequest request) {
        if (request == null || request.applicationId() == null) {
            throw new BusinessRuleException("applicationId is required");
        }
        PlanInputs inputs = new PlanInputs(
                request.applicationId(),
                request.policyDocumentId(),
                request.workflowId(),
                request.customerCategoryId(),
                request.policyApplicabilityId(),
                request.workflowVersion(),
                request.replanFromPlanId(),
                request.inventoryHints() != null ? request.inventoryHints() : Map.of());
        RequirementPlanEntity plan = dataRequirementPlanner.plan(inputs);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public RequirementDtos.PlanningSummary planningSummary(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("Requirement plan not found: " + planId));
        return buildPlanningSummary(plan);
    }

    public RequirementDtos.PlanningSummary buildPlanningSummary(RequirementPlanEntity plan) {
        List<RequirementItemEntity> items = plan.getItems() != null ? plan.getItems() : List.of();
        List<RequirementDtos.CandidateSummary> automatic = new ArrayList<>();
        List<RequirementDtos.CandidateSummary> derivation = new ArrayList<>();
        List<RequirementDtos.CandidateSummary> blocked = new ArrayList<>();
        List<RequirementDtos.CandidateSummary> already = new ArrayList<>();
        List<RequirementDtos.CandidateSummary> explanations = new ArrayList<>();

        Map<String, RequirementDtos.CustomerRequestSummary> customerByKey = new LinkedHashMap<>();

        for (RequirementItemEntity item : items) {
            RequirementDtos.CandidateSummary cand = toCandidate(item);
            explanations.add(cand);
            String blockingReason = item.getSourceHints() != null && item.getSourceHints().get("blockingReason") != null
                    ? String.valueOf(item.getSourceHints().get("blockingReason")) : null;

            if (item.getRequirementClass() == RequirementClass.ALREADY_AVAILABLE
                    && item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY) {
                already.add(cand);
            } else if (item.getRequirementClass() == RequirementClass.AUTO_SOURCE
                    || item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)) {
                automatic.add(cand);
            } else if (item.getRequirementClass() == RequirementClass.DERIVABLE
                    || item.allowsOnly(FulfilmentMode.DERIVATION)) {
                derivation.add(cand);
            }
            if (item.getRequirementClass() == RequirementClass.UNAVAILABLE_BLOCKER
                    || "NO_FULFILMENT_PATH".equals(blockingReason)
                    || "UNRESOLVED_CANONICAL_PARAMETER".equals(blockingReason)) {
                blocked.add(cand);
            }

            if (item.getRequirementClass() == RequirementClass.CUSTOMER_PROVIDED
                    && RequirementCompletenessEvaluator.isCustomerUnresolved(item)) {
                String docGroup = item.getSourceHints() != null
                        && item.getSourceHints().get("pendingDocumentGroup") != null
                        ? String.valueOf(item.getSourceHints().get("pendingDocumentGroup")) : null;
                boolean hasDoc = item.allows(FulfilmentMode.DOCUMENT_UPLOAD) && docGroup != null;
                boolean hasDirect = item.allows(FulfilmentMode.DIRECT_INPUT);
                String mode;
                String requestKey;
                if (hasDoc && hasDirect) {
                    mode = "DOCUMENT_UPLOAD_OR_DIRECT_INPUT";
                    requestKey = "doc-or-direct:" + docGroup + ":" + item.getItemKey();
                    // dual-mode items remain distinct customer requests (golden C)
                    requestKey = "customer:" + item.getItemKey();
                } else if (hasDoc) {
                    mode = FulfilmentMode.DOCUMENT_UPLOAD.name();
                    requestKey = "document:" + docGroup;
                } else if (hasDirect) {
                    mode = FulfilmentMode.DIRECT_INPUT.name();
                    requestKey = "direct:" + item.getItemKey();
                } else {
                    mode = "CUSTOMER";
                    requestKey = "customer:" + item.getItemKey();
                }

                if (hasDoc && !hasDirect) {
                    RequirementDtos.CustomerRequestSummary existing = customerByKey.get(requestKey);
                    List<String> linked = new ArrayList<>();
                    List<UUID> ids = new ArrayList<>();
                    if (existing != null) {
                        linked.addAll(existing.linkedCanonicalParameterIds());
                        ids.addAll(existing.itemIds());
                    }
                    if (item.getCanonicalParameterId() != null) {
                        linked.add(item.getCanonicalParameterId());
                    }
                    ids.add(item.getId());
                    customerByKey.put(requestKey, new RequirementDtos.CustomerRequestSummary(
                            requestKey, mode, docGroup, List.copyOf(new LinkedHashSet<>(linked)),
                            List.copyOf(ids), item.getCustomerFulfilmentState()));
                } else {
                    customerByKey.put(requestKey, new RequirementDtos.CustomerRequestSummary(
                            requestKey, mode, docGroup,
                            item.getCanonicalParameterId() != null
                                    ? List.of(item.getCanonicalParameterId()) : List.of(),
                            item.getId() != null ? List.of(item.getId()) : List.of(),
                            item.getCustomerFulfilmentState()));
                }
            }
        }

        RequirementDtos.CompletenessResult completeness = completenessEvaluator.evaluate(plan);
        Object semanticHash = plan.getMetadata() != null ? plan.getMetadata().get("semanticHash") : null;
        return new RequirementDtos.PlanningSummary(
                plan.getId(),
                plan.getApplicationId(),
                plan.getPolicyDocumentId(),
                plan.getPlanHash(),
                semanticHash != null ? String.valueOf(semanticHash) : null,
                completeness,
                already.size(),
                automatic.size(),
                derivation.size(),
                customerByKey.size(),
                blocked.size(),
                List.copyOf(customerByKey.values()),
                automatic,
                derivation,
                blocked,
                already,
                explanations);
    }

    @SuppressWarnings("unchecked")
    private RequirementDtos.CandidateSummary toCandidate(RequirementItemEntity item) {
        Map<String, Object> explanation = new LinkedHashMap<>();
        if (item.getSourceHints() != null) {
            Object exp = item.getSourceHints().get("explanation");
            if (exp instanceof Map<?, ?> m) {
                m.forEach((k, v) -> explanation.put(String.valueOf(k), v));
            } else {
                explanation.putAll(item.getSourceHints());
            }
        }
        explanation.putIfAbsent("whyRequired", item.getSourceHints() != null
                ? item.getSourceHints().get("whyRequired") : null);
        explanation.putIfAbsent("requiredByPolicyRules", item.getPolicyRuleRefs());
        explanation.putIfAbsent("canonicalParameterId", item.getCanonicalParameterId());
        explanation.putIfAbsent("chosenFulfilmentMode",
                item.getSourceHints() != null ? item.getSourceHints().get("preferredMode") : null);
        explanation.putIfAbsent("whyChosen",
                item.getSourceHints() != null ? item.getSourceHints().get("whyChosen") : null);
        explanation.putIfAbsent("otherAvailableModes",
                item.getAllowedFulfilmentModes() != null
                        ? item.getAllowedFulfilmentModes().stream().map(Enum::name).toList() : List.of());
        explanation.putIfAbsent("currentCustomerFulfilment", item.getCustomerFulfilmentState().name());
        explanation.putIfAbsent("currentDataReadiness", item.getDataReadinessState().name());
        explanation.putIfAbsent("currentSourceState", item.getSourceAcquisitionState().name());
        explanation.putIfAbsent("productionReadiness",
                item.getSourceHints() != null ? item.getSourceHints().get("productionReadiness") : null);
        explanation.putIfAbsent("blockingReason",
                item.getSourceHints() != null ? item.getSourceHints().get("blockingReason") : null);
        explanation.putIfAbsent("documentRequirement",
                item.getSourceHints() != null ? item.getSourceHints().get("documentRequirement") : null);

        FulfilmentMode preferred = null;
        if (item.getSourceHints() != null && item.getSourceHints().get("preferredMode") != null) {
            try {
                preferred = FulfilmentMode.valueOf(String.valueOf(item.getSourceHints().get("preferredMode")));
            } catch (IllegalArgumentException ignored) {
                preferred = item.getFulfilmentModeUsed();
            }
        }
        String blocking = item.getSourceHints() != null
                ? (item.getSourceHints().get("blockingReason") != null
                ? String.valueOf(item.getSourceHints().get("blockingReason")) : null)
                : null;
        String prod = item.getSourceHints() != null && item.getSourceHints().get("productionReadiness") != null
                ? String.valueOf(item.getSourceHints().get("productionReadiness")) : null;

        return new RequirementDtos.CandidateSummary(
                item.getId(),
                item.getItemKey(),
                item.getCanonicalParameterId(),
                item.getRequirementClass(),
                preferred,
                item.getAllowedFulfilmentModes(),
                prod,
                blocking,
                explanation);
    }

    @Transactional(readOnly = true)
    public RequirementDtos.PlanSummary summarize(UUID planId) {
        RequirementPlanEntity plan = planRepository.findByIdWithItems(planId)
                .orElseThrow(() -> new BusinessRuleException("Requirement plan not found: " + planId));
        List<RequirementItemEntity> items = plan.getItems() != null ? plan.getItems() : List.of();

        Map<String, Long> fulfilment = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getCustomerFulfilmentState().name(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        Map<String, Long> readiness = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getDataReadinessState().name(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        Map<String, Long> source = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSourceAcquisitionState().name(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        RequirementDtos.CompletenessResult completeness = completenessEvaluator.evaluate(plan);
        return new RequirementDtos.PlanSummary(
                plan.getId(),
                plan.getApplicationId(),
                plan.getStatus(),
                fulfilment,
                readiness,
                source,
                items.size(),
                completeness.customerUnresolvedCount(),
                completeness);
    }

    public RequirementDtos.PlanResponse toResponse(RequirementPlanEntity plan) {
        List<RequirementDtos.ItemResponse> items = (plan.getItems() != null ? plan.getItems() : List.<RequirementItemEntity>of())
                .stream()
                .sorted(Comparator.comparingInt(RequirementItemEntity::getSortOrder)
                        .thenComparing(RequirementItemEntity::getItemKey))
                .map(this::toItemResponse)
                .toList();
        return new RequirementDtos.PlanResponse(
                plan.getId(),
                plan.getApplicationId(),
                plan.getCustomerCategoryId(),
                plan.getPolicyDocumentId(),
                plan.getPolicyApplicabilityId(),
                plan.getWorkflowId(),
                plan.getPlanVersion(),
                plan.getStatus(),
                plan.getPlanHash(),
                plan.getMetadata(),
                items,
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    public RequirementDtos.ItemResponse toItemResponse(RequirementItemEntity item) {
        return new RequirementDtos.ItemResponse(
                item.getId(),
                item.getItemKey(),
                item.getRequirementType(),
                item.getRequirementClass(),
                item.getPhase(),
                item.getCanonicalParameterId(),
                item.getBusinessName(),
                item.isRequired(),
                item.getCustomerFulfilmentState(),
                item.getDataReadinessState(),
                item.getSourceAcquisitionState(),
                item.getAllowedFulfilmentModes(),
                item.getFulfilmentModeUsed(),
                item.getEvidenceRef(),
                item.getDocumentRef(),
                item.getPolicyRuleRefs(),
                item.getSourceHints(),
                item.getProvenance(),
                item.getSortOrder(),
                item.getProvidedAt());
    }

    static String computePlanHash(RequirementPlanEntity plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.getApplicationId()).append('|').append(plan.getPlanVersion()).append('|');
        List<RequirementItemEntity> items = new ArrayList<>(plan.getItems() != null ? plan.getItems() : List.of());
        items.sort(Comparator.comparingInt(RequirementItemEntity::getSortOrder)
                .thenComparing(RequirementItemEntity::getItemKey));
        for (RequirementItemEntity i : items) {
            sb.append(i.getItemKey()).append(':')
                    .append(i.getRequirementType()).append(':')
                    .append(i.getRequirementClass()).append(':')
                    .append(i.getCanonicalParameterId()).append(';');
        }
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
