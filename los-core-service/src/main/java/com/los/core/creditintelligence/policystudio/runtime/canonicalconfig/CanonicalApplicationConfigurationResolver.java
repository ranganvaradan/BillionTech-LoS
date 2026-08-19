package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphNode;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphOperand;
import com.los.core.creditintelligence.policystudio.lifecycle.ApplicationPolicyQueryFactory;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinitionRepository;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphNodeRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphOperandRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.creditintelligence.policystudio.scorecard.PolicyVersionScorecardLinkage;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sole future authority for resolving the canonical application configuration identity package.
 * Starts from application / customer-category pins. Never product → latest policy/workflow/scorecard.
 * Does not mutate application bindings. Does not determine live credit decision.
 */
@Service
@RequiredArgsConstructor
public class CanonicalApplicationConfigurationResolver {

    public static final String WORKFLOW_VERSION_RESOLVED = "WORKFLOW_VERSION_RESOLVED";
    public static final String WORKFLOW_VERSION_NOT_PINNED = "WORKFLOW_VERSION_NOT_PINNED";
    public static final String WORKFLOW_VERSION_NOT_FOUND = "WORKFLOW_VERSION_NOT_FOUND";
    public static final String WORKFLOW_VERSION_IDENTITY_MISMATCH = "WORKFLOW_VERSION_IDENTITY_MISMATCH";

    public static final String SCORECARD_EXPLICITLY_ABSENT = "POLICY_HAS_NO_SCORECARD";
    public static final String BUREAU_NOT_REQUIRED = "BUREAU_NOT_REQUIRED";
    public static final String BUREAU_REPORT_PINNED = "BUREAU_REPORT_PINNED";

    private final CustomerCategoryRepository customerCategoryRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final CiPolicyDocumentRepository policyDocumentRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final CiPolicyRuleGraphRepository ruleGraphRepository;
    private final CiPolicyRuleGraphNodeRepository ruleGraphNodeRepository;
    private final CiPolicyRuleGraphOperandRepository operandRepository;
    private final CiGacatDerivedCalculationDefinitionRepository calculationDefinitionRepository;
    private final CiBureauReportRepository bureauReportRepository;

    Function<String, Optional<CanonicalParameterDefinition>> parameterLookup = id -> {
        try {
            return CanonicalParameterRegistry.shared().findById(id);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    };

    @Transactional(readOnly = true)
    public CanonicalApplicationConfigurationResolution resolve(LoanApplication app) {
        List<String> reasons = new ArrayList<>();
        Map<String, String> outcomes = new LinkedHashMap<>();
        Map<String, String> provenance = new LinkedHashMap<>();
        provenance.put("resolver", getClass().getName());
        provenance.put("startedFrom", "APPLICATION_CUSTOMER_CATEGORY_SELECTION");
        provenance.put("latestLookupForbidden", "true");
        provenance.put("productDefaultForbidden", "true");
        provenance.put("wallClockFallbackForbidden", "true");
        stampForbiddenLookups(provenance);

        if (app == null || app.getId() == null) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
            return fail(null, reasons, outcomes, Instant.now());
        }

        UUID applicationId = app.getId();
        Instant resolvedAt = Instant.now();

        CustomerCategoryEntity category = resolveCategory(app, reasons, outcomes, provenance);
        WorkflowConfig workflow = resolveWorkflow(app, category, reasons, outcomes, provenance);
        CiPolicyApplicability applicability = resolveApplicability(app, category, reasons, outcomes, provenance);
        CiPolicyDocument document = resolveDocument(app, category, applicability, reasons, outcomes, provenance);
        ScorecardPin scorecard = resolveScorecard(document, reasons, outcomes, provenance);
        LocalDate evaluationAsOf = resolveEvaluationAsOf(app, reasons, outcomes, provenance);
        List<CanonicalCalculationPin> calcPins = resolveCalculationPins(
                document, scorecard.row(), reasons, outcomes, provenance);
        BureauPin bureau = resolveBureau(app, workflow, calcPins, reasons, outcomes, provenance);

        CanonicalApplicationConfiguration cfg = new CanonicalApplicationConfiguration(
                applicationId,
                category == null ? app.getSelectedCustomerCategoryId() : category.getId(),
                category == null ? app.getSelectedCustomerCategoryVersion() : Integer.valueOf(category.getVersionNo()),
                category == null ? app.getSelectedCustomerCategoryCode() : category.getCode(),
                workflow == null ? app.getWorkflowId() : workflow.getId(),
                workflow == null ? app.getWorkflowVersion() : Integer.valueOf(workflow.getVersion()),
                applicability == null ? app.getSelectedPolicyApplicabilityId() : applicability.getId(),
                document == null ? app.getSelectedPolicyDocumentId() : document.getId(),
                document == null ? null : document.getDocumentVersion(),
                applicability == null ? app.getSelectedPolicyVersionLabel() : applicability.getPolicyVersionLabel(),
                scorecard.id(),
                scorecard.version(),
                scorecard.required(),
                scorecard.explicitlyAbsent(),
                bureau.id(),
                bureau.providerCode(),
                bureau.parserVersion(),
                bureau.normalizerVersion(),
                evaluationAsOf,
                calcPins,
                resolvedAt,
                provenance);

        if (!reasons.isEmpty()) {
            return new CanonicalApplicationConfigurationResolution(
                    CanonicalResolutionStatus.NOT_RESOLVABLE, cfg, reasons, outcomes);
        }
        if (cfg.workflowVersionId() == null) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_PINNED.name());
        }
        if (cfg.policyApplicabilityId() == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
        }
        if (cfg.scorecardRequired() && cfg.scorecardId() == null) {
            reasons.add(CanonicalResolutionFailureCode.SCORECARD_VERSION_NOT_RESOLVABLE.name());
        }
        int authoredUnpinned = 0;
        for (CanonicalCalculationPin pin : calcPins) {
            if (CanonicalCalculationPin.SOURCE_NOT_PROVEN.equals(pin.calculationType())
                    || PolicyExecutionReadiness.isSourceNotProven(pin.parameterId())) {
                continue;
            }
            if (CanonicalCalculationPin.AUTHORED_EXPRESSION.equals(pin.calculationType())
                    && pin.calculationDefinitionId() == null) {
                authoredUnpinned++;
            }
            if (pin.fallbackRequired()) {
                reasons.add(CanonicalResolutionFailureCode.AUTHORED_CALCULATION_NOT_PINNED.name());
            }
        }
        if (authoredUnpinned > 0) {
            reasons.add(CanonicalResolutionFailureCode.AUTHORED_CALCULATION_NOT_PINNED.name());
        }
        if (!reasons.isEmpty()) {
            return new CanonicalApplicationConfigurationResolution(
                    CanonicalResolutionStatus.NOT_RESOLVABLE, cfg, unique(reasons), outcomes);
        }
        outcomes.putIfAbsent("status", CanonicalResolutionStatus.RESOLVED.name());
        return new CanonicalApplicationConfigurationResolution(
                CanonicalResolutionStatus.RESOLVED, cfg, List.of(), outcomes);
    }

    /**
     * Configuration-only resolution from a Customer Category bind.
     * Does not create an application. Does not look up latest/default/product-override artifacts.
     */
    @Transactional(readOnly = true)
    public CanonicalApplicationConfigurationResolution resolveFromCustomerCategory(UUID categoryId) {
        List<String> reasons = new ArrayList<>();
        Map<String, String> outcomes = new LinkedHashMap<>();
        Map<String, String> provenance = new LinkedHashMap<>();
        provenance.put("resolver", getClass().getName());
        provenance.put("startedFrom", "CUSTOMER_CATEGORY_CONFIGURATION");
        provenance.put("preflight", "true");
        provenance.put("applicationCreated", "false");
        provenance.put("latestLookupForbidden", "true");
        provenance.put("productDefaultForbidden", "true");
        provenance.put("wallClockFallbackForbidden", "true");
        stampForbiddenLookups(provenance);
        Instant resolvedAt = Instant.now();

        if (categoryId == null) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
            outcomes.put("customerCategory", CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
            return fail(null, reasons, outcomes, resolvedAt);
        }
        Optional<CustomerCategoryEntity> foundCat = customerCategoryRepository.findById(categoryId);
        if (foundCat.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_FOUND.name());
            outcomes.put("customerCategory", CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_FOUND.name());
            provenance.put("customerCategory", "NOT_FOUND");
            return fail(null, reasons, outcomes, resolvedAt);
        }
        CustomerCategoryEntity category = foundCat.get();
        outcomes.put("customerCategory", "CUSTOMER_CATEGORY_RESOLVED");
        provenance.put("customerCategory", "CATEGORY_ROW");
        provenance.put("customerCategoryId", category.getId().toString());

        WorkflowConfig workflow = null;
        if (category.getWorkflowId() == null) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_PINNED.name());
            outcomes.put("workflow", WORKFLOW_VERSION_NOT_PINNED);
            provenance.put("workflow", WORKFLOW_VERSION_NOT_PINNED);
        } else {
            Optional<WorkflowConfig> foundWf = workflowConfigRepository.findById(category.getWorkflowId());
            if (foundWf.isEmpty()) {
                reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_FOUND.name());
                outcomes.put("workflow", WORKFLOW_VERSION_NOT_FOUND);
                provenance.put("workflow", WORKFLOW_VERSION_NOT_FOUND);
            } else {
                workflow = foundWf.get();
                if (category.getWorkflowVersion() != null
                        && category.getWorkflowVersion() != workflow.getVersion()) {
                    reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_IDENTITY_MISMATCH.name());
                    outcomes.put("workflow", WORKFLOW_VERSION_IDENTITY_MISMATCH);
                    provenance.put("workflow", WORKFLOW_VERSION_IDENTITY_MISMATCH);
                } else {
                    outcomes.put("workflow", WORKFLOW_VERSION_RESOLVED);
                    provenance.put("workflow", "CUSTOMER_CATEGORY_WORKFLOW_ID");
                    provenance.put("workflowVersionId", workflow.getId().toString());
                }
            }
        }

        CiPolicyApplicability applicability = null;
        if (category.getPolicyApplicabilityId() == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
            outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
            provenance.put("policyApplicability", "NOT_PINNED");
        } else {
            Optional<CiPolicyApplicability> foundApp =
                    applicabilityRepository.findById(category.getPolicyApplicabilityId());
            if (foundApp.isEmpty()) {
                reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_FOUND.name());
                outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_FOUND.name());
                provenance.put("policyApplicability", "PINNED_BUT_NOT_FOUND");
            } else {
                applicability = foundApp.get();
                String lifecycle = PolicyCanonicalLifecycleAuthority.reconcile(null, applicability.getBusinessStatus());
                if (!PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(lifecycle)) {
                    reasons.add(CanonicalResolutionFailureCode.POLICY_LIFECYCLE_NOT_ELIGIBLE.name());
                    outcomes.put("policyApplicability",
                            CanonicalResolutionFailureCode.POLICY_LIFECYCLE_NOT_ELIGIBLE.name());
                    provenance.put("policyApplicability", "LIFECYCLE_" + lifecycle);
                } else {
                    outcomes.put("policyApplicability", "POLICY_APPLICABILITY_RESOLVED");
                    provenance.put("policyApplicability", "CUSTOMER_CATEGORY_BIND");
                    provenance.put("canonicalLifecycle", lifecycle);
                }
            }
        }

        CiPolicyDocument document = null;
        UUID documentId = category.getPolicyDocumentId();
        if (documentId == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_PINNED.name());
            outcomes.put("policyDocument", CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_PINNED.name());
            provenance.put("policyDocument", "NOT_PINNED");
        } else {
            Optional<CiPolicyDocument> foundDoc = policyDocumentRepository.findById(documentId);
            if (foundDoc.isEmpty()) {
                reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_FOUND.name());
                outcomes.put("policyDocument", CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_FOUND.name());
                provenance.put("policyDocument", "PINNED_BUT_NOT_FOUND");
            } else {
                document = foundDoc.get();
                boolean drift = applicability != null && applicability.getPolicyDocumentId() != null
                        && !applicability.getPolicyDocumentId().equals(document.getId());
                if (drift) {
                    reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_IDENTITY_MISMATCH.name());
                    outcomes.put("policyDocument",
                            CanonicalResolutionFailureCode.POLICY_DOCUMENT_IDENTITY_MISMATCH.name());
                    provenance.put("policyDocument", "IDENTITY_MISMATCH");
                } else {
                    outcomes.put("policyDocument", "POLICY_DOCUMENT_RESOLVED");
                    provenance.put("policyDocument", "CUSTOMER_CATEGORY_BIND");
                    provenance.put("policyDocumentId", document.getId().toString());
                    provenance.put("scorecardLinkageAuthority", PolicyVersionScorecardLinkage.AUTHORITY);
                }
            }
        }

        ScorecardPin scorecard = resolveScorecard(document, reasons, outcomes, provenance);
        outcomes.put("evaluationAsOf", "CONFIGURATION_PREFLIGHT_NO_APPLICATION");
        provenance.put("evaluationAsOf", "NOT_AN_APPLICATION");
        provenance.put("wallClockNowUsed", "false");
        List<CanonicalCalculationPin> calcPins = resolveCalculationPins(
                document, scorecard.row(), reasons, outcomes, provenance);
        outcomes.put("bureau", BUREAU_NOT_REQUIRED);
        provenance.put("bureau", "CONFIGURATION_PREFLIGHT_NO_APPLICATION");

        CanonicalApplicationConfiguration cfg = new CanonicalApplicationConfiguration(
                null,
                category.getId(),
                category.getVersionNo(),
                category.getCode(),
                workflow == null ? category.getWorkflowId() : workflow.getId(),
                workflow == null ? category.getWorkflowVersion() : workflow.getVersion(),
                applicability == null ? category.getPolicyApplicabilityId() : applicability.getId(),
                document == null ? category.getPolicyDocumentId() : document.getId(),
                document == null ? null : document.getDocumentVersion(),
                applicability == null ? category.getPolicyVersionLabel() : applicability.getPolicyVersionLabel(),
                scorecard.id(),
                scorecard.version(),
                scorecard.required(),
                scorecard.explicitlyAbsent(),
                null, null, null, null,
                null,
                calcPins,
                resolvedAt,
                provenance);

        if (cfg.workflowVersionId() == null) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_PINNED.name());
        }
        if (cfg.policyApplicabilityId() == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
        }
        if (cfg.scorecardRequired() && cfg.scorecardId() == null) {
            reasons.add(CanonicalResolutionFailureCode.SCORECARD_VERSION_NOT_RESOLVABLE.name());
        }
        if (!reasons.isEmpty()) {
            return new CanonicalApplicationConfigurationResolution(
                    CanonicalResolutionStatus.NOT_RESOLVABLE, cfg, unique(reasons), outcomes);
        }
        outcomes.putIfAbsent("status", CanonicalResolutionStatus.RESOLVED.name());
        return new CanonicalApplicationConfigurationResolution(
                CanonicalResolutionStatus.RESOLVED, cfg, List.of(), outcomes);
    }

    public static Map<String, Integer> forbiddenLookupCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("DEFAULT_WORKFLOW_LOOKUP_COUNT", 0);
        counts.put("LATEST_WORKFLOW_LOOKUP_COUNT", 0);
        counts.put("LATEST_POLICY_LOOKUP_COUNT", 0);
        counts.put("LATEST_SCORECARD_LOOKUP_COUNT", 0);
        counts.put("LATEST_CALCULATION_LOOKUP_COUNT", 0);
        counts.put("PRODUCT_OVERRIDE_LOOKUP_COUNT", 0);
        return counts;
    }

    private static void stampForbiddenLookups(Map<String, String> provenance) {
        for (Map.Entry<String, Integer> e : forbiddenLookupCounts().entrySet()) {
            provenance.put(e.getKey(), String.valueOf(e.getValue()));
        }
    }

    private CustomerCategoryEntity resolveCategory(
            LoanApplication app,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        UUID pinnedId = app.getSelectedCustomerCategoryId();
        if (pinnedId == null) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
            outcomes.put("customerCategory", CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
            provenance.put("customerCategory", "NOT_PINNED");
            return null;
        }
        Optional<CustomerCategoryEntity> found = customerCategoryRepository.findById(pinnedId);
        if (found.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_FOUND.name());
            outcomes.put("customerCategory", CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_FOUND.name());
            provenance.put("customerCategory", "PINNED_BUT_NOT_FOUND");
            return null;
        }
        CustomerCategoryEntity category = found.get();
        if (app.getSelectedCustomerCategoryVersion() != null
                && app.getSelectedCustomerCategoryVersion() != category.getVersionNo()) {
            reasons.add(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_VERSION_MISMATCH.name());
            outcomes.put("customerCategory", CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_VERSION_MISMATCH.name());
            provenance.put("customerCategory", "VERSION_MISMATCH");
            return category;
        }
        outcomes.put("customerCategory", "CUSTOMER_CATEGORY_RESOLVED");
        provenance.put("customerCategory", "APPLICATION_PIN");
        provenance.put("customerCategoryId", category.getId().toString());
        return category;
    }

    private WorkflowConfig resolveWorkflow(
            LoanApplication app,
            CustomerCategoryEntity category,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        UUID pinnedId = app.getWorkflowId();
        if (pinnedId == null) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_PINNED.name());
            outcomes.put("workflow", WORKFLOW_VERSION_NOT_PINNED);
            provenance.put("workflow", WORKFLOW_VERSION_NOT_PINNED);
            return null;
        }
        Optional<WorkflowConfig> found = workflowConfigRepository.findById(pinnedId);
        if (found.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_FOUND.name());
            outcomes.put("workflow", WORKFLOW_VERSION_NOT_FOUND);
            provenance.put("workflow", WORKFLOW_VERSION_NOT_FOUND);
            return null;
        }
        WorkflowConfig wf = found.get();
        boolean mismatch = false;
        if (app.getWorkflowVersion() != null && app.getWorkflowVersion() != wf.getVersion()) {
            mismatch = true;
        }
        if (category != null && category.getWorkflowId() != null && !category.getWorkflowId().equals(wf.getId())) {
            mismatch = true;
        }
        if (mismatch) {
            reasons.add(CanonicalResolutionFailureCode.WORKFLOW_VERSION_IDENTITY_MISMATCH.name());
            outcomes.put("workflow", WORKFLOW_VERSION_IDENTITY_MISMATCH);
            provenance.put("workflow", WORKFLOW_VERSION_IDENTITY_MISMATCH);
            return wf;
        }
        outcomes.put("workflow", WORKFLOW_VERSION_RESOLVED);
        provenance.put("workflow", "APPLICATION_WORKFLOW_ID");
        provenance.put("workflowVersionId", wf.getId().toString());
        return wf;
    }

    private CiPolicyApplicability resolveApplicability(
            LoanApplication app,
            CustomerCategoryEntity category,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        UUID pinnedId = app.getSelectedPolicyApplicabilityId();
        if (pinnedId == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
            outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_PINNED.name());
            provenance.put("policyApplicability", "NOT_PINNED");
            return null;
        }
        Optional<CiPolicyApplicability> found = applicabilityRepository.findById(pinnedId);
        if (found.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_FOUND.name());
            outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_FOUND.name());
            provenance.put("policyApplicability", "PINNED_BUT_NOT_FOUND");
            return null;
        }
        CiPolicyApplicability applicability = found.get();
        if (category != null && category.getPolicyApplicabilityId() != null
                && !category.getPolicyApplicabilityId().equals(applicability.getId())) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_DRIFT.name());
            outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_APPLICABILITY_DRIFT.name());
            provenance.put("policyApplicability", "DRIFT_VS_CATEGORY");
            return applicability;
        }
        if (category != null) {
            boolean productOk = category.getLoanProduct() == null
                    || category.getLoanProduct().equalsIgnoreCase(nullToEmpty(app.getLoanProduct()));
            boolean borrowerOk = category.getBorrowerType() == null
                    || app.getBorrowerType() == null
                    || category.getBorrowerType().equalsIgnoreCase(app.getBorrowerType().name());
            if (!productOk || !borrowerOk) {
                reasons.add(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_SCOPE_MISMATCH.name());
                outcomes.put("policyApplicability",
                        CanonicalResolutionFailureCode.POLICY_APPLICABILITY_SCOPE_MISMATCH.name());
                provenance.put("policyApplicability", "SCOPE_MISMATCH");
                return applicability;
            }
        }
        String lifecycle = PolicyCanonicalLifecycleAuthority.reconcile(null, applicability.getBusinessStatus());
        if (!PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(lifecycle)) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_LIFECYCLE_NOT_ELIGIBLE.name());
            outcomes.put("policyApplicability", CanonicalResolutionFailureCode.POLICY_LIFECYCLE_NOT_ELIGIBLE.name());
            provenance.put("policyApplicability", "LIFECYCLE_" + lifecycle);
            return applicability;
        }
        outcomes.put("policyApplicability", "POLICY_APPLICABILITY_RESOLVED");
        provenance.put("policyApplicability", "APPLICATION_PIN");
        provenance.put("canonicalLifecycle", lifecycle);
        return applicability;
    }

    private CiPolicyDocument resolveDocument(
            LoanApplication app,
            CustomerCategoryEntity category,
            CiPolicyApplicability applicability,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        UUID pinnedDoc = app.getSelectedPolicyDocumentId();
        if (pinnedDoc == null && applicability != null) {
            pinnedDoc = applicability.getPolicyDocumentId();
        }
        if (pinnedDoc == null) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_PINNED.name());
            outcomes.put("policyDocument", CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_PINNED.name());
            provenance.put("policyDocument", "NOT_PINNED");
            return null;
        }
        Optional<CiPolicyDocument> found = policyDocumentRepository.findById(pinnedDoc);
        if (found.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_FOUND.name());
            outcomes.put("policyDocument", CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_FOUND.name());
            provenance.put("policyDocument", "PINNED_BUT_NOT_FOUND");
            return null;
        }
        CiPolicyDocument document = found.get();
        boolean drift = false;
        if (applicability != null && applicability.getPolicyDocumentId() != null
                && !applicability.getPolicyDocumentId().equals(document.getId())) {
            drift = true;
        }
        if (category != null && category.getPolicyDocumentId() != null
                && !category.getPolicyDocumentId().equals(document.getId())) {
            drift = true;
        }
        if (app.getSelectedPolicyDocumentId() != null
                && !app.getSelectedPolicyDocumentId().equals(document.getId())) {
            drift = true;
        }
        if (drift) {
            reasons.add(CanonicalResolutionFailureCode.POLICY_DOCUMENT_IDENTITY_MISMATCH.name());
            outcomes.put("policyDocument", CanonicalResolutionFailureCode.POLICY_DOCUMENT_IDENTITY_MISMATCH.name());
            provenance.put("policyDocument", "IDENTITY_MISMATCH");
            return document;
        }
        outcomes.put("policyDocument", "POLICY_DOCUMENT_RESOLVED");
        provenance.put("policyDocument", "APPLICABILITY_AND_APPLICATION_PIN");
        provenance.put("policyDocumentId", document.getId().toString());
        provenance.put("scorecardLinkageAuthority", PolicyVersionScorecardLinkage.AUTHORITY);
        return document;
    }

    private ScorecardPin resolveScorecard(
            CiPolicyDocument document,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        if (document == null) {
            outcomes.put("scorecard", "POLICY_DOCUMENT_UNAVAILABLE");
            provenance.put("scorecard", "SKIPPED_NO_DOCUMENT");
            return new ScorecardPin(null, null, false, false, null);
        }
        UUID canonicalFk = PolicyVersionScorecardLinkage.canonicalScorecardId(document.getScorecardId());
        if (canonicalFk == null) {
            outcomes.put("scorecard", SCORECARD_EXPLICITLY_ABSENT);
            provenance.put("scorecard", SCORECARD_EXPLICITLY_ABSENT);
            return new ScorecardPin(null, null, false, true, null);
        }
        Optional<UnderwritingScorecard> found = scorecardRepository.findById(canonicalFk);
        if (found.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.SCORECARD_VERSION_NOT_RESOLVABLE.name());
            outcomes.put("scorecard", CanonicalResolutionFailureCode.SCORECARD_VERSION_NOT_RESOLVABLE.name());
            provenance.put("scorecard", "BROKEN_DOCUMENT_FK");
            return new ScorecardPin(canonicalFk, null, true, false, null);
        }
        UnderwritingScorecard row = found.get();
        outcomes.put("scorecard", "SCORECARD_VERSION_RESOLVED");
        provenance.put("scorecard", PolicyVersionScorecardLinkage.AUTHORITY);
        provenance.put("scorecardId", row.getId().toString());
        return new ScorecardPin(row.getId(), row.getVersion(), true, false, row);
    }

    private LocalDate resolveEvaluationAsOf(
            LoanApplication app,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        LocalDate asOf = ApplicationPolicyQueryFactory.resolveEvaluationBusinessDate(app, null);
        if (asOf == null) {
            reasons.add(CanonicalResolutionFailureCode.EVALUATION_AS_OF_NOT_RESOLVABLE.name());
            outcomes.put("evaluationAsOf", CanonicalResolutionFailureCode.EVALUATION_AS_OF_NOT_RESOLVABLE.name());
            provenance.put("evaluationAsOf", "NOT_RESOLVABLE");
            return null;
        }
        outcomes.put("evaluationAsOf", "EVALUATION_AS_OF_PINNED");
        provenance.put("evaluationAsOf", "submittedAt>createdAt Asia/Kolkata; wall-clock now forbidden");
        provenance.put("evaluationAsOfValue", asOf.toString());
        return asOf;
    }

    private List<CanonicalCalculationPin> resolveCalculationPins(
            CiPolicyDocument document,
            UnderwritingScorecard scorecard,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        Set<String> participating = new LinkedHashSet<>();
        if (document != null) {
            Optional<CiPolicyRuleGraph> graph = ruleGraphRepository
                    .findByPolicyDocumentIdAndDocumentVersion(document.getId(), document.getDocumentVersion());
            graph.ifPresent(g -> {
                Map<UUID, CiPolicyRuleGraphNode> nodes = new LinkedHashMap<>();
                for (CiPolicyRuleGraphNode n : ruleGraphNodeRepository.findByGraphIdOrderBySortOrderAsc(g.getId())) {
                    if (n.getId() != null) {
                        nodes.put(n.getId(), n);
                    }
                }
                for (CiPolicyRuleGraphOperand op : operandRepository.findByGraphId(g.getId())) {
                    if (op.getCanonicalParameterId() == null || op.getCanonicalParameterId().isBlank()) {
                        continue;
                    }
                    CiPolicyRuleGraphNode node = op.getNodeId() == null ? null : nodes.get(op.getNodeId());
                    if (node != null
                            && !PolicyExecutionReadiness.isCanonicalFreezeParticipatingMetadata(node.getMetadata())) {
                        continue;
                    }
                    participating.add(op.getCanonicalParameterId().trim());
                }
            });
        }
        if (scorecard != null) {
            participating.addAll(extractCanonicalIds(scorecard.getScorecardJson()));
            participating.addAll(extractCanonicalIds(scorecard.getHardRulesJson()));
            participating.addAll(extractCanonicalIds(scorecard.getThresholdsJson()));
        }
        List<CanonicalCalculationPin> pins = new ArrayList<>();
        for (String parameterId : participating) {
            CanonicalCalculationPin pin = pinForParameter(parameterId, reasons);
            if (pin != null) {
                pins.add(pin);
            }
        }
        outcomes.put("calculationPins", String.valueOf(pins.size()));
        provenance.put("calculationPinCount", String.valueOf(pins.size()));
        provenance.put("calculationPinRule",
                "participating graph operands + scorecard ids; unique non-retired definition or built-in; never latestFor; deferred SOURCE_NOT_PROVEN excluded");
        return List.copyOf(pins);
    }

    private CanonicalCalculationPin pinForParameter(String parameterId, List<String> reasons) {
        if (PolicyExecutionReadiness.isSourceNotProven(parameterId)) {
            return new CanonicalCalculationPin(
                    parameterId,
                    CanonicalCalculationPin.SOURCE_NOT_PROVEN,
                    "GACAT_SOURCE_NOT_PROVEN",
                    null,
                    null,
                    false,
                    false);
        }
        Optional<CanonicalParameterDefinition> defOpt = parameterLookup.apply(parameterId);
        String gacatType = defOpt.map(CanonicalParameterDefinition::type).orElse(null);
        boolean builtIn = BuiltInBureauMetricProducer.EMITTED_IDS.contains(parameterId);
        List<CiGacatDerivedCalculationDefinition> rows = calculationDefinitionRepository
                .findByCanonicalParameterId(parameterId);
        List<CiGacatDerivedCalculationDefinition> live = new ArrayList<>();
        for (CiGacatDerivedCalculationDefinition row : rows) {
            String st = row.getStatus() == null ? "" : row.getStatus();
            if (!DerivedCalculationDefinitionService.STATUS_RETIRED.equalsIgnoreCase(st)) {
                live.add(row);
            }
        }
        if (live.size() > 1) {
            reasons.add(CanonicalResolutionFailureCode.CALCULATION_DEFINITION_AMBIGUOUS.name());
            return new CanonicalCalculationPin(
                    parameterId,
                    firstNonBlank(first(live).getCalculationType(), CanonicalCalculationPin.AUTHORED_EXPRESSION),
                    "AMBIGUOUS_DEFINITION_ROWS",
                    null,
                    null,
                    false,
                    true);
        }
        if (live.size() == 1) {
            CiGacatDerivedCalculationDefinition row = live.get(0);
            String calcType = firstNonBlank(row.getCalculationType(),
                    builtIn ? CanonicalCalculationPin.BUILT_IN_CODE : CanonicalCalculationPin.AUTHORED_EXPRESSION);
            String authority = CanonicalCalculationPin.BUILT_IN_CODE.equalsIgnoreCase(calcType)
                    ? BuiltInBureauMetricProducer.PRODUCER_ID
                    : "CiGacatDerivedCalculationDefinition:" + row.getId();
            return new CanonicalCalculationPin(
                    parameterId,
                    calcType,
                    authority,
                    row.getId(),
                    row.getVersionNo(),
                    true,
                    false);
        }
        if (builtIn) {
            return new CanonicalCalculationPin(
                    parameterId,
                    CanonicalCalculationPin.BUILT_IN_CODE,
                    BuiltInBureauMetricProducer.PRODUCER_ID,
                    null,
                    1,
                    true,
                    false);
        }
        if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(gacatType)) {
            reasons.add(CanonicalResolutionFailureCode.AUTHORED_CALCULATION_NOT_PINNED.name());
            return new CanonicalCalculationPin(
                    parameterId,
                    CanonicalCalculationPin.AUTHORED_EXPRESSION,
                    "MISSING_DEFINITION_ROW",
                    null,
                    null,
                    false,
                    true);
        }
        if (CanonicalParameterDefinition.RAW.equalsIgnoreCase(gacatType)
                || parameterId.startsWith("bureau.")) {
            return new CanonicalCalculationPin(
                    parameterId,
                    CanonicalCalculationPin.PROVIDER_DERIVED,
                    "ci_bureau_report+parser+normalizer",
                    null,
                    null,
                    true,
                    false);
        }
        return new CanonicalCalculationPin(
                parameterId,
                firstNonBlank(gacatType, CanonicalCalculationPin.RAW),
                "GACAT_PARAMETER",
                null,
                null,
                true,
                false);
    }

    private BureauPin resolveBureau(
            LoanApplication app,
            WorkflowConfig workflow,
            List<CanonicalCalculationPin> calcPins,
            List<String> reasons,
            Map<String, String> outcomes,
            Map<String, String> provenance) {
        boolean bureauRequired = workflow != null && workflow.isBureauEnabled();
        if (!bureauRequired) {
            for (CanonicalCalculationPin pin : calcPins) {
                if (pin.parameterId() != null && pin.parameterId().startsWith("bureau.")) {
                    bureauRequired = true;
                    break;
                }
                if (CanonicalCalculationPin.BUILT_IN_CODE.equals(pin.calculationType())
                        || CanonicalCalculationPin.PROVIDER_DERIVED.equals(pin.calculationType())) {
                    bureauRequired = true;
                    break;
                }
            }
        }
        List<CiBureauReport> reports = bureauReportRepository.findByApplicationId(app.getId());
        if (!bureauRequired) {
            if (reports != null && reports.size() == 1) {
                CiBureauReport only = reports.get(0);
                outcomes.put("bureau", BUREAU_REPORT_PINNED);
                provenance.put("bureau", "SINGLE_REPORT");
                return new BureauPin(only.getId(), only.getProviderCode(), only.getParserVersion(),
                        only.getNormalizerVersion());
            }
            outcomes.put("bureau", BUREAU_NOT_REQUIRED);
            provenance.put("bureau", BUREAU_NOT_REQUIRED);
            return new BureauPin(null, null, null, null);
        }
        if (reports == null || reports.isEmpty()) {
            reasons.add(CanonicalResolutionFailureCode.BUREAU_REPORT_NOT_PINNED.name());
            outcomes.put("bureau", CanonicalResolutionFailureCode.BUREAU_REPORT_NOT_PINNED.name());
            provenance.put("bureau", "REQUIRED_BUT_ABSENT");
            return new BureauPin(null, null, null, null);
        }
        if (reports.size() > 1) {
            reasons.add(CanonicalResolutionFailureCode.BUREAU_REPORT_AMBIGUOUS.name());
            outcomes.put("bureau", CanonicalResolutionFailureCode.BUREAU_REPORT_AMBIGUOUS.name());
            provenance.put("bureau", "MULTIPLE_REPORTS_NO_PIN");
            return new BureauPin(null, null, null, null);
        }
        CiBureauReport only = reports.get(0);
        outcomes.put("bureau", BUREAU_REPORT_PINNED);
        provenance.put("bureau", "SINGLE_REPORT");
        provenance.put("bureauReportId", only.getId().toString());
        return new BureauPin(only.getId(), only.getProviderCode(), only.getParserVersion(),
                only.getNormalizerVersion());
    }

    @SuppressWarnings("unchecked")
    static Set<String> extractCanonicalIds(Object json) {
        Set<String> ids = new LinkedHashSet<>();
        walkJson(json, ids);
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static void walkJson(Object json, Set<String> ids) {
        if (json instanceof Map<?, ?> map) {
            Object direct = map.get("canonicalParameterId");
            if (direct != null && !String.valueOf(direct).isBlank()) {
                ids.add(String.valueOf(direct).trim());
            }
            for (Object v : map.values()) {
                walkJson(v, ids);
            }
        } else if (json instanceof List<?> list) {
            for (Object v : list) {
                walkJson(v, ids);
            }
        }
    }

    private static CanonicalApplicationConfigurationResolution fail(
            CanonicalApplicationConfiguration cfg,
            List<String> reasons,
            Map<String, String> outcomes,
            Instant ts) {
        return new CanonicalApplicationConfigurationResolution(
                CanonicalResolutionStatus.NOT_RESOLVABLE, cfg, unique(reasons), outcomes);
    }

    private static List<String> unique(List<String> in) {
        return List.copyOf(new LinkedHashSet<>(in));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static CiGacatDerivedCalculationDefinition first(List<CiGacatDerivedCalculationDefinition> live) {
        return live.get(0);
    }

    private record ScorecardPin(
            UUID id,
            Integer version,
            boolean required,
            boolean explicitlyAbsent,
            UnderwritingScorecard row) {}

    private record BureauPin(
            UUID id,
            String providerCode,
            String parserVersion,
            String normalizerVersion) {}
}
