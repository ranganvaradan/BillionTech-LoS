package com.los.core.requirement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing RequirementPlan projection (W5). No GACAT/internal ids in customer fields.
 */
public final class CustomerRequirementDtos {

    private CustomerRequirementDtos() {}

    public record CustomerSummary(
            int informationRequiredCount,
            int documentsRequiredCount,
            int providedCount,
            int processingCount,
            int remainingActionsCount,
            int reuploadRequiredCount
    ) {}

    public record FieldMeta(
            String label,
            String helpText,
            String datatype,
            String unit,
            List<String> allowedValues,
            boolean required,
            String inputType
    ) {}

    /**
     * One customer-facing action card (may represent a document group linking many parameters).
     */
    public record CustomerAction(
            String actionKey,
            String section,
            String title,
            String description,
            String whyNeeded,
            List<FulfilmentMode> allowedModes,
            FulfilmentMode preferredMode,
            FulfilmentMode chosenMode,
            CustomerFulfilmentState customerFulfilment,
            String customerStatusLabel,
            DataReadinessState dataReadiness,
            String processingLabel,
            boolean actionable,
            boolean showChoice,
            boolean reuploadRequired,
            boolean extractionFailed,
            String documentGroup,
            String documentRef,
            List<UUID> itemIds,
            List<String> linkedLabels,
            FieldMeta field,
            Map<String, Object> draftValue
    ) {}

    public record CustomerRequirementsView(
            UUID applicationId,
            UUID planId,
            int planVersion,
            boolean planPresent,
            CustomerSummary summary,
            List<CustomerAction> actions,
            List<CustomerAction> providedOrProcessing,
            String emptyMessage
    ) {}

    public record AdminCustomerDebugRow(
            UUID itemId,
            String itemKey,
            String canonicalParameterId,
            boolean customerActionRequired,
            List<FulfilmentMode> allowedModes,
            FulfilmentMode preferredMode,
            CustomerFulfilmentState customerFulfilment,
            DataReadinessState dataReadiness,
            SourceAcquisitionState sourceState,
            String whyRequired,
            String evidenceRef,
            String documentRef
    ) {}

    public record DirectInputSubmitRequest(
            String value,
            Boolean verified,
            String actor,
            String actorRole,
            String reason,
            Boolean saveDraftOnly
    ) {}

    public record DocumentFulfilRequest(
            String documentRef,
            String actor,
            String actorRole,
            String reason
    ) {}

    public record ChooseModeRequest(
            FulfilmentMode mode,
            String actor,
            String actorRole
    ) {}

    public record DocumentOutcomeRequest(
            String outcome,
            String actor,
            String actorRole,
            String reason
    ) {}
}
