package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;

/**
 * Classifies existing applications by pin completeness. Does not auto-migrate.
 */
public final class CanonicalApplicationPinClassifier {

    public enum Class {
        FULLY_CANONICALLY_PINNED,
        PARTIALLY_PINNED,
        LEGACY_UNPINNED,
        INTERNALLY_INCONSISTENT
    }

    private CanonicalApplicationPinClassifier() {}

    public static Class classify(
            LoanApplication app,
            CustomerCategoryEntity category,
            CiPolicyApplicability applicability,
            WorkflowConfig workflow) {
        if (app == null) {
            return Class.LEGACY_UNPINNED;
        }
        boolean hasCat = app.getSelectedCustomerCategoryId() != null;
        boolean hasWf = app.getWorkflowId() != null;
        boolean hasPol = app.getSelectedPolicyApplicabilityId() != null;
        boolean hasDoc = app.getSelectedPolicyDocumentId() != null;
        int pinCount = (hasCat ? 1 : 0) + (hasWf ? 1 : 0) + (hasPol ? 1 : 0) + (hasDoc ? 1 : 0);
        if (pinCount == 0) {
            return Class.LEGACY_UNPINNED;
        }
        if (inconsistent(app, category, applicability, workflow, hasCat, hasWf, hasPol, hasDoc)) {
            return Class.INTERNALLY_INCONSISTENT;
        }
        if (hasCat && hasWf && hasPol && hasDoc) {
            return Class.FULLY_CANONICALLY_PINNED;
        }
        return Class.PARTIALLY_PINNED;
    }

    private static boolean inconsistent(
            LoanApplication app,
            CustomerCategoryEntity category,
            CiPolicyApplicability applicability,
            WorkflowConfig workflow,
            boolean hasCat,
            boolean hasWf,
            boolean hasPol,
            boolean hasDoc) {
        if (hasCat && category == null) {
            return true;
        }
        if (hasPol && applicability == null) {
            return true;
        }
        if (hasWf && workflow == null) {
            return true;
        }
        if (hasCat && category != null) {
            if (app.getSelectedCustomerCategoryVersion() != null
                    && app.getSelectedCustomerCategoryVersion() != category.getVersionNo()) {
                return true;
            }
            if (hasPol && category.getPolicyApplicabilityId() != null
                    && !category.getPolicyApplicabilityId().equals(app.getSelectedPolicyApplicabilityId())) {
                return true;
            }
            if (hasDoc && category.getPolicyDocumentId() != null
                    && !category.getPolicyDocumentId().equals(app.getSelectedPolicyDocumentId())) {
                return true;
            }
            if (hasWf && category.getWorkflowId() != null
                    && !category.getWorkflowId().equals(app.getWorkflowId())) {
                return true;
            }
        }
        if (hasPol && hasDoc && applicability != null && applicability.getPolicyDocumentId() != null
                && !applicability.getPolicyDocumentId().equals(app.getSelectedPolicyDocumentId())) {
            return true;
        }
        if (hasWf && workflow != null && app.getWorkflowVersion() != null
                && app.getWorkflowVersion() != workflow.getVersion()) {
            return true;
        }
        return false;
    }
}
