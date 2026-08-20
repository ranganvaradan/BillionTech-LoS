package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.policystudio.parameters.manualoverride.ManualParameterOverrideSpine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInBureauMetricProducerTest {

    private final BuiltInBureauMetricProducer producer = new BuiltInBureauMetricProducer();

    @Test
    void manualOverride_suppliesValue_whenNoRealDataPresent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 20))
                .entity(ManualParameterOverrideSpine.MANUAL_PARAMETER_OVERRIDES,
                        Map.of(BureauMetricService.RECENT_INQUIRIES_90D, new BigDecimal("3")))
                .build();

        ExecutionResult result = producer.execute(BureauMetricService.RECENT_INQUIRIES_90D, ctx, null);

        assertThat(result.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(result.value()).isEqualTo(new BigDecimal("3"));
        assertThat(result.provenance().get("sourceType"))
                .isEqualTo(ManualParameterOverrideSpine.SOURCE_MANUAL_PARAMETER_OVERRIDE);
    }

    @Test
    void realPersistedMetric_takesPrecedenceOverManualOverride() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 20))
                .entity(PersistedDerivedMetricSpine.PRECOMPUTED_METRICS,
                        Map.of(BureauMetricService.RECENT_INQUIRIES_90D, new BigDecimal("7")))
                .entity(ManualParameterOverrideSpine.MANUAL_PARAMETER_OVERRIDES,
                        Map.of(BureauMetricService.RECENT_INQUIRIES_90D, new BigDecimal("3")))
                .build();

        ExecutionResult result = producer.execute(BureauMetricService.RECENT_INQUIRIES_90D, ctx, null);

        assertThat(result.value()).isEqualTo(new BigDecimal("7"));
        assertThat(result.provenance().get("sourceType"))
                .isEqualTo(PersistedDerivedMetricSpine.SOURCE_PERSISTED_CANONICAL_DERIVED);
    }

    @Test
    void noRealDataAndNoOverride_dataNotAvailable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 20))
                .build();

        ExecutionResult result = producer.execute(BureauMetricService.RECENT_INQUIRIES_90D, ctx, null);

        assertThat(result.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
    }
}
