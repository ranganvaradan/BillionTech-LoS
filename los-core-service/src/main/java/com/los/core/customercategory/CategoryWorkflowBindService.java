package com.los.core.customercategory;

import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.workflow.WorkflowContentHash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W2 — Category → exact Workflow Version bind (configuration / governance only).
 * Does not mutate application.workflow_id, KYC routing, or live UW.
 * At most one Workflow Version per Customer Category Version.
 * Multiple Categories may share the same Workflow Version.
 */
@Service
@RequiredArgsConstructor
public class CategoryWorkflowBindService {

    public static final String LINKAGE_REQUIRED = "WORKFLOW_LINKAGE_REQUIRED";
    public static final String WORKFLOW_NOT_FOUND = "WORKFLOW_NOT_FOUND";
    public static final String WORKFLOW_VERSION_INVALID = "WORKFLOW_VERSION_INVALID";
    public static final String WORKFLOW_VERSION_MISMATCH = "WORKFLOW_VERSION_MISMATCH";
    public static final String WORKFLOW_VERSION_MUTATED = "WORKFLOW_VERSION_MUTATED";
    public static final String WORKFLOW_NOT_ELIGIBLE = "WORKFLOW_NOT_ELIGIBLE";

    private final WorkflowConfigRepository workflowConfigRepository;

    @Transactional(readOnly = true)
    public WorkflowConfig requireWorkflow(UUID workflowId) {
        if (workflowId == null) {
            throw CustomerCategoryValidator.biz("workflowId required",
                    "WORKFLOW_ID_REQUIRED", Map.of());
        }
        return workflowConfigRepository.findById(workflowId)
                .orElseThrow(() -> CustomerCategoryValidator.biz(
                        "Workflow Version not found: " + workflowId,
                        WORKFLOW_NOT_FOUND,
                        Map.of("workflowId", workflowId.toString())));
    }

    /**
     * Resolve exact Workflow Version. Optional workflowVersion must match when supplied.
     */
    @Transactional(readOnly = true)
    public ResolvedWorkflowBind resolveBind(UUID workflowId, Integer workflowVersion) {
        WorkflowConfig cfg = requireWorkflow(workflowId);
        if (workflowVersion != null && cfg.getVersion() != workflowVersion) {
            throw CustomerCategoryValidator.biz(
                    "workflowVersion does not match selected Workflow Version",
                    WORKFLOW_VERSION_MISMATCH,
                    Map.of(
                            "workflowVersion", workflowVersion,
                            "expected", cfg.getVersion(),
                            "workflowId", workflowId.toString()));
        }
        if (!cfg.isActive()) {
            throw CustomerCategoryValidator.biz(
                    "Workflow Version is not active/governed for Category bind",
                    WORKFLOW_NOT_ELIGIBLE,
                    Map.of("workflowId", workflowId.toString(), "active", false));
        }
        String hash = WorkflowContentHash.of(cfg);
        return new ResolvedWorkflowBind(
                cfg.getId(),
                cfg.getVersion(),
                hash,
                cfg.getName(),
                cfg.isActive());
    }

    public void applyBind(CustomerCategoryEntity e, ResolvedWorkflowBind bind) {
        e.setWorkflowId(bind.workflowId());
        e.setWorkflowVersion(bind.workflowVersion());
        e.setWorkflowContentHash(bind.contentHash());
        e.setWorkflowName(bind.workflowName());
    }

    public void clearBind(CustomerCategoryEntity e) {
        e.setWorkflowId(null);
        e.setWorkflowVersion(null);
        e.setWorkflowContentHash(null);
        e.setWorkflowName(null);
    }

    public static String linkageStatus(CustomerCategoryEntity e) {
        if (e.getWorkflowId() != null
                && e.getWorkflowVersion() != null
                && e.getWorkflowContentHash() != null
                && !e.getWorkflowContentHash().isBlank()) {
            return "LINKED";
        }
        return LINKAGE_REQUIRED;
    }

    public void requireCompatible(CustomerCategoryEntity category, UUID workflowId) {
        WorkflowConfig cfg = requireWorkflow(workflowId);
        CategoryWorkflowCompatibility.Result r = CategoryWorkflowCompatibility.evaluate(category, cfg);
        if (!r.compatible()) {
            throw CustomerCategoryValidator.biz(
                    "Workflow is incompatible with Customer Category: " + String.join(", ", r.reasons()),
                    CategoryWorkflowCompatibility.WORKFLOW_SCOPE_INCOMPATIBLE,
                    Map.of(
                            "workflowId", workflowId.toString(),
                            "reasons", r.reasons(),
                            "status", r.status()));
        }
    }

    public void requireCompatible(
            String customerRole,
            String entityType,
            String loanProduct,
            UUID workflowId) {
        WorkflowConfig cfg = requireWorkflow(workflowId);
        CategoryWorkflowCompatibility.Result r =
                CategoryWorkflowCompatibility.evaluate(customerRole, entityType, loanProduct, cfg);
        if (!r.compatible()) {
            throw CustomerCategoryValidator.biz(
                    "Workflow is incompatible with Customer Category: " + String.join(", ", r.reasons()),
                    CategoryWorkflowCompatibility.WORKFLOW_SCOPE_INCOMPATIBLE,
                    Map.of(
                            "workflowId", workflowId.toString(),
                            "reasons", r.reasons(),
                            "status", r.status()));
        }
    }

    /**
     * Workflow picker — all Workflow Versions (not priority-matched).
     * Incompatible rows remain visible.
     */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.EligibleWorkflowView> listEligibleWorkflows(
            String entityType,
            String loanProduct,
            String customerRole) {
        List<WorkflowConfig> all = workflowConfigRepository.findAll();
        List<CustomerCategoryDtos.EligibleWorkflowView> out = new ArrayList<>();
        for (WorkflowConfig cfg : all) {
            CategoryWorkflowCompatibility.Result compat =
                    CategoryWorkflowCompatibility.evaluate(customerRole, entityType, loanProduct, cfg);
            List<String> notes = new ArrayList<>();
            for (var c : compat.checks()) {
                if (!c.ok()) {
                    notes.add(c.label() + ": " + c.detail());
                }
            }
            if (compat.compatible()) {
                notes.add(0, "Compatible — Workflow can process this Category proposition");
            }
            out.add(new CustomerCategoryDtos.EligibleWorkflowView(
                    cfg.getId(),
                    cfg.getName(),
                    cfg.getVersion(),
                    cfg.isActive(),
                    cfg.getBorrowerType(),
                    cfg.getIntakeSegment(),
                    cfg.getLoanProduct(),
                    stepSummary(cfg),
                    WorkflowContentHash.of(cfg),
                    compat.compatible(),
                    compat.status(),
                    List.copyOf(compat.reasons()),
                    notes,
                    compat.scopeSummary(),
                    cfg.resolvedFamilyId(),
                    cfg.resolvedPublicationStatus(),
                    "ACTIVE".equals(cfg.resolvedPublicationStatus())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.ActivationCheck> workflowActivationChecks(CustomerCategoryEntity e) {
        List<CustomerCategoryDtos.ActivationCheck> checks = new ArrayList<>();
        String linkage = linkageStatus(e);
        boolean linked = "LINKED".equals(linkage);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "WORKFLOW_SELECTED",
                "Workflow Version selected",
                linked,
                linked ? "Linked to exact Workflow Version"
                        : "WORKFLOW LINKAGE REQUIRED — select a Workflow Version for this Category"));

        if (!linked) {
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_VERSION_EXISTS",
                    "Workflow Version exists",
                    false,
                    "No workflowId"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_ELIGIBLE",
                    "Workflow eligible/governed for use",
                    false,
                    "Skipped — no Workflow link"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_APPLICABILITY_COMPATIBLE",
                    "Workflow applicability compatible with Category",
                    false,
                    "Skipped — no Workflow link"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_CONTENT_IDENTITY_VALID",
                    "Workflow content/version identity valid",
                    false,
                    "Skipped — no Workflow link"));
            return checks;
        }

        WorkflowConfig cfg = workflowConfigRepository.findById(e.getWorkflowId()).orElse(null);
        boolean exists = cfg != null;
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "WORKFLOW_VERSION_EXISTS",
                "Workflow Version exists",
                exists,
                exists ? cfg.getName() + " / v" + cfg.getVersion() + " id=" + cfg.getId() : "workflow_configs row missing"));

        if (cfg == null) {
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_ELIGIBLE", "Workflow eligible/governed for use", false, "Missing"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_APPLICABILITY_COMPATIBLE",
                    "Workflow applicability compatible with Category", false, "Missing"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "WORKFLOW_CONTENT_IDENTITY_VALID",
                    "Workflow content/version identity valid", false, "Missing"));
            return checks;
        }

        boolean eligible = "ACTIVE".equals(cfg.resolvedPublicationStatus())
                || cfg.isActive();
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "WORKFLOW_ELIGIBLE",
                "Workflow eligible/governed for use (published version)",
                eligible,
                eligible
                        ? ("publication=" + cfg.resolvedPublicationStatus() + " version=" + cfg.getVersion())
                        : "WORKFLOW_NOT_ELIGIBLE — " + cfg.resolvedPublicationStatus()));

        CategoryWorkflowCompatibility.Result scope = CategoryWorkflowCompatibility.evaluate(e, cfg);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "WORKFLOW_APPLICABILITY_COMPATIBLE",
                "Workflow applicability compatible with Category",
                scope.compatible(),
                scope.compatible()
                        ? scope.scopeSummary()
                        : scope.status() + ": " + String.join(", ", scope.reasons())));

        boolean versionMatch = e.getWorkflowVersion() != null
                && e.getWorkflowVersion() == cfg.getVersion();
        String currentHash = WorkflowContentHash.of(cfg);
        boolean hashMatch = e.getWorkflowContentHash() != null
                && e.getWorkflowContentHash().equalsIgnoreCase(currentHash);
        boolean identityOk = versionMatch && hashMatch;
        String detail;
        if (!versionMatch) {
            detail = WORKFLOW_VERSION_INVALID + " — bound version row is v" + cfg.getVersion()
                    + " but category pin is v" + e.getWorkflowVersion()
                    + " (exact id=" + cfg.getId() + "; siblings are not compared)";
        } else if (!hashMatch) {
            detail = WORKFLOW_VERSION_MUTATED
                    + " — Workflow content changed after Category linked (P1: immutable Workflow versions)";
        } else {
            detail = "exactVersionId=" + cfg.getId() + " version=" + cfg.getVersion() + " hash=OK";
        }
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "WORKFLOW_CONTENT_IDENTITY_VALID",
                "Workflow content/version identity valid (no unexpected mutation)",
                identityOk,
                detail));
        if (!hashMatch && versionMatch) {
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    WORKFLOW_VERSION_MUTATED,
                    "Workflow mutated since Category bind",
                    false,
                    "Stored content hash no longer matches workflow_configs (P1 remains)"));
        }

        return checks;
    }

    private static String stepSummary(WorkflowConfig cfg) {
        if (cfg.getSteps() == null || cfg.getSteps().isEmpty()) {
            return "No steps configured";
        }
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> step : cfg.getSteps()) {
            Object k = step.get("stepKey");
            if (k == null) k = step.get("key");
            if (k == null) k = step.get("name");
            if (k != null) keys.add(String.valueOf(k));
            if (keys.size() >= 8) break;
        }
        String joined = String.join(" → ", keys);
        if (cfg.getSteps().size() > keys.size()) {
            joined = joined + " … (+" + (cfg.getSteps().size() - keys.size()) + ")";
        }
        return joined.isBlank() ? (cfg.getSteps().size() + " steps") : joined;
    }

    public record ResolvedWorkflowBind(
            UUID workflowId,
            int workflowVersion,
            String contentHash,
            String workflowName,
            boolean active
    ) {}
}
