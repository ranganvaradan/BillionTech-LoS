package com.los.core.service.aa;

import com.los.core.model.entity.AaConsent;
import com.los.core.repository.AaConsentRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Account Aggregator Service — RBI NBFC-AA Directions compliance.
 * Manages consent lifecycle: create → approve → fetch data → revoke/expire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAggregatorService {

    private final AaConsentRepository aaConsentRepository;
    private final AuditService auditService;

    /**
     * Create a consent request to an Account Aggregator.
     */
    @Transactional
    public AaConsent createConsentRequest(UUID applicationId, UUID customerId,
                                           List<String> fiTypes, String aaName,
                                           Map<String, Object> purpose) {
        String consentHandle = "AA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        AaConsent consent = AaConsent.builder()
                .applicationId(applicationId)
                .customerId(customerId)
                .consentHandle(consentHandle)
                .status("PENDING")
                .fiTypes(fiTypes != null ? fiTypes : List.of("DEPOSIT", "TERM_DEPOSIT"))
                .consentStartDate(Instant.now().minus(365, ChronoUnit.DAYS))
                .consentExpiryDate(Instant.now().plus(180, ChronoUnit.DAYS))
                .fetchFrequency("ONETIME")
                .consentMode("STORE")
                .purposeInfo(purpose != null ? purpose : Map.of(
                        "code", "101",
                        "text", "Loan underwriting and credit assessment",
                        "refUri", "https://api.rebit.org.in/aa/purpose/101"
                ))
                .aaName(aaName != null ? aaName : "DEFAULT_AA")
                .build();

        consent = aaConsentRepository.save(consent);

        auditService.logEvent(applicationId, "AA_CONSENT_REQUESTED",
                Map.of("consentHandle", consentHandle, "fiTypes", String.join(",", consent.getFiTypes())));

        log.info("AA consent request created: handle={} for application={}", consentHandle, applicationId);
        return consent;
    }

    /**
     * Process consent approval callback from AA.
     */
    @Transactional
    public AaConsent approveConsent(String consentHandle, String consentId) {
        AaConsent consent = aaConsentRepository.findByConsentHandle(consentHandle)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentHandle));

        if (!"PENDING".equals(consent.getStatus())) {
            throw new RuntimeException("Consent is not in PENDING status. Current: " + consent.getStatus());
        }

        consent.setStatus("APPROVED");
        consent.setConsentId(consentId);
        consent.setApprovedAt(Instant.now());
        consent = aaConsentRepository.save(consent);

        auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_APPROVED",
                Map.of("consentHandle", consentHandle, "consentId", consentId));

        log.info("AA consent approved: handle={}, consentId={}", consentHandle, consentId);
        return consent;
    }

    /**
     * Fetch financial data from AA after consent approval.
     * In production, this calls the AA FI fetch API. Here we simulate the response.
     */
    @Transactional
    public AaConsent fetchData(String consentHandle) {
        AaConsent consent = aaConsentRepository.findByConsentHandle(consentHandle)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentHandle));

        if (!"APPROVED".equals(consent.getStatus())) {
            throw new RuntimeException("Consent must be APPROVED to fetch data. Current: " + consent.getStatus());
        }

        // Simulated data fetch — in production, call AA's /FI/fetch API
        Map<String, Object> fetchedData = new LinkedHashMap<>();
        fetchedData.put("fetchTimestamp", Instant.now().toString());
        fetchedData.put("accountCount", 2);
        fetchedData.put("accounts", List.of(
                Map.of("type", "SAVINGS", "bank", "SBI", "balance", 245000,
                        "avgMonthlyBalance", 180000, "txnCount6Months", 142),
                Map.of("type", "CURRENT", "bank", "HDFC", "balance", 890000,
                        "avgMonthlyBalance", 620000, "txnCount6Months", 387)
        ));
        fetchedData.put("totalBalance", 1135000);
        fetchedData.put("avgMonthlyInflow", 450000);
        fetchedData.put("avgMonthlyOutflow", 320000);
        fetchedData.put("regularEmiOutflows", 45000);
        fetchedData.put("bounceCount6Months", 0);

        consent.setFetchedDataSummary(fetchedData);
        consent.setDataFetchedAt(Instant.now());
        consent.setStatus("DATA_FETCHED");
        consent = aaConsentRepository.save(consent);

        auditService.logEvent(consent.getApplicationId(), "AA_DATA_FETCHED",
                Map.of("consentHandle", consentHandle, "accountCount", "2"));

        log.info("AA data fetched for consent: handle={}, accounts=2", consentHandle);
        return consent;
    }

    /**
     * Revoke a consent.
     */
    @Transactional
    public AaConsent revokeConsent(UUID consentId, String reason) {
        AaConsent consent = aaConsentRepository.findById(consentId)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentId));

        consent.setStatus("REVOKED");
        consent.setRevokedAt(Instant.now());
        consent.setRevokeReason(reason);
        consent = aaConsentRepository.save(consent);

        auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_REVOKED",
                Map.of("consentHandle", consent.getConsentHandle(), "reason", reason));

        log.info("AA consent revoked: handle={}, reason={}", consent.getConsentHandle(), reason);
        return consent;
    }

    /**
     * Get consents for an application.
     */
    public List<AaConsent> getConsents(UUID applicationId) {
        return aaConsentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    /**
     * Get consent by handle.
     */
    public AaConsent getByHandle(String consentHandle) {
        return aaConsentRepository.findByConsentHandle(consentHandle)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentHandle));
    }

    /**
     * Check if application has approved/fetched AA data.
     */
    public boolean hasActiveConsent(UUID applicationId) {
        return aaConsentRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(applicationId, "DATA_FETCHED")
                .isPresent()
                || aaConsentRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(applicationId, "APPROVED")
                .isPresent();
    }
}
