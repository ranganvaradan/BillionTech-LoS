package com.los.core.creditintelligence.tax.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
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
 * Entry point for ITR/AIS/26AS canonicalization after successful ITR return-forms pull.
 * Gated by credit-intelligence.canonicalization.tax flags. Never throws to caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxIngestionService {

    private final TaxNormalizationService normalizationService;
    private final CiItrReturnRepository returnRepository;
    private final CreditIntelligenceProperties properties;
    private final KycStepResultRepository kycStepResultRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Canonicalization.Tax cfg =
                properties.getCanonicalization().getTax();
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
     * Ingest after ITR_RETURN_FORMS SUCCESS. Never throws.
     */
    public void ingestFromItrStep(
            LoanApplication app,
            Map<String, Object> parsedData,
            UUID kycStepResultId,
            String requestId) {
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
            log.info("Tax canonicalization ingested for application {} requestId={}",
                    app.getId(), requestId);
        } catch (Exception e) {
            log.warn("Tax canonicalization failed (non-fatal) for application {}: {}",
                    app != null ? app.getId() : null, e.getMessage());
        }
    }

    /**
     * Lazy ensure: if flag on and no ITR returns yet, ingest from latest SUCCESS ITR_RETURN_FORMS.
     */
    public void ensureIngested(UUID applicationId) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            List<CiItrReturn> existing =
                    returnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
            if (!existing.isEmpty()) {
                return;
            }
            Optional<KycStepResult> step = findLatestSuccessfulItr(applicationId);
            if (step.isEmpty()) {
                return;
            }
            KycStepResult r = step.get();
            Map<String, Object> parsed = r.getParsedData() != null ? r.getParsedData() : Map.of();
            String requestId = r.getTransactionId();
            if (requestId == null || requestId.isBlank()) {
                Object rid = parsed.get("requestId");
                requestId = rid != null ? String.valueOf(rid) : null;
            }
            ingestFromItrStep(app, parsed, r.getId(), requestId);
        } catch (Exception e) {
            log.warn("Tax ensureIngested failed (non-fatal) for {}: {}", applicationId, e.getMessage());
        }
    }

    public Optional<CiItrReturn> findLatestEffective(UUID applicationId) {
        return returnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId)
                .stream().findFirst();
    }

    private Optional<KycStepResult> findLatestSuccessfulItr(UUID applicationId) {
        List<KycStepResult> all = kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            KycStepResult r = all.get(i);
            if (r.getStepType() != KycStepType.ITR_RETURN_FORMS) {
                continue;
            }
            if (r.getOutcome() != StepOutcome.SUCCESS && !r.isOverridden()) {
                continue;
            }
            return Optional.of(r);
        }
        return Optional.empty();
    }
}
