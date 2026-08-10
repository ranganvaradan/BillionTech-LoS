package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.LegacyUnderwritingContextAdapter;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
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
class LegacyUnderwritingContextAdapterTaxTest {

    @Mock private CiFactSnapshotRepository snapshotRepository;
    @Mock private CiUnderwritingFactRepository factRepository;
    @Mock private CiMetricResultRepository metricResultRepository;
    @Mock private com.los.core.creditintelligence.evaluation.PinnedMetricLookup pinnedMetricLookup;

    private CreditIntelligenceProperties props;
    private LegacyUnderwritingContextAdapter adapter;

    @BeforeEach
    void setUp() {
        props = new CreditIntelligenceProperties();
        adapter = new LegacyUnderwritingContextAdapter(
                snapshotRepository, factRepository, metricResultRepository, props, pinnedMetricLookup);
    }

    @Test
    void overlaysItrIncomeWhenShadowFlagAndPass() {
        props.getCanonicalization().getTax().setUseForShadowRules(true);
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
                        .canonicalPath("compat.ITR_INCOME")
                        .valueType("DECIMAL")
                        .value(Map.of("v", new BigDecimal("450000")))
                        .classification(FactClassification.DEFAULTED.name())
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TURNOVER_LATEST)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(TaxMetricService.TURNOVER_LATEST)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("28921624")))
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TOTAL_INCOME_LATEST)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.PAT_ABS)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(TaxMetricService.PAT_ABS)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("1026039")))
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TOL_ABS)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TNW_ABS)))
                .thenReturn(Optional.empty());

        var adapted = adapter.adapt(snapshotId);
        assertThat(adapted.context().scorecard().get("ITR_INCOME"))
                .isEqualByComparingTo("28921624");
        assertThat(adapted.context().scorecard().get("PAT"))
                .isEqualByComparingTo("1026039");
        @SuppressWarnings("unchecked")
        Map<String, Object> flags = (Map<String, Object>) adapted.metadata().get("adapterFlags");
        @SuppressWarnings("unchecked")
        Map<String, Object> tax = (Map<String, Object>) flags.get("tax");
        assertThat(tax.get("useForShadowRules")).isEqualTo(true);
        assertThat(tax.get("ITR_INCOME_applied")).isEqualTo(true);
    }

    @Test
    void keepsLegacyWhenDataInsufficient() {
        props.getCanonicalization().getTax().setUseForShadowRules(true);
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
                        .canonicalPath("compat.ITR_INCOME")
                        .valueType("DECIMAL")
                        .value(Map.of("v", new BigDecimal("450000")))
                        .classification(FactClassification.DEFAULTED.name())
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TURNOVER_LATEST)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(TaxMetricService.TURNOVER_LATEST)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.DATA_INSUFFICIENT.name())
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TOTAL_INCOME_LATEST)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.PAT_ABS))).thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TOL_ABS))).thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(TaxMetricService.TNW_ABS))).thenReturn(Optional.empty());

        var adapted = adapter.adapt(snapshotId);
        assertThat(adapted.context().scorecard().get("ITR_INCOME"))
                .isEqualByComparingTo("450000");
    }
}
