package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * W1 — consume the application's persisted Workflow Version pin.
 *
 * <p>Cutover discriminator: persisted {@code workflow_id}. If present, bind that exact
 * Version (historical / Category / admin-explicit). If absent, fail closed —
 * never discover or persist a product default for a new application.
 *
 * <p>Read paths must not persist configuration. {@link #requireConfig} is read-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationWorkflowResolver {

    /** Mirrors {@link com.los.core.customercategory.CategoryWorkflowBindService#WORKFLOW_VERSION_MUTATED}. */
    public static final String WORKFLOW_VERSION_MUTATED = "WORKFLOW_VERSION_MUTATED";

    private final LoanApplicationRepository loanApplicationRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ResolvedWorkflowVersion resolveForApplication(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Application not found: " + applicationId,
                        "APPLICATION_NOT_FOUND",
                        "RESOLVE_WORKFLOW",
                        Map.of("applicationId", applicationId.toString())));
        return resolveForApplication(app);
    }

    /**
     * Bind the persisted pin only. Does not discover DEFAULT. Does not persist.
     */
    @Transactional(readOnly = true)
    public ResolvedWorkflowVersion resolveForApplication(LoanApplication app) {
        if (app == null || app.getId() == null) {
            throw new BusinessRuleException(
                    "Application required to resolve Workflow",
                    "WORKFLOW_NOT_RESOLVED",
                    "RESOLVE_WORKFLOW",
                    Map.of());
        }

        if (app.getWorkflowId() != null) {
            return bindExisting(app);
        }

        throw ApplicationConfigurationAuthority.notPinned(app);
    }

    /** Require pinned config; never discovers a default. */
    @Transactional(readOnly = true)
    public WorkflowConfig requireConfig(LoanApplication app) {
        return resolveForApplication(app).config();
    }

    /**
     * Like {@link #resolveForApplication(LoanApplication)}, but fails closed (P1) instead of
     * merely logging when the pinned Workflow Version's content has mutated since resolve —
     * i.e. someone edited a supposedly-immutable published Workflow Version row in place.
     * Use this at execution/progression boundaries (running or advancing a flow step); plain
     * display/read paths should keep using {@link #resolveForApplication(LoanApplication)} so a
     * mutated-but-inert application can still be inspected.
     */
    @Transactional(readOnly = true)
    public ResolvedWorkflowVersion resolveForExecution(LoanApplication app) {
        ResolvedWorkflowVersion resolved = resolveForApplication(app);
        if (resolved.definitionMutatedSinceResolve()) {
            throw new BusinessRuleException(
                    "Pinned Workflow Version content changed after Category linked it "
                            + "(P1: immutable Workflow versions) — cannot execute against a mutated definition",
                    WORKFLOW_VERSION_MUTATED,
                    "EXECUTE_WORKFLOW_STEP",
                    Map.of(
                            "applicationId", app.getId().toString(),
                            "workflowId", resolved.workflowId().toString(),
                            "storedContentHash", resolved.contentHash()));
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowConfig> findPinnedConfig(LoanApplication app) {
        if (app == null || app.getWorkflowId() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(bindExisting(app).config());
        } catch (BusinessRuleException e) {
            if (ApplicationConfigurationAuthority.isUnconfiguredReason(e.getReason())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Stamp EXPLICIT resolution for controlled ADMIN / MIGRATION / TEST create only.
     * Does not discover DEFAULT. Caller must enforce Option B role gate.
     */
    @Transactional
    public void stampExplicit(LoanApplication app, WorkflowConfig cfg) {
        if (app == null || cfg == null || cfg.getId() == null) {
            return;
        }
        app.setWorkflowId(cfg.getId());
        app.setWorkflowVersion(cfg.getVersion());
        app.setWorkflowResolutionSource(WorkflowResolutionSource.EXPLICIT.name());
        Instant when = Instant.now();
        app.setWorkflowResolvedAt(when);
        app.setWorkflowContentHash(WorkflowContentHash.of(cfg));
        loanApplicationRepository.save(app);
        auditResolution(app, cfg, WorkflowResolutionSource.EXPLICIT, when, false);
    }

    private ResolvedWorkflowVersion bindExisting(LoanApplication app) {
        UUID id = app.getWorkflowId();
        WorkflowConfig cfg = workflowConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException(
                        "Persisted Workflow Version not found: " + id,
                        "WORKFLOW_VERSION_NOT_FOUND",
                        "RESOLVE_WORKFLOW",
                        Map.of(
                                "applicationId", app.getId().toString(),
                                "workflowId", id.toString())));

        WorkflowResolutionSource source = parseSource(app.getWorkflowResolutionSource());
        if (source == null) {
            source = WorkflowResolutionSource.LEGACY_EXISTING;
        }

        Instant resolvedAt = app.getWorkflowResolvedAt() != null ? app.getWorkflowResolvedAt() : Instant.now();
        String storedHash = app.getWorkflowContentHash();
        String currentHash = WorkflowContentHash.of(cfg);
        boolean mutated = storedHash != null && !storedHash.isBlank() && !storedHash.equals(currentHash);
        if (mutated) {
            log.warn("W1 workflow definition mutated after resolve applicationId={} workflowId={} storedHash={} currentHash={}",
                    app.getId(), id, storedHash, currentHash);
        }

        int version = app.getWorkflowVersion() != null ? app.getWorkflowVersion() : cfg.getVersion();
        return new ResolvedWorkflowVersion(
                cfg.getId(),
                version,
                source,
                resolvedAt,
                storedHash != null && !storedHash.isBlank() ? storedHash : currentHash,
                mutated,
                cfg);
    }

    private static WorkflowResolutionSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return WorkflowResolutionSource.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return WorkflowResolutionSource.LEGACY_EXISTING;
        }
    }

    private void auditResolution(
            LoanApplication app,
            WorkflowConfig cfg,
            WorkflowResolutionSource source,
            Instant when,
            boolean mutated) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("applicationId", app.getId().toString());
            evidence.put("workflowId", cfg.getId().toString());
            evidence.put("workflowVersion", cfg.getVersion());
            evidence.put("resolutionSource", source.name());
            evidence.put("resolvedAt", when.toString());
            evidence.put("contentHash", app.getWorkflowContentHash());
            evidence.put("definitionMutatedSinceResolve", mutated);
            auditService.logEvent(
                    app.getId(),
                    "WORKFLOW",
                    "RESOLVED",
                    null,
                    null,
                    evidence,
                    "W1 Workflow Version resolved");
        } catch (Exception e) {
            log.debug("W1 workflow resolve audit skipped: {}", e.getMessage());
        }
    }
}
