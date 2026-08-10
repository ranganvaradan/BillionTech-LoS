package com.los.core.service.integration;

import com.los.core.model.enums.KycStepType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public interface IIntegrationRouterService {

    /**
     * Primary KYC routing: uses {@code aggregator_routing} when configured, otherwise
     * legacy capability + {@code IKycProvider#getPriority()} ordering.
     *
     * @param preferredProviderName when non-null, matches workflow {@code steps[].provider}
     *                              (e.g. AUTHBRIDGE) — used only when the provider is active
     *                              for the step in {@code aggregator_routing} or when no routing
     *                              rows exist; takes first attempt, then same fallback as today.
     */
    default KycRouteResult routeKycRequest(
            UUID applicationId, KycStepType stepType, Map<String, Object> payload) {
        return routeKycRequest(applicationId, stepType, payload, null);
    }

    KycRouteResult routeKycRequest(
            UUID applicationId,
            KycStepType stepType,
            Map<String, Object> payload,
            String preferredProviderName);

    /**
     * Bureau pull with optional application context (for logging/auditing in providers).
     */
    BureauRouteResult routeBureauRequest(UUID applicationId, Map<String, Object> payload);

    /**
     * eSign initiation. Expected payload keys: {@code documentKey} or {@code documentStorageKey},
     * {@code signerInfo} (map), optional {@code returnUrl} (embedded signing redirect),
     * optional {@code esignStepType}. Additional keys remain available for providers.
     */
    ESignRouteResult routeESignRequest(UUID applicationId, Map<String, Object> payload);

    /**
     * Central webhook entry: normalizes provider name, dispatches to the right handler (e.g. eSign).
     */
    Map<String, Object> handleWebhookCallback(String providerName, Map<String, Object> payload);

    /**
     * @deprecated prefer {@link #routeKycRequest(UUID, KycStepType, Map)} with application id
     */
    default KycRouteResult routeKycCheck(KycStepType stepType, Map<String, Object> payload) {
        return routeKycRequest(null, stepType, payload, null);
    }

    default BureauRouteResult routeBureauPull(Map<String, Object> borrowerInfo) {
        UUID appId = extractApplicationId(borrowerInfo);
        return routeBureauRequest(appId, borrowerInfo != null ? borrowerInfo : Map.of());
    }

    default ESignRouteResult routeESignRequest(UUID applicationId, String documentKey, Map<String, Object> signerInfo) {
        Map<String, Object> p = new HashMap<>();
        p.put("documentKey", documentKey);
        p.put("signerInfo", signerInfo != null ? signerInfo : Map.of());
        if (p.get("esignStepType") == null) {
            String dk = documentKey != null ? documentKey : "";
            String u = dk.toUpperCase(Locale.ROOT);
            if (u.contains("AGREEMENT") || u.contains("LOAN")) {
                p.put("esignStepType", "ESIGN_AGREEMENT");
            } else if (u.contains("KFS")) {
                p.put("esignStepType", "ESIGN_KFS");
            } else {
                p.put("esignStepType", "ESIGN_AGREEMENT");
            }
        }
        return routeESignRequest(applicationId, p);
    }

    Map<String, Object> testConnectivity(String providerName);

    private static UUID extractApplicationId(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        Object aid = m.get("applicationId");
        if (aid == null) {
            return null;
        }
        try {
            return UUID.fromString(aid.toString());
        } catch (Exception e) {
            return null;
        }
    }

    record KycRouteResult(boolean success, String providerName, Map<String, Object> resultData, String errorMessage) {}

    /**
     * @param providerName name of the eSign provider that was invoked (or last attempted in chain); may be null on failures
     */
    record ESignRouteResult(
            boolean success,
            String transactionId,
            String signingUrl,
            String errorMessage,
            String providerName,
            Map<String, Object> providerMetadata) {

        public ESignRouteResult(boolean success, String transactionId, String signingUrl, String errorMessage, String providerName) {
            this(success, transactionId, signingUrl, errorMessage, providerName, null);
        }
    }

    record BureauRouteResult(boolean success, int creditScore, Map<String, Object> reportData, String transactionId, String errorMessage) {}
}
