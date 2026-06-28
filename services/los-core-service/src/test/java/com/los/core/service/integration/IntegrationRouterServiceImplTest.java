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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntegrationRouterServiceImplTest {

    @Mock
    private AggregatorConfigRepository aggregatorConfigRepository;
    @Mock
    private IntegrationCallbackProcessor integrationCallbackProcessor;
    @Mock
    private IKycProvider karzaProvider;
    @Mock
    private IKycProvider authbridgeProvider;
    @Mock
    private IKycProvider perfiosProvider;
    @Mock
    private IESignProvider emsignerEsign;
    @Mock
    private IESignProvider authbridgeEsign;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;

    private Map<String, IKycProvider> kycProvidersByName;
    private IntegrationRouterServiceImpl router;

    @BeforeEach
    void setUp() {
        kycProvidersByName = new HashMap<>();
        kycProvidersByName.put("KARZA", karzaProvider);
        kycProvidersByName.put("AUTHBRIDGE", authbridgeProvider);
        kycProvidersByName.put("PERFIOS", perfiosProvider);
        when(karzaProvider.getProviderName()).thenReturn("KARZA");
        when(authbridgeProvider.getProviderName()).thenReturn("AUTHBRIDGE");
        when(perfiosProvider.getProviderName()).thenReturn("PERFIOS");

        lenient().when(esignRequestTrackingService.findReusableSigningSession(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        router = new IntegrationRouterServiceImpl(
                kycProvidersByName,
                Map.of(),
                Map.of(),
                aggregatorConfigRepository,
                integrationCallbackProcessor,
                esignRequestTrackingService);
    }

    @Test
    void routeKyc_workflowPreferenceTriesPreferredFirstWhenActiveInRouting() {
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of(
                        kycRule("KARZA", 100, true),
                        kycRule("AUTHBRIDGE", 90, true)));

        when(authbridgeProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(karzaProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(authbridgeProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        true, 0.99, Map.of("pan", "XXXX"), "tx-auth", null));

        IIntegrationRouterService.KycRouteResult r = router.routeKycRequest(
                UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of("panNumber", "ABCDE1234F"), "AUTHBRIDGE");

        assertTrue(r.success());
        assertEquals("AUTHBRIDGE", r.providerName());
        verify(authbridgeProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(karzaProvider, never()).verify(any(), anyMap());
    }

    @Test
    void routeKyc_noPreferenceUsesAggregatorOrder() {
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of(
                        kycRule("PERFIOS", 200, true),
                        kycRule("AUTHBRIDGE", 190, true)));

        when(perfiosProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(authbridgeProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(perfiosProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        true, 0.9, Map.of(), "tx-k", null));

        IIntegrationRouterService.KycRouteResult r = router.routeKycRequest(
                UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of(), null);

        assertTrue(r.success());
        assertEquals("PERFIOS", r.providerName());
        verify(perfiosProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(authbridgeProvider, never()).verify(any(), anyMap());
    }

    @Test
    void routeKyc_preferenceFallbacksToNextInChainOnFailure() {
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of(
                        kycRule("AUTHBRIDGE", 100, true),
                        kycRule("KARZA", 90, true)));

        when(authbridgeProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(karzaProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(authbridgeProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        false, 0, Map.of(), null, "fail"));
        when(karzaProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        true, 0.9, Map.of(), "tx2", null));

        IIntegrationRouterService.KycRouteResult r = router.routeKycRequest(
                UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of(), "AUTHBRIDGE");

        assertTrue(r.success());
        assertEquals("KARZA", r.providerName());
        verify(authbridgeProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(karzaProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
    }

    @Test
    void routeKyc_emptyRoutingTable_usesWorkflowPreferenceThenLegacy() {
        when(aggregatorConfigRepository.findActiveKycRoutings(eq(IntegrationCategory.KYC), eq("PAN_VERIFY")))
                .thenReturn(List.of());

        when(authbridgeProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(karzaProvider.supports(KycStepType.PAN_VERIFY)).thenReturn(true);
        when(karzaProvider.getPriority()).thenReturn(0);
        when(authbridgeProvider.getPriority()).thenReturn(0);
        when(authbridgeProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        false, 0, Map.of(), null, "auth-fail"));
        when(karzaProvider.verify(eq(KycStepType.PAN_VERIFY), anyMap()))
                .thenReturn(new IKycProvider.KycVerificationResult(
                        true, 0.8, Map.of(), "tx3", null));

        IIntegrationRouterService.KycRouteResult r = router.routeKycRequest(
                UUID.randomUUID(), KycStepType.PAN_VERIFY, Map.of(), "AUTHBRIDGE");

        assertTrue(r.success());
        assertEquals("KARZA", r.providerName());
        verify(authbridgeProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
        verify(karzaProvider, times(1)).verify(eq(KycStepType.PAN_VERIFY), anyMap());
    }

    @Test
    void routeEsign_usesEsignMapByProviderNameAndInitiateSigning() {
        Map<String, IESignProvider> eSign = new HashMap<>();
        eSign.put("EMSIGNER", emsignerEsign);
        eSign.put("AUTHBRIDGE_ESIGN", authbridgeEsign);
        when(emsignerEsign.getProviderName()).thenReturn("EMSIGNER");
        when(authbridgeEsign.getProviderName()).thenReturn("AUTHBRIDGE_ESIGN");
        when(aggregatorConfigRepository.findActiveEsignRoutings(eq(IntegrationCategory.ESIGN), anyString()))
                .thenReturn(List.of(
                        esignRule("EMSIGNER", 200, true),
                        esignRule("AUTHBRIDGE_ESIGN", 190, true)));
        when(aggregatorConfigRepository.findActiveByType(IntegrationCategory.ESIGN))
                .thenReturn(List.of());
        when(emsignerEsign.initiateSigningRequest(any(IESignProvider.ESignInitRequest.class)))
                .thenReturn(new IESignProvider.ESignInitResult(true, "ES-TX-1", "https://sign.example/1", null));

        IntegrationRouterServiceImpl esignRouter = new IntegrationRouterServiceImpl(
                kycProvidersByName,
                Map.of(),
                eSign,
                aggregatorConfigRepository,
                integrationCallbackProcessor,
                esignRequestTrackingService);

        UUID app = UUID.randomUUID();
        IIntegrationRouterService.ESignRouteResult r = esignRouter.routeESignRequest(app, Map.of(
                "documentKey", "KFS_AGREEMENT",
                "signerInfo", Map.of("name", "Test")));

        assertTrue(r.success());
        assertEquals("ES-TX-1", r.transactionId());
        assertEquals("https://sign.example/1", r.signingUrl());
        assertEquals("EMSIGNER", r.providerName());
        verify(emsignerEsign, times(1)).initiateSigningRequest(any(IESignProvider.ESignInitRequest.class));
        verify(authbridgeEsign, never()).initiateSigningRequest(any());
    }

    private static AggregatorConfig kycRule(String providerName, int priority, boolean allowFallback) {
        return AggregatorConfig.builder()
                .providerName(providerName)
                .integrationType(IntegrationCategory.KYC)
                .kycStepType(null)
                .priority(priority)
                .active(true)
                .allowFallback(allowFallback)
                .build();
    }

    private static AggregatorConfig esignRule(String providerName, int priority, boolean allowFallback) {
        return AggregatorConfig.builder()
                .providerName(providerName)
                .integrationType(IntegrationCategory.ESIGN)
                .kycStepType(null)
                .priority(priority)
                .active(true)
                .allowFallback(allowFallback)
                .build();
    }
}
