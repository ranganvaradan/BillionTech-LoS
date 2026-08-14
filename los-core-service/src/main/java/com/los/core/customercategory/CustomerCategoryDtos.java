package com.los.core.customercategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CustomerCategoryDtos {

    private CustomerCategoryDtos() {}

    public record Actor(String userId, String displayName, String role) {
        public String identity() {
            if (displayName != null && !displayName.isBlank()) {
                return displayName.trim();
            }
            if (userId != null && !userId.isBlank()) {
                return userId.trim();
            }
            return "SYSTEM";
        }
    }

    public record PolicySetRequest(
            String code,
            String name,
            String description,
            UUID primaryRuleSetId,
            List<UUID> additionalRuleSetIds,
            UUID scorecardId
    ) {}

    public record PolicySetResponse(
            UUID id,
            String code,
            int versionNo,
            String name,
            String description,
            String status,
            UUID primaryRuleSetId,
            List<UUID> additionalRuleSetIds,
            UUID scorecardId,
            UUID seedSourceRuleSetId,
            String createdBy,
            String updatedBy
    ) {}

    public record CategoryRequest(
            String code,
            String name,
            String description,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            UUID policySetId
    ) {}

    public record CategoryResponse(
            UUID id,
            String code,
            int versionNo,
            String name,
            String description,
            String status,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            UUID policySetId,
            UUID seedSourceRuleSetId,
            String reviewStatus,
            Map<String, Object> inferenceNotes,
            String createdBy,
            String updatedBy,
            List<Map<String, Object>> overlapWarnings
    ) {}

    public record SeedCandidateView(
            String categoryCode,
            String categoryName,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            String amountRange,
            String policySetCode,
            String policySetName,
            List<UUID> ruleSetIds,
            UUID scorecardId,
            String scorecardName,
            UUID sourceRuleSetId,
            String sourceRuleSetName,
            String reviewStatus,
            Map<String, Object> inferenceSource,
            List<Map<String, Object>> overlaps,
            boolean alreadySeeded
    ) {}

    public record SeedPreviewResponse(
            int candidateCount,
            int alreadySeededCount,
            List<SeedCandidateView> candidates,
            List<Map<String, Object>> pairwiseOverlaps
    ) {}

    public record SeedApplyResponse(
            int createdCategories,
            int createdPolicySets,
            int skippedExisting,
            List<SeedCandidateView> created,
            String note
    ) {}
}
