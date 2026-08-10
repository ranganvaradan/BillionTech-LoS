package com.los.core.service.aa.providers;

public interface IAccountAggregatorProvider {

    AaConsentResponse createConsent(AaConsentRequest request);

    AaConsentStatus checkConsentStatus(String providerConsentId);

    AaFetchResponse fetchFinancialData(String providerConsentId);

    void revokeConsent(String providerConsentId, String reason);

    String getProviderName();
}
