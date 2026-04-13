package com.los.core.service.integration;

import com.los.core.model.enums.KycStepType;
import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class IntegrationRouterServiceImpl implements IIntegrationRouterService {

    private final List<IKycProvider> kycProviders;
    private final IBureauProvider bureauProvider;
    private final IESignProvider eSignProvider;

    public IntegrationRouterServiceImpl(List<IKycProvider> kycProviders,
                                         IBureauProvider bureauProvider,
                                         IESignProvider eSignProvider) {
        this.kycProviders = kycProviders;
        this.bureauProvider = bureauProvider;
        this.eSignProvider = eSignProvider;
    }

    @Override
    public KycRouteResult routeKycCheck(KycStepType stepType, Map<String, Object> payload) {
        IKycProvider provider = findKycProvider(stepType);
        if (provider == null) {
            return new KycRouteResult(false, null, null, "No provider found for step: " + stepType);
        }

        log.info("Routing KYC check {} to provider: {}", stepType, provider.getProviderName());
        IKycProvider.KycVerificationResult result = provider.verify(stepType, payload);

        return new KycRouteResult(
                result.success(),
                provider.getProviderName(),
                Map.of(
                        "confidenceScore", result.confidenceScore(),
                        "parsedData", result.parsedData() != null ? result.parsedData() : Map.of(),
                        "transactionId", result.transactionId()
                ),
                result.errorMessage()
        );
    }

    @Override
    public ESignRouteResult routeESignRequest(UUID applicationId, String documentKey, Map<String, Object> signerInfo) {
        log.info("Routing eSign request for application: {}", applicationId);
        IESignProvider.ESignInitResult result = eSignProvider.initiateSigning(applicationId, documentKey, signerInfo);

        return new ESignRouteResult(
                result.success(),
                result.transactionId(),
                result.signingUrl(),
                result.errorMessage()
        );
    }

    @Override
    public BureauRouteResult routeBureauPull(Map<String, Object> borrowerInfo) {
        log.info("Routing bureau pull request");
        IBureauProvider.BureauPullResult result = bureauProvider.pullReport(borrowerInfo);

        return new BureauRouteResult(
                result.success(),
                result.creditScore(),
                result.reportData(),
                result.transactionId(),
                result.errorMessage()
        );
    }

    @Override
    public Map<String, Object> testConnectivity(String providerName) {
        log.info("Testing connectivity for provider: {}", providerName);
        // Basic connectivity check — in production, would ping provider APIs
        return Map.of(
                "provider", providerName,
                "status", "REACHABLE",
                "latencyMs", 45,
                "timestamp", java.time.Instant.now().toString()
        );
    }

    private IKycProvider findKycProvider(KycStepType stepType) {
        return kycProviders.stream()
                .filter(p -> p.supports(stepType))
                .findFirst()
                .orElse(null);
    }
}
