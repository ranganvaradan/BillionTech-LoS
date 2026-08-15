package com.los.core.requirement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request/response records for W3 Requirement Plan admin APIs.
 */
public final class RequirementDtos {

    private RequirementDtos() {}

    public record ItemSpec(
            String itemKey,
            RequirementType requirementType,
            RequirementClass requirementClass,
            RequirementPhase phase,
            String canonicalParameterId,
            String businessName,
            Boolean required,
            CustomerFulfilmentState customerFulfilmentState,
            DataReadinessState dataReadinessState,
            SourceAcquisitionState sourceAcquisitionState,
            List<FulfilmentMode> allowedFulfilmentModes,
            List<String> policyRuleRefs,
            Map<String, Object> sourceHints,
            Map<String, Object> provenance,
            Integer sortOrder,
            String documentRef,
            String evidenceRef
    ) {}

    public record CreatePlanRequest(
            UUID applicationId,
            UUID customerCategoryId,
            UUID policyDocumentId,
            UUID policyApplicabilityId,
            UUID workflowId,
            Integer planVersion,
            RequirementPlanStatus status,
            Map<String, Object> metadata,
            List<ItemSpec> items
    ) {}

    public record ItemResponse(
            UUID id,
            String itemKey,
            RequirementType requirementType,
            RequirementClass requirementClass,
            RequirementPhase phase,
            String canonicalParameterId,
            String businessName,
            boolean required,
            CustomerFulfilmentState customerFulfilmentState,
            DataReadinessState dataReadinessState,
            SourceAcquisitionState sourceAcquisitionState,
            List<FulfilmentMode> allowedFulfilmentModes,
            FulfilmentMode fulfilmentModeUsed,
            String evidenceRef,
            String documentRef,
            List<String> policyRuleRefs,
            Map<String, Object> sourceHints,
            Map<String, Object> provenance,
            int sortOrder,
            java.time.Instant providedAt
    ) {}

    public record PlanResponse(
            UUID id,
            UUID applicationId,
            UUID customerCategoryId,
            UUID policyDocumentId,
            UUID policyApplicabilityId,
            UUID workflowId,
            int planVersion,
            RequirementPlanStatus status,
            String planHash,
            Map<String, Object> metadata,
            List<ItemResponse> items,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public record CompletenessResult(
            CompletenessStatus status,
            List<String> reasons,
            int customerUnresolvedCount,
            int requiredNotReadyCount,
            int blockedCount
    ) {}

    public record PlanSummary(
            UUID planId,
            UUID applicationId,
            RequirementPlanStatus planStatus,
            Map<String, Long> fulfilmentCounts,
            Map<String, Long> readinessCounts,
            Map<String, Long> sourceCounts,
            int itemCount,
            int customerUnresolvedCount,
            CompletenessResult completeness
    ) {}

    public record DocumentUploadedRequest(
            String documentRef,
            List<UUID> itemIds,
            String actor,
            String reason
    ) {}

    public record DirectInputRequest(
            String valueRef,
            Boolean verified,
            String actor,
            String reason
    ) {}

    public record StateAdvanceRequest(
            String state,
            String reason,
            String actor
    ) {}

    /** W4 — POST plan-from-policy body. */
    public record PlanFromPolicyRequest(
            UUID applicationId,
            UUID policyDocumentId,
            UUID workflowId,
            UUID customerCategoryId,
            UUID policyApplicabilityId,
            String workflowVersion,
            UUID replanFromPlanId,
            Map<String, Object> inventoryHints
    ) {}

    public record CustomerRequestSummary(
            String requestKey,
            String mode,
            String documentGroup,
            List<String> linkedCanonicalParameterIds,
            List<UUID> itemIds,
            CustomerFulfilmentState customerFulfilment
    ) {}

    public record CandidateSummary(
            UUID itemId,
            String itemKey,
            String canonicalParameterId,
            RequirementClass requirementClass,
            FulfilmentMode preferredMode,
            List<FulfilmentMode> allowedModes,
            String productionReadiness,
            String blockingReason,
            Map<String, Object> explanation
    ) {}

    public record PlanningSummary(
            UUID planId,
            UUID applicationId,
            UUID policyDocumentId,
            String planHash,
            String semanticHash,
            CompletenessResult completeness,
            int alreadySatisfiedCount,
            int automaticAcquisitionCandidateCount,
            int derivationCandidateCount,
            int customerRequestCount,
            int blockedCount,
            List<CustomerRequestSummary> customerRequests,
            List<CandidateSummary> automaticSourceCandidates,
            List<CandidateSummary> derivationCandidates,
            List<CandidateSummary> blockedRequirements,
            List<CandidateSummary> alreadySatisfied,
            List<CandidateSummary> explanations
    ) {}
}
