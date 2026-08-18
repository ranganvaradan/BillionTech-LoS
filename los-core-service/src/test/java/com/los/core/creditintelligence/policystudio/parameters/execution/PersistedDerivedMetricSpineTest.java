package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistedDerivedMetricSpineTest {

    @Mock
    private CiMetricResultRepository metricResultRepository;

    private PersistedDerivedMetricSpine spine;
    private CanonicalParameterExecutionService cpes;

    @BeforeEach
    void setUp() {
        spine = new PersistedDerivedMetricSpine(metricResultRepository);
        cpes = ExecutionSpineProducerBootstrap.standalone((id, t) -> java.util.Optional.empty());
    }

    @Test
    void valuePresentPersistedDerived_sameCpesValueIncludingZero() {
        UUID reportId = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(reportId)).thenReturn(List.of(
                metric(reportId, BureauMetricService.OVERDUE_AGE_MONTHS, "PASS", 0, Instant.parse("2026-01-01T00:00:00Z")),
                metric(reportId, BureauMetricService.DPD_30_PLUS_COUNT_6M, "PASS", 0, Instant.parse("2026-01-01T00:00:01Z")),
                metric(reportId, BureauMetricService.RECENT_INQUIRIES_90D, "PASS", 1, Instant.parse("2026-01-01T00:00:02Z")),
                metric(reportId, BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT, "PASS", 0, Instant.parse("2026-01-01T00:00:03Z"))
        ));

        EvaluationContext ctx = contextFor(reportId);
        assertMatch(ctx, BureauMetricService.OVERDUE_AGE_MONTHS, 0);
        assertMatch(ctx, BureauMetricService.DPD_30_PLUS_COUNT_6M, 0);
        assertMatch(ctx, BureauMetricService.RECENT_INQUIRIES_90D, 1);
        assertMatch(ctx, BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT, 0);
        assertThat(mismatchCount(ctx, Map.of(
                BureauMetricService.OVERDUE_AGE_MONTHS, 0,
                BureauMetricService.DPD_30_PLUS_COUNT_6M, 0,
                BureauMetricService.RECENT_INQUIRIES_90D, 1,
                BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT, 0
        ))).isZero();
        verify(metricResultRepository).findByBureauReportId(reportId);
    }

    @Test
    void honestDataInsufficient_remainsDataInsufficient() {
        UUID reportId = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(reportId)).thenReturn(List.of(
                insufficient(reportId, BureauMetricService.WRITEOFF_NON_CC),
                insufficient(reportId, BureauMetricService.WRITEOFF_CC)
        ));
        EvaluationContext ctx = contextFor(reportId);
        ExecutionResult nonCc = cpes.resolveAndExecute(BureauMetricService.WRITEOFF_NON_CC, ctx);
        ExecutionResult cc = cpes.resolveAndExecute(BureauMetricService.WRITEOFF_CC, ctx);
        assertThat(nonCc.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(cc.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(nonCc.value()).isNull();
        assertThat(cc.value()).isNull();
        assertThat(nonCc.reason()).contains("DATA_INSUFFICIENT");
        assertThat(nonCc.reason()).contains("zero not invented");
    }

    @Test
    void zeroPersistedValue_isValuePresentZeroNotMissing() {
        UUID reportId = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(reportId)).thenReturn(List.of(
                metric(reportId, BureauMetricService.OVERDUE_AGE_MONTHS, "PASS", 0, Instant.parse("2026-02-01T00:00:00Z"))
        ));
        EvaluationContext ctx = contextFor(reportId);
        ExecutionResult er = cpes.resolveAndExecute(BureauMetricService.OVERDUE_AGE_MONTHS, ctx);
        assertThat(er.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(toNumber(er.value()).intValue()).isZero();
        assertThat(er.valueAvailable()).isTrue();
    }

    @Test
    void wrongReportId_doesNotBorrowValues() {
        UUID frozen = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(frozen)).thenReturn(List.of());
        EvaluationContext ctx = contextFor(frozen);
        ExecutionResult er = cpes.resolveAndExecute(BureauMetricService.RECENT_INQUIRIES_90D, ctx);
        assertThat(er.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(er.reason()).contains("no exact-ID value");
        verify(metricResultRepository).findByBureauReportId(frozen);
        org.mockito.Mockito.verify(metricResultRepository, org.mockito.Mockito.never())
                .findByBureauReportId(other);
    }

    @Test
    void historicalFrozenReport_doesNotUseNewerReport() {
        UUID frozen = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(frozen)).thenReturn(List.of(
                metric(frozen, BureauMetricService.RECENT_INQUIRIES_90D, "PASS", 1, Instant.parse("2026-01-01T00:00:00Z"))
        ));
        EvaluationContext ctx = contextFor(frozen);
        ExecutionResult er = cpes.resolveAndExecute(BureauMetricService.RECENT_INQUIRIES_90D, ctx);
        assertThat(toNumber(er.value()).intValue()).isEqualTo(1);
        org.mockito.Mockito.verify(metricResultRepository, org.mockito.Mockito.never())
                .findByApplicationIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(metricResultRepository, org.mockito.Mockito.never())
                .findByBureauReportId(newer);
    }

    @Test
    void providerIndependentCanonicalIdConsumption() {
        UUID reportId = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(reportId)).thenReturn(List.of(
                metric(reportId, BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT, "PASS", 2, Instant.parse("2026-03-01T00:00:00Z"))
        ));
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 3, 1))
                .applicationId(UUID.randomUUID())
                .entity("bureauProviderCode", "GENERIC_BUREAU")
                .build();
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 3, 1))
                .applicationId(ctx.applicationId());
        spine.bindExactReport(b, reportId);
        EvaluationContext bound = b.build();
        ExecutionResult er = cpes.resolveAndExecute(BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT, bound);
        assertThat(er.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(toNumber(er.value()).intValue()).isEqualTo(2);
        assertThat(er.provenance().get("sourceType"))
                .isEqualTo(PersistedDerivedMetricSpine.SOURCE_PERSISTED_CANONICAL_DERIVED);
        assertThat(String.valueOf(er.exactProducerPath())).doesNotContain("EQUIFAX");
    }

    @Test
    void readyValuePresentDerivedNotVisibleToCpesCount_isZero() {
        UUID reportId = UUID.randomUUID();
        when(metricResultRepository.findByBureauReportId(reportId)).thenReturn(List.of(
                metric(reportId, BureauMetricService.OVERDUE_AGE_MONTHS, "PASS", 0, Instant.now()),
                insufficient(reportId, BureauMetricService.WRITEOFF_NON_CC)
        ));
        EvaluationContext ctx = contextFor(reportId);
        int hidden = 0;
        ExecutionResult present = cpes.resolveAndExecute(BureauMetricService.OVERDUE_AGE_MONTHS, ctx);
        if (present.status() != ExecutionStatus.VALUE_AVAILABLE) {
            hidden++;
        }
        ExecutionResult di = cpes.resolveAndExecute(BureauMetricService.WRITEOFF_NON_CC, ctx);
        assertThat(di.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(hidden).isZero();
    }

    private EvaluationContext contextFor(UUID reportId) {
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .applicationId(UUID.randomUUID());
        spine.bindExactReport(b, reportId);
        return b.build();
    }

    private void assertMatch(EvaluationContext ctx, String id, int expected) {
        ExecutionResult er = cpes.resolveAndExecute(id, ctx);
        assertThat(er.status()).as(id).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(toNumber(er.value()).intValue()).as(id).isEqualTo(expected);
    }

    private int mismatchCount(EvaluationContext ctx, Map<String, Integer> expected) {
        int n = 0;
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            ExecutionResult er = cpes.resolveAndExecute(e.getKey(), ctx);
            if (er.status() != ExecutionStatus.VALUE_AVAILABLE
                    || toNumber(er.value()).intValue() != e.getValue()) {
                n++;
            }
        }
        return n;
    }

    private static Number toNumber(Object v) {
        if (v instanceof Number n) {
            return n;
        }
        return new java.math.BigDecimal(String.valueOf(v));
    }

    private static CiMetricResult metric(UUID reportId, String code, String outcome, int v, Instant createdAt) {
        return CiMetricResult.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .bureauReportId(reportId)
                .metricCode(code)
                .metricVersion(BureauMetricService.METRIC_VERSION)
                .outcome(outcome)
                .value(Map.of("v", v))
                .dataQualityStatus("OK")
                .createdAt(createdAt)
                .build();
    }

    private static CiMetricResult insufficient(UUID reportId, String code) {
        return CiMetricResult.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .bureauReportId(reportId)
                .metricCode(code)
                .metricVersion(BureauMetricService.METRIC_VERSION)
                .outcome(BureauMetricOutcome.DATA_INSUFFICIENT.name())
                .value(null)
                .dataQualityStatus("DATA_INSUFFICIENT")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
