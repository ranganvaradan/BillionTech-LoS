package com.los.core.service.integration;

import com.los.core.model.entity.AggregatorConfig;
import com.los.core.model.enums.IntegrationCategory;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.AggregatorConfigRepository;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.integration.providers.IKycProvider;
import com.los.core.service.integration.webhook.IntegrationCallbackProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationProviderHierarchyTest {

    @Mock
    private AggregatorConfigRepository aggregatorConfigRepository;
    @Mock
    private IntegrationCallbackProcessor integrationCallbackProcessor;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;

    @Mock
    private IKycProvider perfiosProvider;
    @Mock
    private IKycProvider authbridgeProvider;

    @Mock
    private IBureauProvider equifaxProvider;

    @Mock
    private IESignProvider emsigner;
    @Mock
    private IESignProvider authbridgeEsign;

    @BeforeEach
    void stubEsignReuse() {
        lenient().when(esignRequestTrackingService.findReusableSigningSession(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void panVerify_triesPerfiostBeforeAuthbridge() {
        Map<String, IKycProvider> kycMap = new HashMap<>();
        kycMap.put("PERFIOS", perfiosProvider);
        kycMap.put("AUTHBRIDGE", authbridgeProvider);
        when(perfiosProvider.getProviderName()).thenReturn("PERFIOS");
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of(
                        AggregatorConfig.builder().providerName("PERFIOS").integrationType(IntegrationCategory.KYC)
                                .kycStepType("PAN_VERIFY").priority(200).active(true).allowFallback(true).build(),
                        AggregatorConfig.builder().providerName("AUTHBRIDGE").integrationType(IntegrationCategory.KYC)
                                .kycStepType("PAN_VERIFY").priority(190).active(true).allowFallback(true).build()));
        when(perfiosProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(perfiosProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(true, 0.95, Map.of(), "pf", null));

        var router = new IntegrationRouterServiceImpl(
                kycMap, Map.of(), Map.of(), aggregatorConfigRepository, integrationCallbackProcessor,
                esignRequestTrackingService);

        var r = router.routeKycRequest(UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of("panNumber", "ABCDE1234F"), null);
        assertTrue(r.success());
        assertEquals("PERFIOS", r.providerName());
        verify(perfiosProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(authbridgeProvider, never()).verify(any(), anyMap());
    }

    @Test
    void panVerify_fallsBackWhenPerfiFails() {
        Map<String, IKycProvider> kycMap = new HashMap<>();
        kycMap.put("PERFIOS", perfiosProvider);
        kycMap.put("AUTHBRIDGE", authbridgeProvider);
        when(perfiosProvider.getProviderName()).thenReturn("PERFIOS");
        when(authbridgeProvider.getProviderName()).thenReturn("AUTHBRIDGE");
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of(
                        AggregatorConfig.builder().providerName("PERFIOS").integrationType(IntegrationCategory.KYC)
                                .kycStepType("PAN_VERIFY").priority(200).active(true).allowFallback(true).build(),
                        AggregatorConfig.builder().providerName("AUTHBRIDGE").integrationType(IntegrationCategory.KYC)
                                .kycStepType("PAN_VERIFY").priority(190).active(true).allowFallback(true).build()));
        when(perfiosProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(authbridgeProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(perfiosProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(false, 0, Map.of(), null, "pf-fail"));
        when(authbridgeProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(true, 0.9, Map.of(), "ab", null));

        var router = new IntegrationRouterServiceImpl(
                kycMap, Map.of(), Map.of(), aggregatorConfigRepository, integrationCallbackProcessor,
                esignRequestTrackingService);

        var r = router.routeKycRequest(UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of(), null);
        assertTrue(r.success());
        assertEquals("AUTHBRIDGE", r.providerName());
        verify(perfiosProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(authbridgeProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
    }

    @Test
    void bureauRouting_usesOnlyEquifaxInOrder() {
        when(aggregatorConfigRepository.findActiveByType(IntegrationCategory.BUREAU))
                .thenReturn(List.of(
                        AggregatorConfig.builder().providerName("EQUIFAX").integrationType(IntegrationCategory.BUREAU)
                                .priority(200).active(true).allowFallback(false).build()));
        when(equifaxProvider.getProviderName()).thenReturn("EQUIFAX");
        when(equifaxProvider.pullReport(any()))
                .thenReturn(new IBureauProvider.BureauPullResult(true, 720, Map.of("ok", true), "b1", null));

        var router = new IntegrationRouterServiceImpl(
                new HashMap<>(), Map.of("EQUIFAX", equifaxProvider), Map.of(),
                aggregatorConfigRepository, integrationCallbackProcessor,
                esignRequestTrackingService);

        var r = router.routeBureauRequest(UUID.randomUUID(), Map.of("applicationId", UUID.randomUUID().toString()));
        assertTrue(r.success());
        verify(equifaxProvider, times(1)).pullReport(any());
    }

    @Test
    void esignAgreement_triesEmsignerBeforeAuthbridgeEsign() {
        when(aggregatorConfigRepository.findActiveEsignRoutings(eq(IntegrationCategory.ESIGN), eq("ESIGN_AGREEMENT")))
                .thenReturn(List.of(
                        AggregatorConfig.builder().providerName("EMSIGNER").integrationType(IntegrationCategory.ESIGN)
                                .priority(200).active(true).allowFallback(true).build(),
                        AggregatorConfig.builder().providerName("AUTHBRIDGE_ESIGN").integrationType(IntegrationCategory.ESIGN)
                                .priority(190).active(true).allowFallback(true).build()));
        when(emsigner.getProviderName()).thenReturn("EMSIGNER");
        when(emsigner.initiateSigningRequest(any(IESignProvider.ESignInitRequest.class)))
                .thenReturn(new IESignProvider.ESignInitResult(true, "T1", "https://sign/x", null));

        Map<String, IESignProvider> eSign = new HashMap<>();
        eSign.put("EMSIGNER", emsigner);
        eSign.put("AUTHBRIDGE_ESIGN", authbridgeEsign);
        var esignRouter = new IntegrationRouterServiceImpl(
                new HashMap<>(), Map.of(), eSign, aggregatorConfigRepository, integrationCallbackProcessor,
                esignRequestTrackingService);

        UUID app = UUID.randomUUID();
        var r = esignRouter.routeESignRequest(app, Map.of(
                "documentKey", "KFS_AGREEMENT",
                "esignStepType", "ESIGN_AGREEMENT",
                "signerInfo", Map.of()));
        assertTrue(r.success());
        assertEquals("EMSIGNER", r.providerName());
        verify(emsigner, times(1)).initiateSigningRequest(any(IESignProvider.ESignInitRequest.class));
        verify(authbridgeEsign, never()).initiateSigningRequest(any());
    }
}
