package com.los.core.service.integration;

import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;

import java.util.Map;

public interface IIntegrationRouterService {

    KycCheckResult routeKycCheck(KycStepType stepType, Map<String, Object> payload, ProviderType preferredProvider);

    ESignResult routeESignRequest(String documentType, byte[] documentBytes, Map<String, Object> signerInfo);

    BureauResult routeBureauPull(Map<String, Object> borrowerInfo);

    boolean testConnectivity(ProviderType providerType);

    record KycCheckResult(boolean verified, double confidenceScore, Map<String, Object> parsedData, String rawResponse, String errorMessage) {}

    record ESignResult(String signingRequestId, String signingUrl, String status, String errorMessage) {}

    record BureauResult(int creditScore, String scoreTier, Map<String, Object> reportData, String rawResponse, String errorMessage) {}
}
