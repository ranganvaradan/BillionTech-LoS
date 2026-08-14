package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUREAU-P0-STEP1 — Equifax sentinel -1 / NTC reach bridge and Policy Test OR(-1, NTC, &gt;=650) parity.
 */
@ExtendWith(MockitoExtension.class)
class CanonicalBureauScoreNtcStep1Test {

    @Mock private CiBureauReportRepository reportRepository;
    @Mock private CiMetricResultRepository metricResultRepository;

    private CreditIntelligenceProperties properties;
    private CanonicalBureauContextBridge bridge;
    private UUID appId;
    private UUID reportId;

    /** Same golden as DeterministicGoldenInterpretationProvider / Gate1 compound. */
    private static final Map<String, Object> COMPOUND = PolicyDsl.or(
            PolicyDsl.eq(PolicyDsl.metric("bureau.score"), -1),
            PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), true),
            PolicyDsl.gte(PolicyDsl.metric("bureau.score"), 650));

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getCanonicalization().getBureau().setEnabled(true);
        properties.getCanonicalization().getBureau().setUseForProductionUnderwriting(true);
        bridge = new CanonicalBureauContextBridge(reportRepository, metricResultRepository, properties);
        appId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        lenient().when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                        any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void bridge_overlaysSentinelMinusOneAndNtcFlag() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(-1, "EMPTY")));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.STATUS_NTC)))
                .thenReturn(Optional.of(metric(BureauMetricService.STATUS_NTC, 1)));

        var result = bridge.overlay(app()).orElseThrow();

        assertThat(result.bureauScore()).isEqualTo(-1);
        assertThat(result.scorecard().get("BUREAU_SCORE")).isEqualByComparingTo("-1");
        assertThat(result.scorecard().get("NTC_FLAG")).isEqualByComparingTo("1");
        assertThat(result.provenance().get("NTC_FLAG")).isEqualTo(ScorecardValueProvenance.CANONICAL);
    }

    @Test
    void bridge_doesNotAcceptArbitraryNegativeScore() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(-5, "OK")));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.STATUS_NTC)))
                .thenReturn(Optional.of(metric(BureauMetricService.STATUS_NTC, 0)));

        var result = bridge.overlay(app());
        assertThat(result).isPresent();
        assertThat(result.get().bureauScore()).isNull();
        assertThat(result.get().scorecard()).doesNotContainKey("BUREAU_SCORE");
    }

    @Test
    void metricService_noRecordFound_setsNtcTrue() {
        BureauMetricService metricSvc = new BureauMetricService(
                metricResultRepository, mock(CiBureauPaymentHistoryRepository.class));
        CiMetricResult ntc = metricSvc.computeStatusNtc(
                report(-1, "EMPTY"), Map.of("noRecordFound", true, "creditScore", -1));
        assertThat(ntc.getOutcome()).isEqualTo(BureauMetricOutcome.PASS.name());
        assertThat(ntc.getValue().get("v")).isEqualTo(1);
    }

    @Test
    void metricService_positiveScore_setsNtcFalse() {
        BureauMetricService metricSvc = new BureauMetricService(
                metricResultRepository, mock(CiBureauPaymentHistoryRepository.class));
        CiMetricResult ntc = metricSvc.computeStatusNtc(report(720, "OK"), Map.of("creditScore", 720));
        assertThat(ntc.getOutcome()).isEqualTo(BureauMetricOutcome.PASS.name());
        assertThat(ntc.getValue().get("v")).isEqualTo(0);
    }

    @Test
    void metricService_missingScore_isDataInsufficientNotNtc() {
        BureauMetricService metricSvc = new BureauMetricService(
                metricResultRepository, mock(CiBureauPaymentHistoryRepository.class));
        CiBureauReport r = report(0, "OK");
        r.setScore(null);
        CiMetricResult ntc = metricSvc.computeStatusNtc(r, Map.of());
        assertThat(ntc.getOutcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void policyTest_compoundParity_cases() {
        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();
        assertDecision(interp, Map.of("bureau.score", 720), Map.of("bureau.status_ntc", false), true);
        assertDecision(interp, Map.of("bureau.score", 649), Map.of("bureau.status_ntc", false), false);
        assertDecision(interp, Map.of("bureau.score", 650), Map.of("bureau.status_ntc", false), true);
        assertDecision(interp, Map.of("bureau.score", -1), Map.of("bureau.status_ntc", true), true);
        // Case E: -1 without NTC — ALLOW via EQ(score,-1); does not invent NTC
        assertDecision(interp, Map.of("bureau.score", -1), Map.of("bureau.status_ntc", false), true);
    }

    @Test
    void liveEquivalent_fromBridgeOverlay_matchesPolicyTest() {
        when(reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId))
                .thenReturn(Optional.of(report(-1, "EMPTY")));
        when(metricResultRepository.findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
                eq(reportId), eq(BureauMetricService.STATUS_NTC)))
                .thenReturn(Optional.of(metric(BureauMetricService.STATUS_NTC, 1)));

        var overlay = bridge.overlay(app()).orElseThrow();
        Map<String, Object> metrics = Map.of("bureau.score", overlay.bureauScore());
        Map<String, Object> facts = Map.of(
                "bureau.status_ntc",
                overlay.scorecard().get("NTC_FLAG").intValue() == 1);

        assertDecision(new PolicyDslInterpreterV1(), metrics, facts, true);
    }

    @Test
    void missingScore_policyTest_dataInsufficient() {
        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        String outcome = interp.evaluate(COMPOUND, ctx);
        assertThat(outcome).isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);
    }

    private static void assertDecision(
            PolicyDslInterpreterV1 interp,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            boolean expectedAllow) {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(metrics, facts, null);
        String outcome = interp.evaluate(COMPOUND, ctx);
        boolean allow = PolicyDslInterpreterV1.PASS.equals(outcome);
        assertThat(allow).as("EXPECTED=%s ACTUAL=%s metrics=%s facts=%s",
                expectedAllow ? "ALLOW" : "REJECT", outcome, metrics, facts).isEqualTo(expectedAllow);
    }

    private LoanApplication app() {
        return LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-NTC-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .build();
    }

    private CiBureauReport report(int score, String extraction) {
        Integer scoreVal = score == 0 ? null : score;
        return CiBureauReport.builder()
                .id(reportId)
                .tenantId(properties.getDefaultTenantId())
                .applicationId(appId)
                .sourceRecordId(UUID.randomUUID())
                .subjectType("CONSUMER")
                .providerCode("EQUIFAX")
                .parserVersion("EQUIFAX_PARSER_V2")
                .normalizerVersion("BUREAU_NORMALIZER_V1")
                .tradelinesPresent(score == -1)
                .tradelineExtractionStatus(extraction)
                .score(scoreVal)
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
