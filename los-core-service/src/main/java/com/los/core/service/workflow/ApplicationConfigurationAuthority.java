package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CUSTOMER-CATEGORY-AUTHORITY-CUTOVER-2
 *
 * <p>Cutover discriminator (deterministic, persisted — not wall-clock):
 * {@code loan_applications.workflow_id}.
 *
 * <ul>
 *   <li>{@code workflow_id IS NULL} — new unconfigured application. Category must pin
 *       workflow + policy. Default discovery is forbidden.</li>
 *   <li>{@code workflow_id IS NOT NULL} and {@code selected_customer_category_id IS NULL}
 *       — pre-cutover / historical / admin-explicit pin. Execute that exact version.
 *       Do not auto-assign Category.</li>
 *   <li>Both Category and workflow (+ policy) pinned — Category-governed new application.</li>
 * </ul>
 */
public final class ApplicationConfigurationAuthority {

    public static final String CUTOVER_DISCRIMINATOR = "loan_applications.workflow_id";

    public static final String WORKFLOW_NOT_PINNED = "WORKFLOW_NOT_PINNED";
    public static final String CATEGORY_CONFIGURATION_NOT_PINNED = "CATEGORY_CONFIGURATION_NOT_PINNED";
    public static final String WORKFLOW_ID_NOT_PERMITTED_ON_ORDINARY_INTAKE =
            "WORKFLOW_ID_NOT_PERMITTED_ON_ORDINARY_INTAKE";
    public static final String WORKFLOW_PIN_IMMUTABLE = "WORKFLOW_PIN_IMMUTABLE";
    public static final String CONFIGURATION_NOT_READY = "CONFIGURATION_NOT_READY";

    private static final Set<String> CONTROLLED_EXPLICIT_WORKFLOW_ROLES = Set.of(
            "ADMIN", "ADMINISTRATOR", "PLATFORM_ADMIN", "SYSTEM", "TEST");

    private ApplicationConfigurationAuthority() {}

    public static boolean isNewApplicationUnconfigured(LoanApplication app) {
        return app != null && app.getWorkflowId() == null;
    }

    /**
     * Pre-cutover or admin/migration/test explicit pin: workflow exists, Category was never selected.
     */
    public static boolean isPreCutoverWorkflowPin(LoanApplication app) {
        return app != null
                && app.getWorkflowId() != null
                && app.getSelectedCustomerCategoryId() == null;
    }

    public static boolean isCategoryConfigurationPinned(LoanApplication app) {
        return app != null
                && app.getSelectedCustomerCategoryId() != null
                && app.getWorkflowId() != null
                && app.getSelectedPolicyApplicabilityId() != null
                && app.getSelectedPolicyDocumentId() != null;
    }

    public static boolean maySupplyExplicitWorkflowId(String actingUserRole) {
        if (actingUserRole == null || actingUserRole.isBlank()) {
            return false;
        }
        return CONTROLLED_EXPLICIT_WORKFLOW_ROLES.contains(actingUserRole.trim().toUpperCase(Locale.ROOT));
    }

    public static void assertOrdinaryCreateDoesNotSelectWorkflow(UUID requestedWorkflowId, String actingUserRole) {
        if (requestedWorkflowId == null) {
            return;
        }
        if (maySupplyExplicitWorkflowId(actingUserRole)) {
            return;
        }
        throw new BusinessRuleException(
                "Ordinary application intake cannot independently select a Workflow. "
                        + "Customer Category is the configuration-selection authority.",
                WORKFLOW_ID_NOT_PERMITTED_ON_ORDINARY_INTAKE,
                "CREATE_APPLICATION",
                Map.of("workflowId", requestedWorkflowId.toString()));
    }

    /**
     * Fail closed on update: never silently replace a Category pin or an in-flight journey.
     */
    public static void assertWorkflowIdUpdateAllowed(LoanApplication app, UUID requestedWorkflowId) {
        if (requestedWorkflowId == null) {
            return;
        }
        boolean executionBegun = app.getStatus() != null && app.getStatus() != ApplicationStatus.DRAFT;
        boolean categoryPinned = app.getSelectedCustomerCategoryId() != null;
        boolean alreadyPinned = app.getWorkflowId() != null;
        if (categoryPinned || executionBegun || alreadyPinned) {
            throw new BusinessRuleException(
                    "Workflow Version cannot be replaced after Customer Category is pinned "
                            + "or workflow execution has begun",
                    WORKFLOW_PIN_IMMUTABLE,
                    "UPDATE_APPLICATION",
                    Map.of(
                            "applicationId", app.getId() != null ? app.getId().toString() : "",
                            "existingWorkflowId", app.getWorkflowId() != null ? app.getWorkflowId().toString() : "",
                            "requestedWorkflowId", requestedWorkflowId.toString(),
                            "categoryPinned", categoryPinned,
                            "status", app.getStatus() != null ? app.getStatus().name() : ""));
        }
        throw new BusinessRuleException(
                "Ordinary application update cannot independently select a Workflow. "
                        + "Select a Customer Category to pin configuration.",
                WORKFLOW_ID_NOT_PERMITTED_ON_ORDINARY_INTAKE,
                "UPDATE_APPLICATION",
                Map.of("workflowId", requestedWorkflowId.toString()));
    }

    public static void assertReadyForSubmit(LoanApplication app) {
        if (isPreCutoverWorkflowPin(app)) {
            return;
        }
        if (isCategoryConfigurationPinned(app)) {
            return;
        }
        throw new BusinessRuleException(
                "Application cannot be submitted until Customer Category has pinned "
                        + "workflow version, policy applicability, and policy document",
                CATEGORY_CONFIGURATION_NOT_PINNED,
                "SUBMIT_APPLICATION",
                Map.of(
                        "applicationId", app.getId() != null ? app.getId().toString() : "",
                        "categorySelectionState",
                        app.getCategorySelectionState() != null ? app.getCategorySelectionState() : "",
                        "workflowId", app.getWorkflowId() != null ? app.getWorkflowId().toString() : "",
                        "cutoverDiscriminator", CUTOVER_DISCRIMINATOR));
    }

    public static void assertReadyForWorkflowExecution(LoanApplication app) {
        if (app != null && app.getWorkflowId() != null) {
            return;
        }
        throw notPinned(app);
    }

    public static BusinessRuleException notPinned(LoanApplication app) {
        return new BusinessRuleException(
                "No Workflow Version is pinned on this application. "
                        + "Customer Category selection is required for new applications.",
                WORKFLOW_NOT_PINNED,
                "RESOLVE_WORKFLOW",
                Map.of(
                        "applicationId", app != null && app.getId() != null ? app.getId().toString() : "",
                        "categorySelectionState",
                        app != null && app.getCategorySelectionState() != null
                                ? app.getCategorySelectionState() : "",
                        "cutoverDiscriminator", CUTOVER_DISCRIMINATOR));
    }

    public static boolean isUnconfiguredReason(String reason) {
        return WORKFLOW_NOT_PINNED.equals(reason)
                || CATEGORY_CONFIGURATION_NOT_PINNED.equals(reason)
                || CONFIGURATION_NOT_READY.equals(reason)
                || "WORKFLOW_NOT_RESOLVED".equals(reason);
    }
}
