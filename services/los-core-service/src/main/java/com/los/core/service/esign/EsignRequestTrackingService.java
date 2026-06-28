package com.los.core.service.esign;

import com.los.core.model.entity.schema.los2.EsignRequest;
import com.los.core.repository.schema.los2.EsignRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.los.core.model.dto.response.EsignRequestView;

import java.time.Instant;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists {@code esign_requests} rows in parallel with {@code loan_applications.esign_transaction_id}
 * (legacy id on the application row is not removed or replaced here).
 * <p>
 * <b>Gap</b> — other webhook kinds ({@code webhookKind} SANCTION, NACH) do not map into this table; only
 * agreement-like KFS/AGREEMENT callbacks with {@code applicationId} and {@code transactionId} are updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignRequestTrackingService {

    private final EsignRequestRepository esignRequestRepository;

    @Transactional(readOnly = true)
    public List<EsignRequestView> listForApplication(UUID applicationId) {
        return esignRequestRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(EsignRequestTrackingService::toView)
                .toList();
    }

    private static EsignRequestView toView(EsignRequest e) {
        return EsignRequestView.builder()
                .id(e.getId())
                .documentType(e.getDocumentType())
                .provider(e.getProvider())
                .providerRequestId(e.getProviderRequestId())
                .signingUrl(e.getSigningUrl())
                .status(e.getStatus())
                .signerName(e.getSignerName())
                .signedDocumentUrl(e.getSignedDocumentUrl())
                .createdAt(e.getCreatedAt())
                .signedAt(e.getSignedAt())
                .build();
    }

    @Transactional
    public void recordInitiationSuccess(
            UUID applicationId,
            String documentType,
            String providerName,
            String providerRequestId,
            String signingUrl,
            Map<String, Object> signerInfo) {
        recordInitiationSuccess(applicationId, documentType, providerName, providerRequestId, signingUrl,
                signerInfo, null, null);
    }

    @Transactional
    public void recordInitiationSuccess(
            UUID applicationId,
            String documentType,
            String providerName,
            String providerRequestId,
            String signingUrl,
            Map<String, Object> signerInfo,
            Map<String, Object> rawResponse) {
        recordInitiationSuccess(applicationId, documentType, providerName, providerRequestId, signingUrl,
                signerInfo, rawResponse, null);
    }

    @Transactional
    public void recordInitiationSuccess(
            UUID applicationId,
            String documentType,
            String providerName,
            String providerRequestId,
            String signingUrl,
            Map<String, Object> signerInfo,
            Map<String, Object> rawResponse,
            String esignStepType) {
        if (applicationId == null || providerRequestId == null || providerRequestId.isBlank()) {
            log.warn("Skip esign_requests row: missing applicationId or providerRequestId");
            return;
        }
        String provider = (providerName != null && !providerName.isBlank()) ? providerName : "UNKNOWN";
        Map<String, Object> signers = signerInfo != null ? signerInfo : Map.of();

        Map<String, Object> persistedRaw = new HashMap<>();
        if (rawResponse != null) {
            persistedRaw.putAll(rawResponse);
        }
        String signerKey = normalizeSignerEmailForTracking(signers);
        if (signerKey != null && !signerKey.isBlank()) {
            persistedRaw.put("losSignerEmail", signerKey);
        }
        if (esignStepType != null && !esignStepType.isBlank()) {
            persistedRaw.put("losEsignStep", esignStepType.trim());
        }

        EsignRequest row = EsignRequest.builder()
                .applicationId(applicationId)
                .documentType(documentType != null && !documentType.isBlank() ? documentType : "UNKNOWN")
                .provider(provider)
                .providerRequestId(providerRequestId)
                .signingUrl(signingUrl)
                .status(EsignRequestStatuses.INITIATED)
                .signerName(extractSignerName(signers))
                .signerAadhaarLast4(extractAadhaarLast4(signers))
                .rawResponse(persistedRaw.isEmpty() ? null : persistedRaw)
                .build();
        esignRequestRepository.save(row);
        log.info("[ESIGN_PERSIST] saved esign_requests appId={} documentType={} provider={} providerRequestId={} status=INITIATED signingUrlPresent={} losSignerEmailTracked={} losEsignStep={}",
                applicationId, row.getDocumentType(), provider, providerRequestId,
                signingUrl != null && !signingUrl.isBlank(),
                signerKey != null && !signerKey.isBlank(),
                esignStepType);
    }

    @Transactional(readOnly = true)
    public Optional<EsignRequest> findReusableSigningSession(
            UUID applicationId,
            String documentType,
            String providerName,
            String normalizedSignerEmail,
            String esignStepType) {
        if (applicationId == null || documentType == null || documentType.isBlank()
                || providerName == null || providerName.isBlank()
                || normalizedSignerEmail == null || normalizedSignerEmail.isBlank()
                || esignStepType == null || esignStepType.isBlank()) {
            return Optional.empty();
        }
        return esignRequestRepository.findLatestReusableSigningRow(
                applicationId,
                documentType.trim(),
                providerName.trim().toUpperCase(Locale.ROOT),
                normalizedSignerEmail.trim().toLowerCase(Locale.ROOT),
                esignStepType.trim());
    }

    /**
     * Normalized email for idempotency keys (lowercase trim).
     */
    public static String normalizeSignerEmailForTracking(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object v = firstNonNull(
                signerInfo.get("borrowerEmail"),
                signerInfo.get("email"),
                signerInfo.get("signerEmail"),
                signerInfo.get("EmailId"));
        if (v == null) {
            return null;
        }
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    @Transactional(readOnly = true)
    public Optional<EsignRequest> findLatestInitiatedLike(UUID applicationId) {
        if (applicationId == null) {
            return Optional.empty();
        }
        return esignRequestRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(e -> EsignRequestStatuses.INITIATED.equals(e.getStatus())
                        || EsignRequestStatuses.PENDING.equals(e.getStatus()))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<EsignRequest> findByWorkflowIdLatest(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return Optional.empty();
        }
        return esignRequestRepository.findTopByProviderRequestIdOrderByCreatedAtDesc(workflowId);
    }

    @Transactional
    public void markInitiationFailure(
            UUID applicationId,
            String documentType,
            String providerName,
            Map<String, Object> signerInfo,
            Map<String, Object> rawProviderResponse,
            String errorMessage) {
        if (applicationId == null) {
            return;
        }
        Map<String, Object> raw = new HashMap<>();
        if (rawProviderResponse != null) {
            raw.putAll(rawProviderResponse);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            raw.put("errorMessage", errorMessage);
        }
        String provider = (providerName != null && !providerName.isBlank()) ? providerName : "UNKNOWN";
        Map<String, Object> signers = signerInfo != null ? signerInfo : Map.of();
        EsignRequest row = EsignRequest.builder()
                .applicationId(applicationId)
                .documentType(documentType != null && !documentType.isBlank() ? documentType : "UNKNOWN")
                .provider(provider)
                .providerRequestId("FAILED-" + applicationId + "-" + System.currentTimeMillis())
                .status(EsignRequestStatuses.FAILED)
                .signerName(extractSignerName(signers))
                .signerAadhaarLast4(extractAadhaarLast4(signers))
                .rawResponse(raw.isEmpty() ? null : raw)
                .build();
        esignRequestRepository.save(row);
    }

    @Transactional
    public void applySignedLocalPath(
            UUID applicationId,
            String providerRequestId,
            String absolutePath,
            Map<String, Object> mergeIntoRaw) {
        if (applicationId == null || providerRequestId == null || providerRequestId.isBlank()) {
            return;
        }
        Optional<EsignRequest> opt = esignRequestRepository
                .findTopByApplicationIdAndProviderRequestIdOrderByCreatedAtDesc(applicationId, providerRequestId);
        if (opt.isEmpty()) {
            log.debug("applySignedLocalPath: no esign_requests row for appId={} workflowId={}", applicationId, providerRequestId);
            return;
        }
        EsignRequest e = opt.get();
        e.setStatus(EsignRequestStatuses.SIGNED);
        e.setSignedAt(Instant.now());
        if (absolutePath != null && !absolutePath.isBlank()) {
            e.setSignedDocumentUrl(absolutePath);
        }
        Map<String, Object> merged = new HashMap<>();
        if (e.getRawResponse() != null) {
            merged.putAll(e.getRawResponse());
        }
        if (mergeIntoRaw != null) {
            merged.putAll(mergeIntoRaw);
        }
        e.setRawResponse(merged.isEmpty() ? e.getRawResponse() : merged);
        esignRequestRepository.save(e);
    }

    @Transactional
    public void markWorkflowFailed(String providerRequestId, String reason, Map<String, Object> payload) {
        if (providerRequestId == null || providerRequestId.isBlank()) {
            return;
        }
        Optional<EsignRequest> opt = esignRequestRepository.findTopByProviderRequestIdOrderByCreatedAtDesc(providerRequestId);
        if (opt.isEmpty()) {
            return;
        }
        EsignRequest e = opt.get();
        e.setStatus(EsignRequestStatuses.FAILED);
        Map<String, Object> merged = new HashMap<>();
        if (e.getRawResponse() != null) {
            merged.putAll(e.getRawResponse());
        }
        if (payload != null) {
            merged.putAll(payload);
        }
        if (reason != null && !reason.isBlank()) {
            merged.put("failureReason", reason);
        }
        e.setRawResponse(merged);
        esignRequestRepository.save(e);
    }

    @Transactional(readOnly = true)
    public String resolveLatestStatusForApplication(UUID applicationId) {
        return esignRequestRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .findFirst()
                .map(EsignRequest::getStatus)
                .orElse("UNKNOWN");
    }

    /**
     * Updates the latest matching row when the callback {@code status} maps to a terminal state
     * ({@link EsignRequestStatuses#SIGNED}, {@link EsignRequestStatuses#FAILED}, {@link EsignRequestStatuses#EXPIRED}).
     * Unknown status strings are ignored (no row update) to avoid corrupting data.
     */
    @Transactional
    public void updateFromAgreementCallback(
            UUID applicationId,
            String providerRequestId,
            String callbackStatus,
            Map<String, Object> payload) {
        if (applicationId == null || providerRequestId == null || providerRequestId.isBlank()) {
            return;
        }
        String terminal = mapRawCallbackStatusToTerminal(callbackStatus);
        if (terminal == null) {
            log.debug("eSign callback status '{}' left esign_requests unchanged (unmapped terminal)", callbackStatus);
            return;
        }
        Optional<EsignRequest> opt = esignRequestRepository
                .findTopByApplicationIdAndProviderRequestIdOrderByCreatedAtDesc(applicationId, providerRequestId);
        if (opt.isEmpty()) {
            log.debug("No esign_requests row for appId={} providerRequestId={}", applicationId, providerRequestId);
            return;
        }
        EsignRequest e = opt.get();
        e.setStatus(terminal);
        e.setRawResponse(payload != null ? new HashMap<>(payload) : null);
        if (EsignRequestStatuses.SIGNED.equals(terminal)) {
            e.setSignedAt(Instant.now());
            Object url = firstSignedDocumentUrl(payload);
            if (url != null) {
                e.setSignedDocumentUrl(url.toString());
            }
        }
        esignRequestRepository.save(e);
    }

    private static Object firstSignedDocumentUrl(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (String k : new String[] {"signedDocumentUrl", "signedDocument", "documentUrl", "signedUrl"}) {
            Object v = payload.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * Maps provider/lender callback free-text to our terminal status; returns null if not a known terminal.
     */
    private static String mapRawCallbackStatusToTerminal(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim();
        if ("completed".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s) || "signed".equalsIgnoreCase(s)) {
            return EsignRequestStatuses.SIGNED;
        }
        if ("expired".equalsIgnoreCase(s)) {
            return EsignRequestStatuses.EXPIRED;
        }
        if ("failed".equalsIgnoreCase(s) || "error".equalsIgnoreCase(s) || "failure".equalsIgnoreCase(s)) {
            return EsignRequestStatuses.FAILED;
        }
        return null;
    }

    private static String extractSignerName(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object name = firstNonNull(
                signerInfo.get("name"),
                signerInfo.get("fullName"),
                signerInfo.get("signerName"),
                signerInfo.get("borrowerName"));
        if (name != null) {
            return name.toString();
        }
        Object fn = signerInfo.get("firstName");
        Object ln = signerInfo.get("lastName");
        if (fn != null || ln != null) {
            return (fn != null ? fn.toString() : "").trim() + " " + (ln != null ? ln.toString() : "").trim();
        }
        return null;
    }

    private static String extractAadhaarLast4(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object v = firstNonNull(
                signerInfo.get("aadhaarLast4"),
                signerInfo.get("signerAadhaarLast4"),
                signerInfo.get("aadhaar"));
        if (v == null) {
            return null;
        }
        String digits = v.toString().replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        return digits.substring(digits.length() - 4);
    }

    private static Object firstNonNull(Object... o) {
        for (Object x : o) {
            if (x != null) {
                return x;
            }
        }
        return null;
    }
}
