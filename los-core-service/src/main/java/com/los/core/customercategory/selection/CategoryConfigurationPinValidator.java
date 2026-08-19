package com.los.core.customercategory.selection;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.scorecard.PolicyVersionScorecardLinkage;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Validates exact Category → workflow → policy → scorecard identities before any pin write.
 */
@Component
@RequiredArgsConstructor
public class CategoryConfigurationPinValidator {

    private final WorkflowConfigRepository workflowConfigRepository;
    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final CiPolicyDocumentRepository policyDocumentRepository;
    private final UnderwritingScorecardRepository scorecardRepository;

    public record ValidatedPin(
            CustomerCategoryEntity category,
            WorkflowConfig workflow,
            CiPolicyApplicability applicability,
            CiPolicyDocument document,
            UUID scorecardId,
            boolean scorecardExplicitlyAbsent) {}

    public ValidatedPin validate(LoanApplication app, CustomerCategoryEntity chosen, boolean allowDraftSimulation) {
        if (!hasUsableBinds(chosen)) {
            throw fail("Category Policy/Workflow bindings incomplete",
                    "CATEGORY_BINDINGS_INCOMPLETE",
                    Map.of("categoryId", chosen.getId().toString()));
        }

        WorkflowConfig workflow = workflowConfigRepository.findById(chosen.getWorkflowId())
                .orElseThrow(() -> fail(
                        "Category Workflow Version not found: " + chosen.getWorkflowId(),
                        "WORKFLOW_VERSION_NOT_FOUND",
                        Map.of("workflowId", chosen.getWorkflowId().toString())));
        if (!workflow.isActive()) {
            throw fail("Category Workflow Version is not executable",
                    "WORKFLOW_VERSION_NOT_EXECUTABLE",
                    Map.of("workflowId", workflow.getId().toString(), "active", false));
        }

        CiPolicyApplicability applicability = applicabilityRepository.findById(chosen.getPolicyApplicabilityId())
                .orElseThrow(() -> fail(
                        "Category Policy Applicability not found: " + chosen.getPolicyApplicabilityId(),
                        "POLICY_APPLICABILITY_NOT_FOUND",
                        Map.of("policyApplicabilityId", chosen.getPolicyApplicabilityId().toString())));

        String lifecycle = PolicyCanonicalLifecycleAuthority.reconcile(null, applicability.getBusinessStatus());
        if (!PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(lifecycle)
                && !allowDraftSimulation) {
            throw fail("Policy applicability is not eligible for Customer Category linkage",
                    "POLICY_APPLICABILITY_NOT_ELIGIBLE",
                    Map.of(
                            "policyApplicabilityId", applicability.getId().toString(),
                            "lifecycle", lifecycle));
        }

        UUID documentId = chosen.getPolicyDocumentId();
        CiPolicyDocument document = policyDocumentRepository.findById(documentId)
                .orElseThrow(() -> fail(
                        "Category Policy Document not found: " + documentId,
                        "POLICY_DOCUMENT_NOT_FOUND",
                        Map.of("policyDocumentId", documentId.toString())));

        if (applicability.getPolicyDocumentId() != null
                && !applicability.getPolicyDocumentId().equals(document.getId())) {
            throw fail("Policy document does not belong to the selected applicability",
                    "POLICY_DOCUMENT_APPLICABILITY_MISMATCH",
                    Map.of(
                            "policyDocumentId", document.getId().toString(),
                            "applicabilityDocumentId", applicability.getPolicyDocumentId().toString()));
        }
        if (chosen.getPolicyDocumentId() != null
                && !chosen.getPolicyDocumentId().equals(document.getId())) {
            throw fail("Policy document does not match Category bind",
                    "POLICY_DOCUMENT_IDENTITY_MISMATCH",
                    Map.of(
                            "categoryPolicyDocumentId", chosen.getPolicyDocumentId().toString(),
                            "documentId", document.getId().toString()));
        }

        final UUID scorecardFk = PolicyVersionScorecardLinkage.canonicalScorecardId(document.getScorecardId());
        boolean absent = scorecardFk == null;
        UUID resolvedScorecardId = scorecardFk;
        if (!absent) {
            UnderwritingScorecard scorecard = scorecardRepository.findById(scorecardFk)
                    .orElseThrow(() -> fail(
                            "Policy-linked Scorecard not found: " + scorecardFk,
                            "SCORECARD_VERSION_NOT_RESOLVABLE",
                            Map.of(
                                    "scorecardId", scorecardFk.toString(),
                                    "policyDocumentId", document.getId().toString())));
            resolvedScorecardId = scorecard.getId();
        }

        if (app.getWorkflowId() != null && chosen.getWorkflowId() != null
                && !app.getWorkflowId().equals(chosen.getWorkflowId())) {
            throw fail(
                    "Application already bound to a different Workflow Version than the selected Category",
                    "CATEGORY_WORKFLOW_CONFLICT",
                    Map.of(
                            "applicationWorkflowId", app.getWorkflowId().toString(),
                            "categoryWorkflowId", chosen.getWorkflowId().toString()));
        }

        return new ValidatedPin(chosen, workflow, applicability, document, resolvedScorecardId, absent);
    }

    public static boolean hasUsableBinds(CustomerCategoryEntity c) {
        return c != null
                && c.getPolicyApplicabilityId() != null
                && c.getPolicyDocumentId() != null
                && c.getWorkflowId() != null;
    }

    /**
     * Same gate as {@link #validate} — used by proposition discovery so selectable categories
     * always match what selection can pin.
     */
    public boolean isPinReady(LoanApplication app, CustomerCategoryEntity chosen, boolean allowDraftSimulation) {
        try {
            validate(app, chosen, allowDraftSimulation);
            return true;
        } catch (BusinessRuleException ex) {
            return false;
        }
    }

    private static BusinessRuleException fail(String message, String reason, Map<String, Object> ctx) {
        return new BusinessRuleException(message, reason, "category-selection", new LinkedHashMap<>(ctx));
    }
}
