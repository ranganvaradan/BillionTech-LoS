package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.service.GstMetricService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyUnderwritingContextAdapterGstTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiUnderwritingFactRepository factRepository;
    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private com.los.core.creditintelligence.evaluation.PinnedMetricLookup pinnedMetricLookup;

    private CreditIntelligenceProperties props;
    private LegacyUnderwritingContextAdapter adapter;

    @BeforeEach
    void setUp() {
        props = new CreditIntelligenceProperties();
        adapter = new LegacyUnderwritingContextAdapter(
                snapshotRepository, factRepository, metricResultRepository, props, pinnedMetricLookup);
    }

    @Test
    void overlaysAnnualGstTurnoverWhenShadowFlagAndPass() {
        props.getCanonicalization().getGst().setUseForShadowRules(true);
        UUID snapshotId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(CiFactSnapshot.builder()
                .id(snapshotId)
                .applicationId(appId)
                .status(SnapshotStatus.FROZEN.name())
                .metadata(Map.of(
                        "bureauSource", "PROVIDER",
                        "incomeSource", "PROVIDER",
                        "kycSource", "PROVIDER",
                        "applicationView", Map.of()))
                .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                CiUnderwritingFact.builder()
                        .canonicalPath("compat.ANNUAL_GST_TURNOVER")
                        .valueType("DECIMAL")
                        .value(Map.of("v", new BigDecimal("52000000")))
                        .classification(FactClassification.DEFAULTED.name())
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(GstMetricService.TRAILING_12M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(GstMetricService.TRAILING_12M)
                        .metricVersion("V1")
                        .outcome(GstMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("46542043.96")))
                        .build()));

        var result = adapter.adapt(snapshotId);
        assertThat(result.context().scorecard().get("ANNUAL_GST_TURNOVER"))
                .isEqualByComparingTo(new BigDecimal("46542043.96"));
        @SuppressWarnings("unchecked")
        Map<String, Object> gstFlags = (Map<String, Object>) ((Map<?, ?>) result.metadata().get("adapterFlags")).get("gst");
        assertThat(gstFlags.get("canonicalValueApplied")).isEqualTo(true);
    }

    @Test
    void keepsLegacyWhenDataInsufficient() {
        props.getCanonicalization().getGst().setUseForShadowRules(true);
        UUID snapshotId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(CiFactSnapshot.builder()
                .id(snapshotId)
                .applicationId(appId)
                .status(SnapshotStatus.FROZEN.name())
                .metadata(Map.of(
                        "bureauSource", "PROVIDER",
                        "incomeSource", "PROVIDER",
                        "kycSource", "PROVIDER",
                        "applicationView", Map.of()))
                .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                CiUnderwritingFact.builder()
                        .canonicalPath("compat.ANNUAL_GST_TURNOVER")
                        .valueType("DECIMAL")
                        .value(Map.of("v", new BigDecimal("52000000")))
                        .classification(FactClassification.DEFAULTED.name())
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(GstMetricService.TRAILING_12M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(GstMetricService.TRAILING_12M)
                        .metricVersion("V1")
                        .outcome(GstMetricOutcome.DATA_INSUFFICIENT.name())
                        .value(null)
                        .build()));

        var result = adapter.adapt(snapshotId);
        assertThat(result.context().scorecard().get("ANNUAL_GST_TURNOVER"))
                .isEqualByComparingTo(new BigDecimal("52000000"));
        @SuppressWarnings("unchecked")
        Map<String, Object> gstFlags = (Map<String, Object>) ((Map<?, ?>) result.metadata().get("adapterFlags")).get("gst");
        assertThat(gstFlags.get("fallbackUsed")).isEqualTo(true);
    }
}
