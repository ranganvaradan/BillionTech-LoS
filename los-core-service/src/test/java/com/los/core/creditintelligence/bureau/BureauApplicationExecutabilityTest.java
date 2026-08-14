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
class BureauApplicationExecutabilityTest {

    @Mock private CiBureauReportRepository reportRepository;
    @Mock private CiMetricResultRepository metricResultRepository;

    private BureauApplicationExecutabilityService service;
    private UUID appId;

    @BeforeEach
    void setUp() {
        BureauDataReadinessProbe probe = new BureauDataReadinessProbe(reportRepository, metricResultRepository);
        service = new BureauApplicationExecutabilityService(probe);
        appId = UUID.randomUUID();
    }

    @Test
    void gate3ExecutabilityRemainsAuthoritativeWhenEvidenceMissing() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.empty());

        Map<String, Object> result = service.evaluateForApplication(appId, "bureau.max_dpd_6m");

        assertThat(result.get("executionState"))
                .isEqualTo(ParameterExecutabilitySupport.RUNTIME_READY_NONPROD);
        assertThat(result.get("bureauEvidence"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.NO_BUREAU_PULL.name());
        assertThat(result.get("applicationDataQuality")).isEqualTo("NO_BUREAU_PULL");
    }

    @Test
    void evidenceReadyDoesNotUpgradeToProductionReady() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(CiBureauReport.builder()
                        .id(reportId)
                        .applicationId(appId)
                        .tradelinesPresent(true)
                        .tradelineExtractionStatus("OK")
                        .score(758)
                        .build()));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.MAX_DPD_6M)))
                .thenReturn(Optional.of(CiMetricResult.builder()
                        .metricCode(BureauMetricService.MAX_DPD_6M)
                        .outcome(BureauMetricOutcome.PASS.name())
                        .value(Map.of("v", 12))
                        .build()));

        Map<String, Object> result = service.evaluateForApplication(appId, "bureau.max_dpd_6m");

        assertThat(result.get("executionState"))
                .isEqualTo(ParameterExecutabilitySupport.RUNTIME_READY_NONPROD);
        assertThat(result.get("productionReady")).isEqualTo(false);
        assertThat(result.get("bureauEvidence"))
                .isEqualTo(BureauDataReadinessProbe.BureauReadiness.READY.name());
        assertThat(result.get("applicationDataQuality")).isEqualTo("PASS");
    }
}
