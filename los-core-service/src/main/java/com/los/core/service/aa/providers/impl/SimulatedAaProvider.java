package com.los.core.service.aa.providers.impl;

import com.los.core.service.aa.providers.AaConsentRequest;
import com.los.core.service.aa.providers.AaConsentResponse;
import com.los.core.service.aa.providers.AaConsentStatus;
import com.los.core.service.aa.providers.AaFetchResponse;
import com.los.core.service.aa.providers.AaFiDataParser;
import com.los.core.service.aa.providers.IAccountAggregatorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo-safe Account Aggregator provider used when {@code integration.setu-aa.simulation=true}.
 */
@Slf4j
@Component("simulatedAaProvider")
public class SimulatedAaProvider implements IAccountAggregatorProvider {

    @Override
    public AaConsentResponse createConsent(AaConsentRequest request) {
        log.info("[SimulatedAA] Creating consent handle={} application={}",
                request.consentHandle(), request.applicationId());

        String providerConsentId = "SIM-" + request.consentHandle();
        String redirectUrl = "https://aa-simulator.local/consent/" + request.consentHandle();

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("simulated", true);
        raw.put("providerConsentId", providerConsentId);
        raw.put("redirectUrl", redirectUrl);
        raw.put("status", "PENDING");

        return new AaConsentResponse(
                request.consentHandle(),
                providerConsentId,
                redirectUrl,
                "PENDING",
                raw
        );
    }

    @Override
    public AaConsentStatus checkConsentStatus(String providerConsentId) {
        log.info("[SimulatedAA] Checking consent status for {}", providerConsentId);
        return new AaConsentStatus(providerConsentId, "PENDING",
                Map.of("simulated", true, "status", "PENDING"));
    }

    @Override
    public AaFetchResponse fetchFinancialData(String providerConsentId) {
        log.info("[SimulatedAA] Fetching FI data for {}", providerConsentId);
        Map<String, Object> summary = AaFiDataParser.simulatedSummary();
        return new AaFetchResponse(summary, Map.of("simulated", true, "providerConsentId", providerConsentId));
    }

    @Override
    public void revokeConsent(String providerConsentId, String reason) {
        log.info("[SimulatedAA] Revoked consent {} reason={}", providerConsentId, reason);
    }

    @Override
    public String getProviderName() {
        return "SIMULATED_AA";
    }
}
