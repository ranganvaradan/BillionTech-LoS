package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalBureauContextBridgeTest {

    @Mock private CiBureauReportRepository reportRepository;
    @Mock private CiMetricResultRepository metricResultRepository;

    private CreditIntelligenceProperties properties;
    private CanonicalBureauContextBridge bridge;
    private UUID appId;
    private UUID reportId;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getCanonicalization().getBureau().setEnabled(true);
        properties.getCanonicalization().getBureau().setUseForProductionUnderwriting(true);
        bridge = new CanonicalBureauContextBridge(reportRepository, metricResultRepository, properties);
        appId = UUID.randomUUID();
        reportId = UUID.randomUUID();
    }

    @Test
    void overlay_mapsPassMetricsAndReportScore() {
        LoanApplication app = app();
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(758)));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.LIVE_UNSECURED)))
                .thenReturn(Optional.of(metric(BureauMetricService.LIVE_UNSECURED, 3)));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.MAX_DPD_6M)))
                .thenReturn(Optional.of(metric(BureauMetricService.MAX_DPD_6M, 71)));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.MAX_DPD_12M)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.RECENT_INQUIRIES_90D)))
                .thenReturn(Optional.empty());
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.TOTAL_MONTHLY_OBLIGATION)))
                .thenReturn(Optional.empty());

        var result = bridge.overlay(app).orElseThrow();

        assertThat(result.bureauScore()).isEqualTo(758);
        assertThat(result.scorecard().get("BUREAU_SCORE")).isEqualByComparingTo("758");
        assertThat(result.scorecard().get("LIVE_UNSECURED_LOAN_COUNT")).isEqualByComparingTo("3");
        assertThat(result.scorecard().get("MAX_DPD_6M")).isEqualByComparingTo("71");
        assertThat(result.provenance().get("LIVE_UNSECURED_LOAN_COUNT"))
                .isEqualTo(ScorecardValueProvenance.CANONICAL);
    }

    @Test
    void overlay_skipsDataInsufficientMetrics() {
        LoanApplication app = app();
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(720)));
        CiMetricResult insufficient = metric(BureauMetricService.LIVE_UNSECURED, 2);
        insufficient.setOutcome(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        insufficient.setValue(null);
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.LIVE_UNSECURED)))
                .thenReturn(Optional.of(insufficient));

        var result = bridge.overlay(app).orElseThrow();

        assertThat(result.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
        assertThat(result.bureauScore()).isEqualTo(720);
    }

    @Test
    void overlay_emptyWhenExtractionMissing() {
        LoanApplication app = app();
        CiBureauReport bad = report(700);
        bad.setTradelineExtractionStatus("MISSING");
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(bad));

        assertThat(bridge.overlay(app)).isEmpty();
    }

    private LoanApplication app() {
        return LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("UNSECURED_WC")
                .build();
    }

    private CiBureauReport report(int score) {
        return CiBureauReport.builder()
                .id(reportId)
                .tenantId(properties.getDefaultTenantId())
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

    private static CiMetricResult metric(String code, int value) {
        return CiMetricResult.builder()
                .metricCode(code)
                .metricVersion("V1")
                .outcome(BureauMetricOutcome.PASS.name())
                .value(Map.of("v", value))
                .build();
    }
}
