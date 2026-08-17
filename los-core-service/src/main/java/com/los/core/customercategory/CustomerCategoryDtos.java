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

    /**
     * Governance action body. Optional Policy bind fields let Submit persist a pending
     * Policy Version selection atomically before the DRAFT → IN_REVIEW transition.
     */
    public record LifecycleActionRequest(
            String remarks,
            String reason,
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel
    ) {
        public LifecycleActionRequest(String remarks, String reason) {
            this(remarks, reason, null, null, null);
        }
    }

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
            String policyVersionLabel,
            /** W2 — exact Workflow Version id ({@code workflow_configs.id}). Independent of Policy. */
            UUID workflowId,
            /** Optional — must match {@code workflow_configs.version} when supplied. */
            Integer workflowVersion
    ) {
        /** Transitional 12-arg constructor — aliases / Policy / Workflow bind null. */
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
                    null, null, null, null, null, null, null);
        }

        /** STEP-1 14-arg constructor — Policy / Workflow bind null. */
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
                    entityType, customerRole, null, null, null, null, null);
        }

        /** STEP-2 Policy bind constructor — Workflow null. */
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
                String customerRole,
                UUID policyApplicabilityId,
                UUID policyDocumentId,
                String policyVersionLabel) {
            this(code, name, description, borrowerType, loanProduct, intakeSegment,
                    minAmount, maxAmount, policySetId, effectiveFrom, effectiveUntil, reasonForChange,
                    entityType, customerRole, policyApplicabilityId, policyDocumentId, policyVersionLabel,
                    null, null);
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
            String policyLinkageStatus,
            /** W2 exact Workflow Version id. */
            UUID workflowId,
            Integer workflowVersion,
            String workflowContentHash,
            String workflowName,
            /** LINKED | WORKFLOW_LINKAGE_REQUIRED */
            String workflowLinkageStatus
    ) {}

    /**
     * Policy Studio catalogue picker row for Category admin.
     * Incompatible / needs-context Policies are still returned (not hidden).
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
            /** True only when {@link #compatibilityStatus} is COMPATIBLE. */
            boolean compatibleWithCategory,
            /** COMPATIBLE | INCOMPATIBLE | NEEDS_ADDITIONAL_SCOPE_CONTEXT */
            String compatibilityStatus,
            List<String> compatibilityReasons,
            List<String> compatibilityNotes,
            /** Business-facing scope summary (not raw JSON). */
            String scopeSummary,
            /** APPROVED/SCHEDULED/ACTIVE only — from {@code PolicyCanonicalLifecycleAuthority}. */
            boolean eligibleForCategoryLinkage,
            String linkageOwnerType,
            String linkageOwnerId,
            String lifecycleAuthority,
            String ineligibleReason
    ) {}

    /** Read-only scan of DRAFT Categories vs catalogue (no data mutation). */
    public record CategoryPolicyCompatibilityReportRow(
            String categoryCode,
            String categoryName,
            String status,
            int compatiblePolicyVersionCount,
            int incompatibleCount,
            int needsContextCount
    ) {}

    /**
     * W2 Workflow picker row for Category admin.
     * Incompatible Workflows remain visible (not priority-matched / not hidden).
     */
    public record EligibleWorkflowView(
            UUID workflowId,
            String workflowName,
            int workflowVersion,
            boolean active,
            String entityTypeApplicability,
            String customerRoleApplicability,
            String productApplicability,
            String journeyStepSummary,
            String contentHash,
            boolean compatibleWithCategory,
            /** COMPATIBLE | INCOMPATIBLE */
            String compatibilityStatus,
            List<String> compatibilityReasons,
            List<String> compatibilityNotes,
            String scopeSummary
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
