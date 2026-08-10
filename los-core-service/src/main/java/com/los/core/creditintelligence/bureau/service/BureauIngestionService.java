package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.model.entity.LoanApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for bureau canonicalization after a successful bureau pull.
 * Gated by credit-intelligence.canonicalization.bureau flags.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BureauIngestionService {

    private final BureauNormalizationService normalizationService;
    private final CiBureauReportRepository reportRepository;
    private final CreditIntelligenceProperties properties;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Canonicalization.Bureau cfg =
                properties.getCanonicalization().getBureau();
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
            String product = app.getLoanProduct() != null ? app.getLoanProduct() : "";
            boolean match = productCodes.stream()
                    .anyMatch(p -> p != null && p.equalsIgnoreCase(product));
            if (!match) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ingest after bureau pull. Never throws to caller — logs and swallows.
     */
    public void ingestFromPull(
            LoanApplication app,
            Map<String, Object> reportData,
            String transactionId,
            UUID kycStepResultId) {
        try {
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            normalizationService.normalize(
                    app.getId(),
                    properties.getDefaultTenantId(),
                    "EQUIFAX",
                    reportData,
                    transactionId,
                    kycStepResultId);
            log.info("Bureau canonicalization ingested for application {} txn={}",
                    app.getId(), transactionId);
        } catch (Exception e) {
            log.warn("Bureau canonicalization failed (non-fatal) for application {}: {}",
                    app != null ? app.getId() : null, e.getMessage());
        }
    }

    public Optional<CiBureauReport> findLatestReport(UUID applicationId) {
        return reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }
}
