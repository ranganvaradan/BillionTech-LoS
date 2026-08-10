package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.LegacyUnderwritingContextAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyUnderwritingContextAdapterBureauTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiUnderwritingFactRepository factRepository;
    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private com.los.core.creditintelligence.evaluation.PinnedMetricLookup pinnedMetricLookup;

    private CreditIntelligenceProperties properties;
    private LegacyUnderwritingContextAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getCanonicalization().getBureau().setUseForShadowRules(true);
        adapter = new LegacyUnderwritingContextAdapter(
                snapshotRepository, factRepository, metricResultRepository, properties, pinnedMetricLookup);
    }

    @Test
    void appliesCanonicalLiveUnsecuredWhenAvailable() {
        UUID snapshotId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(
                CiFactSnapshot.builder()
                        .id(snapshotId)
                        .applicationId(appId)
                        .status(SnapshotStatus.FROZEN.name())
                        .metadata(Map.of("kycOutcome", "PASS", "bureauSource", "PROVIDER"))
                        .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                CiUnderwritingFact.builder()
                        .canonicalPath("compat.LIVE_UNSECURED_LOAN_COUNT")
                        .value(Map.of("v", "2"))
                        .classification(FactClassification.DEFAULTED.name())
                        .build(),
                CiUnderwritingFact.builder()
                        .canonicalPath("compat.BUREAU_SCORE")
                        .value(Map.of("v", "720"))
                        .classification(FactClassification.VERIFIED.name())
                        .build()
        ));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BureauMetricService.LIVE_UNSECURED)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(BureauMetricService.LIVE_UNSECURED)
                        .metricVersion("V1")
                        .outcome(BureauMetricOutcome.PASS.name())
                        .value(Map.of("v", 4))
                        .build()));

        var result = adapter.adapt(snapshotId);
        assertEquals(new BigDecimal("4"), result.context().scorecard().get("LIVE_UNSECURED_LOAN_COUNT"));
        @SuppressWarnings("unchecked")
        Map<String, Object> flags = (Map<String, Object>) result.metadata().get("adapterFlags");
        assertTrue(Boolean.TRUE.equals(flags.get("canonicalValueApplied")));
        assertFalse(Boolean.TRUE.equals(flags.get("fallbackUsed")));
    }

    @Test
    void dataInsufficientKeepsLegacyAndFlagsFallback() {
        UUID snapshotId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(
                CiFactSnapshot.builder()
                        .id(snapshotId)
                        .applicationId(appId)
                        .status(SnapshotStatus.FROZEN.name())
                        .metadata(Map.of("kycOutcome", "PASS"))
                        .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                CiUnderwritingFact.builder()
                        .canonicalPath("compat.LIVE_UNSECURED_LOAN_COUNT")
                        .value(Map.of("v", "2"))
                        .classification(FactClassification.DEFAULTED.name())
                        .build()
        ));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(BureauMetricService.LIVE_UNSECURED)
                        .outcome(BureauMetricOutcome.DATA_INSUFFICIENT.name())
                        .value(null)
                        .build()));

        var result = adapter.adapt(snapshotId);
        assertEquals(new BigDecimal("2"), result.context().scorecard().get("LIVE_UNSECURED_LOAN_COUNT"));
        @SuppressWarnings("unchecked")
        Map<String, Object> flags = (Map<String, Object>) result.metadata().get("adapterFlags");
        assertTrue(Boolean.TRUE.equals(flags.get("canonicalDataInsufficient")));
        assertTrue(Boolean.TRUE.equals(flags.get("fallbackUsed")));
    }
}
