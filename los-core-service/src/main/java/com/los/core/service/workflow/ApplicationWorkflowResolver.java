package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.IntakeSegment;
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
 * W1 — single authoritative Workflow Version resolver per application.
 *
 * <p>Once an application has a persisted {@code workflow_id}, no consumer may discover a
 * different active/default workflow. Missing/broken references fail closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationWorkflowResolver {

    private final LoanApplicationRepository loanApplicationRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final AuditService auditService;

    /**
     * Resolve (and persist if needed) the Workflow Version for this application.
     * Idempotent: second call returns the same persisted Version.
     */
    @Transactional
    public ResolvedWorkflowVersion resolveForApplication(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Application not found: " + applicationId,
                        "APPLICATION_NOT_FOUND",
                        "RESOLVE_WORKFLOW",
                        Map.of("applicationId", applicationId.toString())));
        return resolveForApplication(app);
    }

    @Transactional
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

        WorkflowConfig discovered = discoverDefault(app)
                .orElseThrow(() -> new BusinessRuleException(
                        "No Workflow could be resolved for this application",
                        "WORKFLOW_NOT_RESOLVED",
                        "RESOLVE_WORKFLOW",
                        Map.of(
                                "applicationId", app.getId().toString(),
                                "borrowerType", app.getBorrowerType() != null ? app.getBorrowerType().name() : "",
                                "loanProduct", app.getLoanProduct() != null ? app.getLoanProduct() : "",
                                "intakeSegment", segmentOf(app).name())));

        return persistResolution(app, discovered, WorkflowResolutionSource.DEFAULT);
    }

    /** Require config; same as resolve then return entity. */
    @Transactional
    public WorkflowConfig requireConfig(LoanApplication app) {
        return resolveForApplication(app).config();
    }

    /**
     * Stamp EXPLICIT resolution when client supplies a validated workflowId at create/update.
     * Does not discover DEFAULT — caller already validated binding.
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

        boolean needsMeta = app.getWorkflowVersion() == null
                || app.getWorkflowResolutionSource() == null
                || app.getWorkflowResolutionSource().isBlank()
                || app.getWorkflowResolvedAt() == null
                || app.getWorkflowContentHash() == null
                || app.getWorkflowContentHash().isBlank();

        WorkflowResolutionSource source = parseSource(app.getWorkflowResolutionSource());
        if (source == null) {
            source = WorkflowResolutionSource.LEGACY_EXISTING;
        }

        Instant resolvedAt = app.getWorkflowResolvedAt() != null ? app.getWorkflowResolvedAt() : Instant.now();
        String storedHash = app.getWorkflowContentHash();
        String currentHash = WorkflowContentHash.of(cfg);
        boolean mutated = storedHash != null && !storedHash.isBlank() && !storedHash.equals(currentHash);

        if (needsMeta) {
            app.setWorkflowVersion(cfg.getVersion());
            app.setWorkflowResolutionSource(source.name());
            app.setWorkflowResolvedAt(resolvedAt);
            if (storedHash == null || storedHash.isBlank()) {
                app.setWorkflowContentHash(currentHash);
                storedHash = currentHash;
                mutated = false;
            }
            loanApplicationRepository.save(app);
            auditResolution(app, cfg, source, resolvedAt, mutated);
        } else if (mutated) {
            log.warn("W1 workflow definition mutated after resolve applicationId={} workflowId={} storedHash={} currentHash={}",
                    app.getId(), id, storedHash, currentHash);
        }

        return new ResolvedWorkflowVersion(
                cfg.getId(),
                cfg.getVersion(),
                source,
                resolvedAt,
                storedHash != null ? storedHash : currentHash,
                mutated,
                cfg);
    }

    private ResolvedWorkflowVersion persistResolution(
            LoanApplication app, WorkflowConfig cfg, WorkflowResolutionSource source) {
        Instant when = Instant.now();
        String hash = WorkflowContentHash.of(cfg);
        app.setWorkflowId(cfg.getId());
        app.setWorkflowVersion(cfg.getVersion());
        app.setWorkflowResolutionSource(source.name());
        app.setWorkflowResolvedAt(when);
        app.setWorkflowContentHash(hash);
        loanApplicationRepository.save(app);
        auditResolution(app, cfg, source, when, false);
        log.info("W1 resolved workflow applicationId={} workflowId={} version={} source={}",
                app.getId(), cfg.getId(), cfg.getVersion(), source);
        return new ResolvedWorkflowVersion(cfg.getId(), cfg.getVersion(), source, when, hash, false, cfg);
    }

    private Optional<WorkflowConfig> discoverDefault(LoanApplication app) {
        if (app.getBorrowerType() == null
                || app.getLoanProduct() == null
                || app.getLoanProduct().isBlank()) {
            return Optional.empty();
        }
        IntakeSegment seg = segmentOf(app);
        return workflowConfigRepository
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        app.getBorrowerType().name(), app.getLoanProduct(), seg.name())
                .stream()
                .findFirst();
    }

    private static IntakeSegment segmentOf(LoanApplication app) {
        return app.getIntakeSegment() != null ? app.getIntakeSegment() : IntakeSegment.BORROWER;
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
