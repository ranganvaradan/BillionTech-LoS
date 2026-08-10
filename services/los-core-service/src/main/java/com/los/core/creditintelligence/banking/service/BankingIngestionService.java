package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.model.entity.AaConsent;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.AaConsentRepository;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for banking canonicalization after AA fetch / OCR. Never throws to caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingIngestionService {

    private final BankingNormalizationService normalizationService;
    private final CiBankAccountRepository accountRepository;
    private final CreditIntelligenceProperties properties;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AaConsentRepository aaConsentRepository;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Canonicalization.Banking cfg =
                properties.getCanonicalization().getBanking();
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
     * Ingest after AA fetchData. Never throws to caller.
     */
    public void ingestFromAaConsent(AaConsent consent, LoanApplication app) {
        try {
            if (consent == null || app == null || !isEnabledFor(app)) {
                return;
            }
            Map<String, Object> summary = consent.getFetchedDataSummary() != null
                    ? consent.getFetchedDataSummary()
                    : Map.of();
            String provider = summary.get("provider") != null
                    ? String.valueOf(summary.get("provider"))
                    : "AA";
            String consentRef = consent.getId() != null
                    ? consent.getId().toString()
                    : consent.getConsentHandle();
            String borrowerName = resolveBorrowerName(app);
            normalizationService.normalizeFromAaSummary(
                    app.getId(),
                    properties.getDefaultTenantId(),
                    summary,
                    provider,
                    consentRef,
                    borrowerName);
            log.info("Banking canonicalization ingested from AA consent {} for application {}",
                    consentRef, app.getId());
        } catch (Exception e) {
            log.warn("Banking AA canonicalization failed (non-fatal) for application {}: {}",
                    app != null ? app.getId() : null, e.getMessage());
        }
    }

    /**
     * Optional OCR bank-statement hook. Never throws.
     */
    public void ingestFromOcrExtract(LoanApplication app, Map<String, Object> extractedMap, UUID documentId) {
        try {
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            normalizationService.normalizeFromOcr(
                    app.getId(),
                    properties.getDefaultTenantId(),
                    extractedMap,
                    documentId,
                    resolveBorrowerName(app));
            log.info("Banking canonicalization ingested from OCR for application {} document {}",
                    app.getId(), documentId);
        } catch (Exception e) {
            log.warn("Banking OCR canonicalization failed (non-fatal) for application {}: {}",
                    app != null ? app.getId() : null, e.getMessage());
        }
    }

    /**
     * Lazy ensure for snapshot path — load latest DATA_FETCHED AA consent and ingest if flag on.
     */
    public void ensureIngested(UUID applicationId) {
        try {
            LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
            if (app == null || !isEnabledFor(app)) {
                return;
            }
            if (accountRepository.existsByApplicationId(applicationId)) {
                return;
            }
            Optional<AaConsent> consent = findLatestFetchedConsent(applicationId);
            if (consent.isEmpty()) {
                return;
            }
            ingestFromAaConsent(consent.get(), app);
        } catch (Exception e) {
            log.warn("Banking ensureIngested failed (non-fatal) for {}: {}", applicationId, e.getMessage());
        }
    }

    public Optional<CiBankAccount> findLatestAccount(UUID applicationId) {
        return accountRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private Optional<AaConsent> findLatestFetchedConsent(UUID applicationId) {
        List<AaConsent> all = aaConsentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        for (AaConsent c : all) {
            if ("DATA_FETCHED".equalsIgnoreCase(c.getStatus())
                    && c.getFetchedDataSummary() != null
                    && !c.getFetchedDataSummary().isEmpty()) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private String resolveBorrowerName(LoanApplication app) {
        if (app == null) {
            return null;
        }
        String fromPersonal = firstName(app.getPersonalInfo(),
                "fullName", "name", "applicantName", "borrowerName");
        if (fromPersonal != null) {
            return fromPersonal;
        }
        return firstName(app.getBusinessInfo(),
                "businessName", "legalName", "tradeName", "entityName");
    }

    private static String firstName(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object n = map.get(key);
            if (n != null && !String.valueOf(n).isBlank()) {
                return String.valueOf(n);
            }
        }
        return null;
    }
}
