package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only inspect of canonical configuration resolution.
 * Does not underwrite, change category/policy/workflow/scorecard, or alter live decisions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence")
@RequiredArgsConstructor
public class CanonicalApplicationConfigurationController {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerCategoryRepository customerCategoryRepository;
    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final CanonicalApplicationConfigurationResolver resolver;
    private final CanonicalApplicationConfigurationFreezeService freezeService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/canonical-configuration")
    public Map<String, Object> inspect(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        out.put("applicationNumber", app.getApplicationNumber());
        out.put("status", app.getStatus() == null ? null : app.getStatus().name());
        out.put("creditDecision", app.getCreditDecision());
        out.put("liveDecisionUnchanged", true);
        out.put("canonicalRuntimeUsedForLiveDecision", false);
        freezeService.findLatestFreeze(applicationId).ifPresentOrElse(
                frozen -> {
                    out.put("freezePresent", true);
                    out.putAll(frozen.toMap());
                },
                () -> {
                    out.put("freezePresent", false);
                    CanonicalApplicationConfigurationResolution live = resolver.resolve(app);
                    out.putAll(live.toMap());
                });
        CustomerCategoryEntity category = app.getSelectedCustomerCategoryId() == null
                ? null
                : customerCategoryRepository.findById(app.getSelectedCustomerCategoryId()).orElse(null);
        CiPolicyApplicability applicability = app.getSelectedPolicyApplicabilityId() == null
                ? null
                : applicabilityRepository.findById(app.getSelectedPolicyApplicabilityId()).orElse(null);
        WorkflowConfig workflow = app.getWorkflowId() == null
                ? null
                : workflowConfigRepository.findById(app.getWorkflowId()).orElse(null);
        out.put("existingApplicationClass", CanonicalApplicationPinClassifier.classify(
                app, category, applicability, workflow).name());
        out.putAll(CanonicalApplicationConfigurationResolver.forbiddenLookupCounts());
        return out;
    }

    /**
     * Configuration resolution preflight from a Customer Category bind.
     * Does not create an application. Does not underwrite or change live decision authority.
     */
    @GetMapping("/customer-categories/{categoryId}/canonical-configuration-preflight")
    public Map<String, Object> categoryConfigurationPreflight(
            @PathVariable UUID categoryId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CanonicalApplicationConfigurationResolution resolution =
                resolver.resolveFromCustomerCategory(categoryId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preflight", true);
        out.put("applicationCreated", false);
        out.put("liveDecisionUnchanged", true);
        out.put("canonicalRuntimeUsedForLiveDecision", false);
        out.putAll(resolution.toMap());
        out.putAll(CanonicalApplicationConfigurationResolver.forbiddenLookupCounts());
        if (resolution.configuration() != null) {
            CanonicalApplicationConfiguration cfg = resolution.configuration();
            out.put("NEW_CUSTOMER_CATEGORY_ID", cfg.customerCategoryId() == null
                    ? null : cfg.customerCategoryId().toString());
            out.put("NEW_CUSTOMER_CATEGORY_VERSION", cfg.customerCategoryVersion());
            out.put("RESOLVED_WORKFLOW_ID", cfg.workflowVersionId() == null
                    ? null : cfg.workflowVersionId().toString());
            out.put("RESOLVED_WORKFLOW_VERSION", cfg.workflowVersionNumber());
            out.put("RESOLVED_POLICY_DOCUMENT_ID", cfg.policyDocumentId() == null
                    ? null : cfg.policyDocumentId().toString());
            out.put("RESOLVED_POLICY_VERSION", firstNonBlank(cfg.policyVersionLabel(),
                    cfg.policyDocumentVersion() == null ? null : String.valueOf(cfg.policyDocumentVersion())));
            out.put("RESOLVED_POLICY_APPLICABILITY_ID", cfg.policyApplicabilityId() == null
                    ? null : cfg.policyApplicabilityId().toString());
            out.put("RESOLVED_SCORECARD_ID", cfg.scorecardId() == null
                    ? null : cfg.scorecardId().toString());
            out.put("RESOLVED_SCORECARD_VERSION", cfg.scorecardVersion());
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    /**
     * Persist the existing resolver freeze without underwriting, policy, or scorecard execution.
     */
    @PostMapping("/applications/{applicationId}/canonical-configuration/freeze")
    public Map<String, Object> freeze(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        CanonicalApplicationConfigurationResolution resolution = freezeService.freezeObservably(app);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        out.put("freezeInvoked", true);
        out.put("liveDecisionUnchanged", true);
        out.put("canonicalRuntimeUsedForLiveDecision", false);
        out.put("policyExecuted", false);
        out.put("scorecardExecuted", false);
        out.putAll(resolution.toMap());
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing canonical inspect without token (local only)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
