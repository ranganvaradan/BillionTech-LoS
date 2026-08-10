package com.los.core.service.integration.providers;

import java.util.Map;
import java.util.UUID;

/**
 * Pluggable eSign / digital signature provider contract (aligns with LOS v2.0
 * IESignProvider: initiate, signing URL, status, download, cancel, provider name).
 * <p>
 * {@link #initiateSigning(UUID, String, Map)} is retained for existing callers; it
 * delegates to {@link #initiateSigningRequest(ESignInitRequest)}. New code may use
 * the request type directly.
 */
public interface IESignProvider {

    String getProviderName();

    // --- v2.0 design-aligned entry points (implement in providers) ---

    /**
     * Design doc: create signing session at provider; returns transaction id, signing URL, etc.
     */
    ESignInitResult initiateSigningRequest(ESignInitRequest request);

    /**
     * Design doc: URL to open for signing, optionally with customer return URL.
     * Used when a signing session already exists and the client needs a (possibly refreshed) URL.
     */
    String getSigningUrl(String signingRequestId, String returnUrl);

    /**
     * Design doc: current status of a signing request (provider-specific status strings).
     */
    ESignStatusResult getSigningStatus(String signingRequestId);

    byte[] downloadSignedDocument(String signingRequestId);

    /**
     * Design doc: cancel in-progress signing at provider. Default is no-op; providers may
     * override when they expose a cancel API.
     */
    default void cancelSigningRequest(String signingRequestId, String reason) {
        // no-op: preserve behavior when provider has no cancel integration
    }

    // --- request wrapper (v2) ---

    /**
     * @param returnUrl optional redirect after signing (e.g. customer portal)
     */
    record ESignInitRequest(
            UUID applicationId,
            String documentStorageKey,
            Map<String, Object> signerInfo,
            String returnUrl) {

        public ESignInitRequest(
                UUID applicationId,
                String documentStorageKey,
                Map<String, Object> signerInfo) {
            this(applicationId, documentStorageKey, signerInfo, null);
        }
    }

    // --- back-compat: existing services call this thin wrapper ---

    default ESignInitResult initiateSigning(
            UUID applicationId,
            String documentStorageKey,
            Map<String, Object> signerInfo) {
        return initiateSigningRequest(new ESignInitRequest(applicationId, documentStorageKey, signerInfo));
    }

    /**
     * @deprecated use {@link #getSigningStatus(String)} (v2.0 name)
     */
    @Deprecated
    default ESignStatusResult checkStatus(String eSignTransactionId) {
        return getSigningStatus(eSignTransactionId);
    }

    // --- result records (unchanged) ---

    record ESignInitResult(
            boolean success,
            String transactionId,
            String signingUrl,
            String errorMessage,
            Map<String, Object> providerMetadata) {

        public ESignInitResult(boolean success, String transactionId, String signingUrl, String errorMessage) {
            this(success, transactionId, signingUrl, errorMessage, null);
        }
    }

    record ESignStatusResult(String status, String transactionId, Map<String, Object> signerDetails, String errorMessage) {}
}
