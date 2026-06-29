package com.los.core.service.aa;

import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.AaConsent;
import com.los.core.repository.AaConsentRepository;
import com.los.core.service.aa.providers.AaConsentRequest;
import com.los.core.service.aa.providers.AaConsentResponse;
import com.los.core.service.aa.providers.AaFetchResponse;
import com.los.core.service.aa.providers.IAccountAggregatorProvider;
import com.los.core.service.aa.providers.impl.SetuAaProvider;
import com.los.core.service.aa.providers.impl.SimulatedAaProvider;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final IntegrationProperties integrationProperties;
    private final SetuAaProvider setuAaProvider;
    private final SimulatedAaProvider simulatedAaProvider;

    /**
     * Create a consent request to an Account Aggregator.
     */
    @Transactional
    public AaConsent createConsentRequest(UUID applicationId, UUID customerId,
                                           List<String> fiTypes, String aaName,
                                           Map<String, Object> purpose) {
        String consentHandle = "AA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant consentStartDate = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant consentExpiryDate = Instant.now().plus(180, ChronoUnit.DAYS);
        Map<String, Object> purposeInfo = purpose != null ? new LinkedHashMap<>(purpose) : defaultPurpose();

        AaConsentRequest providerRequest = new AaConsentRequest(
                applicationId,
                customerId,
                fiTypes != null ? fiTypes : List.of("DEPOSIT", "TERM_DEPOSIT"),
                aaName,
                purposeInfo,
                consentHandle,
                consentStartDate,
                consentExpiryDate,
                "ONETIME",
                "STORE"
        );

        IAccountAggregatorProvider provider = resolveProvider();
        AaConsentResponse providerResponse = provider.createConsent(providerRequest);

        enrichPurposeInfo(purposeInfo, providerResponse, provider.getProviderName());

        AaConsent consent = AaConsent.builder()
                .applicationId(applicationId)
                .customerId(customerId)
                .consentHandle(consentHandle)
                .consentId(providerResponse.providerConsentId())
                .status(normalizeLocalStatus(providerResponse.status(), "PENDING"))
                .fiTypes(providerRequest.fiTypes())
                .consentStartDate(consentStartDate)
                .consentExpiryDate(consentExpiryDate)
                .fetchFrequency(providerRequest.fetchFrequency())
                .consentMode(providerRequest.consentMode())
                .purposeInfo(purposeInfo)
                .aaName(aaName != null ? aaName : provider.getProviderName())
                .build();

        consent = aaConsentRepository.save(consent);

        auditService.logEvent(applicationId, "AA_CONSENT_REQUESTED",
                Map.of(
                        "consentHandle", consentHandle,
                        "provider", provider.getProviderName(),
                        "providerConsentId", providerResponse.providerConsentId() != null
                                ? providerResponse.providerConsentId() : "",
                        "fiTypes", String.join(",", consent.getFiTypes())
                ));

        log.info("AA consent request created: handle={} provider={} for application={}",
                consentHandle, provider.getProviderName(), applicationId);
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
        consent.setConsentId(consentId != null && !consentId.isBlank() ? consentId : consent.getConsentId());
        consent.setApprovedAt(Instant.now());
        consent = aaConsentRepository.save(consent);

        auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_APPROVED",
                Map.of("consentHandle", consentHandle, "consentId", consent.getConsentId()));

        log.info("AA consent approved: handle={}, consentId={}", consentHandle, consent.getConsentId());
        return consent;
    }

    /**
     * Fetch financial data from AA after consent approval.
     */
    @Transactional
    public AaConsent fetchData(String consentHandle) {
        AaConsent consent = aaConsentRepository.findByConsentHandle(consentHandle)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentHandle));

        if (!"APPROVED".equals(consent.getStatus())) {
            throw new RuntimeException("Consent must be APPROVED to fetch data. Current: " + consent.getStatus());
        }

        String providerConsentId = resolveProviderConsentId(consent);
        AaFetchResponse fetchResponse = resolveProvider().fetchFinancialData(providerConsentId);

        Map<String, Object> fetchedData = fetchResponse.fetchedDataSummary() != null
                ? new LinkedHashMap<>(fetchResponse.fetchedDataSummary())
                : new LinkedHashMap<>();
        fetchedData.putIfAbsent("fetchTimestamp", Instant.now().toString());
        fetchedData.put("provider", resolveProvider().getProviderName());

        consent.setFetchedDataSummary(fetchedData);
        consent.setDataFetchedAt(Instant.now());
        consent.setStatus("DATA_FETCHED");
        consent = aaConsentRepository.save(consent);

        auditService.logEvent(consent.getApplicationId(), "AA_DATA_FETCHED",
                Map.of(
                        "consentHandle", consentHandle,
                        "accountCount", String.valueOf(fetchedData.getOrDefault("accountCount", 0))
                ));

        log.info("AA data fetched for consent: handle={}, accounts={}",
                consentHandle, fetchedData.get("accountCount"));
        return consent;
    }

    /**
     * Revoke a consent.
     */
    @Transactional
    public AaConsent revokeConsent(UUID consentId, String reason) {
        AaConsent consent = aaConsentRepository.findById(consentId)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentId));

        String providerConsentId = resolveProviderConsentId(consent);
        if (providerConsentId != null && !providerConsentId.isBlank()) {
            try {
                resolveProvider().revokeConsent(providerConsentId, reason);
            } catch (Exception e) {
                log.warn("AA provider revoke failed for {}: {}", providerConsentId, e.getMessage());
            }
        }

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
     * Poll provider for latest consent status and sync local record when approved.
     */
    @Transactional
    public AaConsent syncConsentStatus(String consentHandle) {
        AaConsent consent = aaConsentRepository.findByConsentHandle(consentHandle)
                .orElseThrow(() -> new RuntimeException("Consent not found: " + consentHandle));

        String providerConsentId = resolveProviderConsentId(consent);
        var status = resolveProvider().checkConsentStatus(providerConsentId);
        String mappedStatus = normalizeLocalStatus(status.status(), consent.getStatus());

        if ("APPROVED".equals(mappedStatus) && "PENDING".equals(consent.getStatus())) {
            consent.setStatus("APPROVED");
            consent.setApprovedAt(Instant.now());
            if (consent.getConsentId() == null || consent.getConsentId().isBlank()) {
                consent.setConsentId(status.providerConsentId());
            }
            consent = aaConsentRepository.save(consent);
            auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_APPROVED",
                    Map.of("consentHandle", consentHandle, "source", "STATUS_POLL"));
        }

        return consent;
    }

    /**
     * Process Setu AA webhook callback (consent status update or FI data ready).
     * Updates local consent state and auto-fetches FI data when consent is approved.
     */
    @Transactional
    public void handleAaCallback(Map<String, Object> callbackPayload) {
        if (callbackPayload == null || callbackPayload.isEmpty()) {
            log.warn("[AA Callback] Ignoring empty payload");
            return;
        }

        String notificationType = stringValue(callbackPayload, "type");
        String providerConsentId = stringValue(callbackPayload, "consentId");
        log.info("[AA Callback] type={} consentId={}", notificationType, providerConsentId);

        Optional<AaConsent> consentOpt = resolveConsentFromCallback(callbackPayload);
        if (consentOpt.isEmpty()) {
            log.warn("[AA Callback] No matching consent for providerConsentId={}", providerConsentId);
            return;
        }

        AaConsent consent = consentOpt.get();
        mergeCallbackMetadata(consent, callbackPayload);

        auditService.logEvent(consent.getApplicationId(), "AA_CALLBACK_RECEIVED",
                Map.of(
                        "consentHandle", consent.getConsentHandle(),
                        "type", notificationType != null ? notificationType : "UNKNOWN",
                        "providerConsentId", providerConsentId != null ? providerConsentId : ""
                ));

        if (isFiDataNotification(notificationType)) {
            handleFiDataReady(consent, callbackPayload);
            return;
        }

        handleConsentStatusUpdate(consent, callbackPayload, providerConsentId);
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

    private IAccountAggregatorProvider resolveProvider() {
        if (integrationProperties.getSetuAa().isSimulation()) {
            return simulatedAaProvider;
        }
        return setuAaProvider;
    }

    private static String resolveProviderConsentId(AaConsent consent) {
        if (consent.getConsentId() != null && !consent.getConsentId().isBlank()) {
            return consent.getConsentId();
        }
        return consent.getConsentHandle();
    }

    private static void enrichPurposeInfo(Map<String, Object> purposeInfo,
                                          AaConsentResponse providerResponse,
                                          String providerName) {
        purposeInfo.put("provider", providerName);
        if (providerResponse.redirectUrl() != null) {
            purposeInfo.put("redirectUrl", providerResponse.redirectUrl());
        }
        if (providerResponse.providerConsentId() != null) {
            purposeInfo.put("providerConsentId", providerResponse.providerConsentId());
        }
        if (providerResponse.rawResponse() != null) {
            purposeInfo.put("providerRawResponse", providerResponse.rawResponse());
        }
    }

    private static Map<String, Object> defaultPurpose() {
        Map<String, Object> purpose = new HashMap<>();
        purpose.put("code", "101");
        purpose.put("text", "Loan underwriting and credit assessment");
        purpose.put("refUri", "https://api.rebit.org.in/aa/purpose/101");
        return purpose;
    }

    private static String normalizeLocalStatus(String providerStatus, String fallback) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return fallback;
        }
        String normalized = providerStatus.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized)) {
            return "APPROVED";
        }
        if ("PENDING".equals(normalized) || "APPROVED".equals(normalized)
                || "REJECTED".equals(normalized) || "REVOKED".equals(normalized)) {
            return normalized;
        }
        return fallback;
    }

    private void handleConsentStatusUpdate(AaConsent consent, Map<String, Object> callbackPayload,
                                           String providerConsentId) {
        if (!isCallbackSuccessful(callbackPayload)) {
            markConsentRejected(consent, callbackPayload, "AA provider reported consent failure");
            return;
        }

        String providerStatus = extractProviderStatus(callbackPayload);
        String mappedStatus = normalizeLocalStatus(providerStatus, consent.getStatus());

        if ("PENDING".equals(mappedStatus)) {
            aaConsentRepository.save(consent);
            return;
        }

        if ("APPROVED".equals(mappedStatus)) {
            approveFromCallback(consent, providerConsentId);
            autoFetchIfNeeded(consent.getConsentHandle());
            return;
        }

        if ("REJECTED".equals(mappedStatus)) {
            markConsentRejected(consent, callbackPayload, "Borrower rejected AA consent");
            return;
        }

        if ("REVOKED".equals(mappedStatus)) {
            consent.setStatus("REVOKED");
            consent.setRevokedAt(Instant.now());
            consent.setRevokeReason("Revoked via AA provider callback");
            aaConsentRepository.save(consent);
            auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_REVOKED",
                    Map.of("consentHandle", consent.getConsentHandle(), "source", "WEBHOOK"));
        }
    }

    private void handleFiDataReady(AaConsent consent, Map<String, Object> callbackPayload) {
        if ("DATA_FETCHED".equals(consent.getStatus())) {
            return;
        }
        if ("APPROVED".equals(consent.getStatus())) {
            autoFetchIfNeeded(consent.getConsentHandle());
            return;
        }
        if ("PENDING".equals(consent.getStatus()) && isCallbackSuccessful(callbackPayload)) {
            approveFromCallback(consent, stringValue(callbackPayload, "consentId"));
            autoFetchIfNeeded(consent.getConsentHandle());
        }
    }

    private void approveFromCallback(AaConsent consent, String providerConsentId) {
        if (!"PENDING".equals(consent.getStatus()) && !"APPROVED".equals(consent.getStatus())) {
            return;
        }
        if ("PENDING".equals(consent.getStatus())) {
            consent.setStatus("APPROVED");
            consent.setApprovedAt(Instant.now());
        }
        if (providerConsentId != null && !providerConsentId.isBlank()) {
            consent.setConsentId(providerConsentId);
        }
        aaConsentRepository.save(consent);
        auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_APPROVED",
                Map.of("consentHandle", consent.getConsentHandle(), "source", "WEBHOOK"));
    }

    private void autoFetchIfNeeded(String consentHandle) {
        AaConsent current = aaConsentRepository.findByConsentHandle(consentHandle).orElse(null);
        if (current == null || "DATA_FETCHED".equals(current.getStatus())) {
            return;
        }
        if (!"APPROVED".equals(current.getStatus())) {
            return;
        }
        try {
            fetchData(consentHandle);
        } catch (Exception e) {
            log.error("[AA Callback] Auto-fetch failed for handle={}: {}", consentHandle, e.getMessage(), e);
        }
    }

    private void markConsentRejected(AaConsent consent, Map<String, Object> callbackPayload, String defaultReason) {
        consent.setStatus("REJECTED");
        Map<String, Object> purposeInfo = consent.getPurposeInfo() != null
                ? new LinkedHashMap<>(consent.getPurposeInfo())
                : new LinkedHashMap<>();
        purposeInfo.put("rejectionReason", extractErrorMessage(callbackPayload, defaultReason));
        consent.setPurposeInfo(purposeInfo);
        aaConsentRepository.save(consent);
        auditService.logEvent(consent.getApplicationId(), "AA_CONSENT_REJECTED",
                Map.of("consentHandle", consent.getConsentHandle(), "source", "WEBHOOK"));
    }

    private Optional<AaConsent> resolveConsentFromCallback(Map<String, Object> callbackPayload) {
        String providerConsentId = stringValue(callbackPayload, "consentId");
        if (providerConsentId != null && !providerConsentId.isBlank()) {
            Optional<AaConsent> byProviderId = aaConsentRepository.findByConsentId(providerConsentId);
            if (byProviderId.isPresent()) {
                return byProviderId;
            }
        }

        String consentHandle = extractConsentHandle(callbackPayload);
        if (consentHandle != null && !consentHandle.isBlank()) {
            return aaConsentRepository.findByConsentHandle(consentHandle);
        }

        return Optional.empty();
    }

    private static boolean isFiDataNotification(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) {
            return false;
        }
        String normalized = notificationType.trim().toUpperCase(Locale.ROOT);
        return "FI_DATA_READY".equals(normalized)
                || "SESSION_STATUS_UPDATE".equals(normalized)
                || "FI_NOTIFICATION".equals(normalized);
    }

    private static boolean isCallbackSuccessful(Map<String, Object> callbackPayload) {
        Object success = callbackPayload.get("success");
        if (success == null) {
            return extractProviderStatus(callbackPayload) != null;
        }
        if (success instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(success));
    }

    private static String extractProviderStatus(Map<String, Object> callbackPayload) {
        Map<String, Object> data = mapValue(callbackPayload.get("data"));
        if (data != null) {
            String status = stringValue(data, "status");
            if (status != null && !status.isBlank()) {
                return status;
            }
        }
        return stringValue(callbackPayload, "status");
    }

    private static String extractConsentHandle(Map<String, Object> callbackPayload) {
        Map<String, Object> data = mapValue(callbackPayload.get("data"));
        if (data != null) {
            Map<String, Object> context = mapValue(data.get("context"));
            if (context != null) {
                String handle = stringValue(context, "consentHandle");
                if (handle != null && !handle.isBlank()) {
                    return handle;
                }
            }
            Map<String, Object> detail = mapValue(data.get("detail"));
            if (detail != null) {
                Map<String, Object> detailContext = mapValue(detail.get("context"));
                if (detailContext != null) {
                    return stringValue(detailContext, "consentHandle");
                }
            }
        }
        Map<String, Object> topContext = mapValue(callbackPayload.get("context"));
        if (topContext != null) {
            return stringValue(topContext, "consentHandle");
        }
        return null;
    }

    private static String extractErrorMessage(Map<String, Object> callbackPayload, String defaultReason) {
        Map<String, Object> error = mapValue(callbackPayload.get("error"));
        if (error != null) {
            String message = stringValue(error, "message");
            if (message != null && !message.isBlank()) {
                return message;
            }
            String code = stringValue(error, "code");
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        Object errorValue = callbackPayload.get("error");
        if (errorValue instanceof String str && !str.isBlank()) {
            return str;
        }
        return defaultReason;
    }

    private static void mergeCallbackMetadata(AaConsent consent, Map<String, Object> callbackPayload) {
        Map<String, Object> purposeInfo = consent.getPurposeInfo() != null
                ? new LinkedHashMap<>(consent.getPurposeInfo())
                : new LinkedHashMap<>();
        Map<String, Object> lastCallback = new LinkedHashMap<>();
        lastCallback.put("type", stringValue(callbackPayload, "type"));
        lastCallback.put("timestamp", stringValue(callbackPayload, "timestamp"));
        lastCallback.put("consentId", stringValue(callbackPayload, "consentId"));
        lastCallback.put("status", extractProviderStatus(callbackPayload));
        lastCallback.put("receivedAt", Instant.now().toString());
        purposeInfo.put("lastCallback", lastCallback);
        consent.setPurposeInfo(purposeInfo);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
