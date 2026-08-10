package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
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
class LegacyUnderwritingContextAdapterBankingTest {

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
    void overlaysAverageBankBalanceWhenShadowFlagAndPass() {
        props.getCanonicalization().getBanking().setUseForShadowRules(true);
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
                        "kycOutcome", "PASS",
                        "applicationView", Map.of("applicationNumber", "APP-B")))
                .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                compat("compat.AVERAGE_BANK_BALANCE", new BigDecimal("120000")),
                compat("compat.BUREAU_SCORE", new BigDecimal("720")),
                compat("compat.MONTHLY_INCOME", new BigDecimal("100000")),
                compat("compat.EMI_OBLIGATION", new BigDecimal("15000")),
                compat("compat.KYC_SUCCESS", BigDecimal.ONE)));

        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.ADB_3M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .applicationId(appId)
                        .metricCode(BankingMetricService.ADB_3M)
                        .metricVersion("V1")
                        .outcome(BankingMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("250000")))
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.MONTHLY_OBL)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .applicationId(appId)
                        .metricCode(BankingMetricService.MONTHLY_OBL)
                        .metricVersion("V1")
                        .outcome(BankingMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("45000")))
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.ADJ_12M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .applicationId(appId)
                        .metricCode(BankingMetricService.ADJ_12M)
                        .metricVersion("V1")
                        .outcome(BankingMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("4800000")))
                        .build()));

        var result = adapter.adapt(snapshotId);
        assertThat(result.context().scorecard().get("AVERAGE_BANK_BALANCE"))
                .isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(result.context().scorecard().get("EMI_OBLIGATION"))
                .isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(result.context().scorecard().get("ANNUAL_BANKING_TURNOVER"))
                .isEqualByComparingTo(new BigDecimal("4800000"));
    }

    @Test
    void keepsLegacyWhenCanonicalDi() {
        props.getCanonicalization().getBanking().setUseForShadowRules(true);
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
                        "kycOutcome", "PASS",
                        "applicationView", Map.of()))
                .build()));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(List.of(
                compat("compat.AVERAGE_BANK_BALANCE", new BigDecimal("120000")),
                compat("compat.BUREAU_SCORE", new BigDecimal("720")),
                compat("compat.MONTHLY_INCOME", new BigDecimal("100000")),
                compat("compat.EMI_OBLIGATION", new BigDecimal("15000")),
                compat("compat.KYC_SUCCESS", BigDecimal.ONE)));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.ADB_3M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .outcome(BankingMetricOutcome.DATA_INSUFFICIENT.name())
                        .metricCode(BankingMetricService.ADB_3M)
                        .metricVersion("V1")
                        .applicationId(appId)
                        .build()));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.MONTHLY_OBL)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(appId), eq(BankingMetricService.ADJ_12M)))
                .thenReturn(Optional.empty());

        var result = adapter.adapt(snapshotId);
        assertThat(result.context().scorecard().get("AVERAGE_BANK_BALANCE"))
                .isEqualByComparingTo(new BigDecimal("120000"));
        @SuppressWarnings("unchecked")
        Map<String, Object> flags = (Map<String, Object>) result.metadata().get("adapterFlags");
        @SuppressWarnings("unchecked")
        Map<String, Object> banking = (Map<String, Object>) flags.get("banking");
        assertThat(banking.get("AVERAGE_BANK_BALANCE_diFallback")).isEqualTo(true);
    }

    private static CiUnderwritingFact compat(String path, BigDecimal value) {
        return CiUnderwritingFact.builder()
                .canonicalPath(path)
                .valueType("DECIMAL")
                .value(Map.of("v", value))
                .classification(FactClassification.EXTRACTED.name())
                .build();
    }
}
