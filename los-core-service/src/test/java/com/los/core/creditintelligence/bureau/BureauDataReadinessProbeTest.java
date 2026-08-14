package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.service.BureauApplicationExecutabilityService;
import com.los.core.creditintelligence.bureau.service.BureauDataReadinessProbe;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BureauDataReadinessProbeTest {

    @Mock private CiBureauReportRepository reportRepository;
    @Mock private CiMetricResultRepository metricResultRepository;

    private BureauDataReadinessProbe probe;
    private UUID appId;
    private UUID reportId;

    @BeforeEach
    void setUp() {
        probe = new BureauDataReadinessProbe(reportRepository, metricResultRepository);
        appId = UUID.randomUUID();
        reportId = UUID.randomUUID();
    }

    @Test
    void readiness_noPull_returnsNoBureauPull() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.empty());
        assertThat(probe.readinessForPath(appId, "bureau.score"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.NO_BUREAU_PULL);
    }

    @Test
    void readiness_scoreReadyWhenReportHasScore() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(758)));
        assertThat(probe.readinessForPath(appId, "bureau.score"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.READY);
    }

    @Test
    void readiness_metricReadyWhenPassMetricExists() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(758)));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.LIVE_UNSECURED)))
                .thenReturn(Optional.of(passMetric(2)));

        assertThat(probe.readinessForPath(appId, "bureau.live_unsecured_loan_count"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.READY);
    }

    @Test
    void readiness_metricIncompleteWhenDataInsufficient() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(758)));
        CiMetricResult m = passMetric(0);
        m.setOutcome(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        m.setValue(null);
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.MAX_DPD_6M)))
                .thenReturn(Optional.of(m));

        assertThat(probe.readinessForPath(appId, "bureau.max_dpd_6m"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.INGEST_INCOMPLETE);
    }

    private CiBureauReport report(int score) {
        return CiBureauReport.builder()
                .id(reportId)
                .tenantId(UUID.randomUUID())
                .applicationId(appId)
                .sourceRecordId(UUID.randomUUID())
                .subjectType("CONSUMER")
                .providerCode("EQUIFAX")
                .parserVersion("EQUIFAX_PARSER_V2")
                .normalizerVersion("BUREAU_NORMALIZER_V1")
                .tradelinesPresent(true)
                .tradelineExtractionStatus("OK")
                .score(score)
                .build();
    }

    private static CiMetricResult passMetric(int v) {
        return CiMetricResult.builder()
                .metricCode(BureauMetricService.LIVE_UNSECURED)
                .outcome(BureauMetricOutcome.PASS.name())
                .value(Map.of("v", v))
                .build();
    }
}
