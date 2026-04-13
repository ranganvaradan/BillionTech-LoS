package com.los.core.service.integration;

import com.los.core.model.enums.KycStepType;

import java.util.Map;
import java.util.UUID;

public interface IIntegrationRouterService {

    KycRouteResult routeKycCheck(KycStepType stepType, Map<String, Object> payload);

    ESignRouteResult routeESignRequest(UUID applicationId, String documentKey, Map<String, Object> signerInfo);

    BureauRouteResult routeBureauPull(Map<String, Object> borrowerInfo);

    Map<String, Object> testConnectivity(String providerName);

    record KycRouteResult(boolean success, String providerName, Map<String, Object> resultData, String errorMessage) {}

    record ESignRouteResult(boolean success, String transactionId, String signingUrl, String errorMessage) {}

    record BureauRouteResult(boolean success, int creditScore, Map<String, Object> reportData, String transactionId, String errorMessage) {}
}
