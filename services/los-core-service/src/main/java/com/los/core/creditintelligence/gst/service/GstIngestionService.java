package com.los.core.creditintelligence.gst.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for GST canonicalization after successful GST analysis report.
 * Gated by credit-intelligence.canonicalization.gst flags. Never fails report generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GstIngestionService {

    private final GstNormalizationService normalizationService;
    private final CiGstRegistrationRepository registrationRepository;
    private final CreditIntelligenceProperties properties;
    private final KycStepResultRepository kycStepResultRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Canonicalization.Gst cfg =
                properties.getCanonicalization().getGst();
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
     * Ingest after GST report SUCCESS. Never throws to caller.
     */
    public void ingestFromReport(
            LoanApplication app,
            Map<String, Object> parsedData,
            String requestId,
            UUID kycStepResultId) {
        try {
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            normalizationService.normalize(
                    app.getId(),
                    properties.getDefaultTenantId(),
                    parsedData,
                    requestId,
                    kycStepResultId);
            log.info("GST canonicalization ingested for application {} requestId={}",
                    app.getId(), requestId);
        } catch (Exception e) {
            log.warn("GST canonicalization failed (non-fatal) for application {}: {}",
                    app != null ? app.getId() : null, e.getMessage());
        }
    }

    /**
     * Lazy ensure: if flag on and no registration yet, ingest from latest SUCCESS GST_ANALYSIS REPORT.
     */
    public void ensureIngested(UUID applicationId) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            if (registrationRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).isPresent()) {
                return;
            }
            Optional<KycStepResult> report = findLatestSuccessfulReport(applicationId);
            if (report.isEmpty()) {
                return;
            }
            KycStepResult step = report.get();
            Map<String, Object> parsed = step.getParsedData() != null ? step.getParsedData() : Map.of();
            String requestId = step.getTransactionId();
            if (requestId == null || requestId.isBlank()) {
                Object rid = parsed.get("requestId");
                requestId = rid != null ? String.valueOf(rid) : null;
            }
            ingestFromReport(app, parsed, requestId, step.getId());
        } catch (Exception e) {
            log.warn("GST ensureIngested failed (non-fatal) for {}: {}", applicationId, e.getMessage());
        }
    }

    public Optional<CiGstRegistration> findLatestRegistration(UUID applicationId) {
        return registrationRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private Optional<KycStepResult> findLatestSuccessfulReport(UUID applicationId) {
        List<KycStepResult> all = kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            KycStepResult r = all.get(i);
            if (r.getStepType() != KycStepType.GST_ANALYSIS) {
                continue;
            }
            if (r.getOutcome() != StepOutcome.SUCCESS && !r.isOverridden()) {
                continue;
            }
            Map<String, Object> p = r.getParsedData();
            if (p != null && "REPORT".equalsIgnoreCase(String.valueOf(p.get("phase")))) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
