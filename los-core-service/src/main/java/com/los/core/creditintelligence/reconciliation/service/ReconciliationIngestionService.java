package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.repository.CiCreditEvidenceSummaryRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Flag-gated reconciliation ingestion. Never throws into production path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationIngestionService {

    private final CreditIntelligenceProperties properties;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ReconciliationOrchestrator orchestrator;
    private final CiReconciliationResultRepository resultRepository;
    private final CiCreditEvidenceSummaryRepository evidenceSummaryRepository;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Reconciliation cfg = properties.getReconciliation();
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        UUID tenantId = properties.getDefaultTenantId();
        List<String> tenantIds = cfg.getTenantIds();
        if (tenantIds != null && !tenantIds.isEmpty()) {
            String tid = tenantId != null ? tenantId.toString() : "";
            boolean match = tenantIds.stream().anyMatch(t -> t != null && t.equalsIgnoreCase(tid));
            if (!match) {
                return false;
            }
        }
        List<String> productCodes = cfg.getProductCodes();
        if (productCodes != null && !productCodes.isEmpty()) {
            String product = app != null && app.getLoanProduct() != null ? app.getLoanProduct() : "";
            boolean match = productCodes.stream()
                    .anyMatch(p -> p != null && p.equalsIgnoreCase(product));
            if (!match) {
                return false;
            }
        }
        return true;
    }

    public boolean isEnabledFor(UUID applicationId) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            return app != null && isEnabledFor(app);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensure reconciliations have been run. Never throws.
     */
    public Optional<ReconciliationOrchestrator.OrchestrationResult> ensureRan(
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            Set<String> changedMetricCodes) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app == null || !isEnabledFor(app)) {
                return Optional.empty();
            }
            List<CiReconciliationResult> existing =
                    resultRepository.findByApplicationIdOrderByExecutedAtDesc(applicationId);
            if ((changedMetricCodes == null || changedMetricCodes.isEmpty())
                    && existing != null && !existing.isEmpty()) {
                CiCreditEvidenceSummary summary = evidenceSummaryRepository
                        .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                        .orElse(null);
                return Optional.of(new ReconciliationOrchestrator.OrchestrationResult(
                        existing, summary, null));
            }
            return Optional.of(orchestrator.run(
                    properties.getDefaultTenantId(),
                    applicationId,
                    factSnapshotId,
                    evaluationId,
                    changedMetricCodes != null ? changedMetricCodes : Set.of(),
                    LocalDate.now()));
        } catch (Exception e) {
            log.warn("Reconciliation ensureRan failed (non-fatal) for {}: {}",
                    applicationId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ReconciliationOrchestrator.OrchestrationResult> runForShadow(
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId) {
        try {
            if (!isEnabledFor(applicationId)) {
                return Optional.empty();
            }
            return Optional.of(orchestrator.run(
                    properties.getDefaultTenantId(),
                    applicationId,
                    factSnapshotId,
                    evaluationId,
                    new HashSet<>(),
                    LocalDate.now()));
        } catch (Exception e) {
            log.warn("Reconciliation shadow run failed (non-fatal) for {}: {}",
                    applicationId, e.getMessage());
            return Optional.empty();
        }
    }
}
