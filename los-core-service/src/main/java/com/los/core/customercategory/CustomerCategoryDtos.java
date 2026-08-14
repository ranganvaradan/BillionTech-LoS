package com.los.core.customercategory;

import java.math.BigDecimal;
import java.time.Instant;
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

    public record LifecycleActionRequest(String remarks, String reason) {}

    public record PolicySetRequest(
            String code,
            String name,
            String description,
            UUID primaryRuleSetId,
            List<UUID> additionalRuleSetIds,
            UUID scorecardId,
            Instant effectiveFrom,
            Instant effectiveUntil,
            String reasonForChange
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
            Instant effectiveFrom,
            Instant effectiveUntil,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            String submittedBy,
            Instant submittedAt,
            String approvedBy,
            Instant approvedAt,
            String activatedBy,
            Instant activatedAt,
            String retiredBy,
            Instant retiredAt,
            String retirementReason,
            String reasonForChange,
            UUID replacesPolicySetId,
            int usedByCategoryCount,
            List<String> allowedActions,
            List<Map<String, Object>> history
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
            /** Transitional — optional; not required for new Categories. */
            UUID policySetId,
            Instant effectiveFrom,
            Instant effectiveUntil,
            String reasonForChange,
            /** Canonical alias for {@code borrowerType}; optional during compatibility. */
            String entityType,
            /** Canonical alias for {@code intakeSegment}; optional during compatibility. */
            String customerRole,
            /** Principal Policy Studio catalogue id (exact Policy Version). */
            UUID policyApplicabilityId,
            /** Optional — must match catalogue row when supplied. */
            UUID policyDocumentId,
            /** Optional — must match catalogue version label when supplied. */
            String policyVersionLabel
    ) {
        /** Transitional 12-arg constructor — aliases / Policy bind null. */
        public CategoryRequest(
                String code,
                String name,
                String description,
                String borrowerType,
                String loanProduct,
                String intakeSegment,
                BigDecimal minAmount,
                BigDecimal maxAmount,
                UUID policySetId,
                Instant effectiveFrom,
                Instant effectiveUntil,
                String reasonForChange) {
            this(code, name, description, borrowerType, loanProduct, intakeSegment,
                    minAmount, maxAmount, policySetId, effectiveFrom, effectiveUntil, reasonForChange,
                    null, null, null, null, null);
        }

        /** STEP-1 14-arg constructor — Policy bind null. */
        public CategoryRequest(
                String code,
                String name,
                String description,
                String borrowerType,
                String loanProduct,
                String intakeSegment,
                BigDecimal minAmount,
                BigDecimal maxAmount,
                UUID policySetId,
                Instant effectiveFrom,
                Instant effectiveUntil,
                String reasonForChange,
                String entityType,
                String customerRole) {
            this(code, name, description, borrowerType, loanProduct, intakeSegment,
                    minAmount, maxAmount, policySetId, effectiveFrom, effectiveUntil, reasonForChange,
                    entityType, customerRole, null, null, null);
        }
    }

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
            /** Transitional internal package id — may be null. */
            UUID policySetId,
            UUID seedSourceRuleSetId,
            String reviewStatus,
            Map<String, Object> inferenceNotes,
            Instant effectiveFrom,
            Instant effectiveUntil,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            String submittedBy,
            Instant submittedAt,
            String approvedBy,
            Instant approvedAt,
            String activatedBy,
            Instant activatedAt,
            String retiredBy,
            Instant retiredAt,
            String retirementReason,
            String reasonForChange,
            UUID replacesCategoryId,
            List<Map<String, Object>> overlapWarnings,
            List<String> allowedActions,
            List<Map<String, Object>> history,
            /** Canonical alias — same storage value as {@code borrowerType}. */
            String entityType,
            /** Canonical alias — same storage value as {@code intakeSegment}. */
            String customerRole,
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel,
            UUID policyLineageId,
            String policyName,
            String policyBusinessStatus,
            /** LINKED | POLICY_LINKAGE_REQUIRED */
            String policyLinkageStatus
    ) {}

    /**
     * Policy Studio catalogue picker row for Category admin.
     * Incompatible Policies are still returned with {@code compatible=false} + notes.
     */
    public record EligiblePolicyView(
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyName,
            String policyVersionLabel,
            UUID enginePolicyVersionId,
            String businessStatus,
            String effectiveFrom,
            String effectiveUntil,
            List<String> products,
            List<String> entityTypes,
            String customerRoleApplicability,
            BigDecimal minLoanAmount,
            BigDecimal maxLoanAmount,
            String dataReadinessStatus,
            String testsStatus,
            String simulationReviewStatus,
            String shadowEligibility,
            Boolean shadowRoutable,
            String productionAuthority,
            Boolean allowCanonicalAuthority,
            boolean compatibleWithCategory,
            List<String> compatibilityNotes
    ) {}

    public record ActivationCheck(String code, String label, boolean ok, String detail) {}

    public record ActivationReadinessResponse(
            UUID id,
            String objectType,
            String status,
            boolean ready,
            List<ActivationCheck> checks,
            List<Map<String, Object>> overlapWarnings
    ) {}

    public record EligibleRuleSetView(
            UUID id,
            String name,
            String borrowerType,
            String loanProduct,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int priority,
            boolean active
    ) {}

    public record EligibleScorecardView(
            UUID id,
            String name,
            String borrowerType,
            String loanProduct,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int priority,
            String status,
            boolean active
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
