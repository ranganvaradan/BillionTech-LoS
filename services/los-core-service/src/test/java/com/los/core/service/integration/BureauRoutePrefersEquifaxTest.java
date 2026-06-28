package com.los.core.service.integration;

import com.los.core.model.enums.IntegrationCategory;
import com.los.core.repository.AggregatorConfigRepository;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.integration.webhook.IntegrationCallbackProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BureauRoutePrefersEquifaxTest {

    @Mock
    private AggregatorConfigRepository aggregatorConfigRepository;
    @Mock
    private IntegrationCallbackProcessor integrationCallbackProcessor;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;
    @Mock
    private IBureauProvider equifaxBureau;
    @Mock
    private IBureauProvider otherBureau;

    @BeforeEach
    void stubEsignReuse() {
        lenient().when(esignRequestTrackingService.findReusableSigningSession(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void whenNoAggregatorRouting_usesEquifaxBureauOverOthers() {
        when(aggregatorConfigRepository.findActiveByType(IntegrationCategory.BUREAU)).thenReturn(List.of());
        when(equifaxBureau.getProviderName()).thenReturn("EQUIFAX");
        when(equifaxBureau.pullReport(any()))
                .thenReturn(new IBureauProvider.BureauPullResult(true, 700, Map.of("ok", true), "tid", null));

        Map<String, IBureauProvider> bureauByName = new HashMap<>();
        bureauByName.put("KARZA", otherBureau);
        bureauByName.put("EQUIFAX", equifaxBureau);
        var router = new IntegrationRouterServiceImpl(
                Collections.emptyMap(),
                bureauByName,
                Collections.emptyMap(),
                aggregatorConfigRepository,
                integrationCallbackProcessor,
                esignRequestTrackingService
        );

        IIntegrationRouterService.BureauRouteResult r = router.routeBureauRequest(UUID.randomUUID(), Map.of("name", "X"));
        assertTrue(r.success());
        verify(equifaxBureau).pullReport(any());
        verify(otherBureau, never()).pullReport(any());
    }
}
