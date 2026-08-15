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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequirementPlanService {

    private final RequirementPlanRepository planRepository;
    private final RequirementCompletenessEvaluator completenessEvaluator;

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
