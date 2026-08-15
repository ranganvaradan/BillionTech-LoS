package com.los.core.customercategory.selection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CategorySelectionDtos {

    private CategorySelectionDtos() {}

    public record EligibilityContext(
            UUID applicationId,
            String customerRole,
            String entityType,
            String loanProduct,
            BigDecimal requestedAmount,
            Instant asOf,
            UUID subProgramId,
            String programmeTag,
            String channelTag,
            /** Staging/tests only — include DRAFT Categories with binds. Never production default. */
            boolean allowDraftSimulation
    ) {}

    public record EligibleCategoryView(
            UUID categoryId,
            String code,
            int versionNo,
            String name,
            String customerFacingName,
            String shortDescription,
            String requirementsSummary,
            int displayOrder,
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel,
            UUID workflowId,
            Integer workflowVersion,
            String status,
            Map<String, List<String>> disambiguationAttributes
    ) {}

    public record AnswerOptionView(
            String value,
            String label,
            List<UUID> retainsCategoryIds
    ) {}

    public record DisambiguationQuestionView(
            String questionId,
            String prompt,
            String attributeKey,
            String intakeFieldKey,
            boolean safe,
            String whySafe,
            List<AnswerOptionView> options
    ) {}

    public record PropositionCard(
            UUID categoryId,
            String code,
            int versionNo,
            String name,
            String shortDescription,
            String requirementsSummary,
            List<String> benefits,
            int displayOrder
    ) {}

    public record EligibilityResult(
            CategorySelectionState state,
            List<EligibleCategoryView> eligible,
            List<String> noMatchReasons,
            DisambiguationQuestionView nextQuestion,
            List<PropositionCard> propositions,
            SelectedApplicationConfiguration selected,
            Map<String, Object> diagnostics
    ) {}

    public record AnswerRequest(
            String questionId,
            String answerValue,
            String actor,
            String actorRole
    ) {}

    public record SelectRequest(
            UUID categoryId,
            String actor,
            String actorRole,
            String reason,
            CategorySelectionSource selectionSource
    ) {}

    /**
     * Clean handoff for later W4/W6 — this step does not invoke them.
     */
    public record SelectedApplicationConfiguration(
            UUID applicationId,
            UUID categoryId,
            String categoryCode,
            int categoryVersion,
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel,
            UUID workflowId,
            Integer workflowVersion,
            CategorySelectionSource selectionSource,
            Instant selectedAt,
            String selectedBy
    ) {}

    public record PropositionConfigUpdate(
            Map<String, Object> proposition,
            Map<String, Object> disambiguation
    ) {}
}
