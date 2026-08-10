package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.service.CanonicalGstRuleEvaluator;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalGstRuleEvaluatorTest {

    private final CanonicalGstRuleEvaluator evaluator = new CanonicalGstRuleEvaluator();

    @Test
    void registrationStatus_activePass_inactiveFail_missingDi() {
        assertThat(evaluator.evaluateRegistrationStatus(List.of()).outcome())
                .isEqualTo(RuleOutcome.DATA_INSUFFICIENT.name());

        CiGstRegistration active = CiGstRegistration.builder()
                .id(UUID.randomUUID()).gstin("29X").registrationStatus("ACTIVE")
                .parserVersion("v").normalizerVersion("v").tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID()).sourceRecordId(UUID.randomUUID()).build();
        assertThat(evaluator.evaluateRegistrationStatus(List.of(active)).outcome())
                .isEqualTo(RuleOutcome.PASS.name());

        CiGstRegistration inactive = CiGstRegistration.builder()
                .id(UUID.randomUUID()).gstin("29Y").registrationStatus("INACTIVE")
                .parserVersion("v").normalizerVersion("v").tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID()).sourceRecordId(UUID.randomUUID()).build();
        assertThat(evaluator.evaluateRegistrationStatus(List.of(inactive)).outcome())
                .isEqualTo(RuleOutcome.FAIL.name());
    }

    @Test
    void turnoverEligibility_usesTrailing12m_shadowOnly() {
        CiMetricResult di = CiMetricResult.builder()
                .metricCode(GstMetricService.TRAILING_12M)
                .metricVersion("V1")
                .outcome(GstMetricOutcome.DATA_INSUFFICIENT.name())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .build();
        assertThat(evaluator.evaluateTurnoverEligibility(
                Map.of(GstMetricService.TRAILING_12M, di), new BigDecimal("50000000")).outcome())
                .isEqualTo(RuleOutcome.DATA_INSUFFICIENT.name());

        CiMetricResult pass = CiMetricResult.builder()
                .metricCode(GstMetricService.TRAILING_12M)
                .metricVersion("V1")
                .outcome(GstMetricOutcome.PASS.name())
                .value(Map.of("v", new BigDecimal("52000000")))
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .build();
        assertThat(evaluator.evaluateTurnoverEligibility(
                Map.of(GstMetricService.TRAILING_12M, pass), new BigDecimal("50000000")).outcome())
                .isEqualTo(RuleOutcome.PASS.name());

        CiMetricResult fail = CiMetricResult.builder()
                .metricCode(GstMetricService.TRAILING_12M)
                .metricVersion("V1")
                .outcome(GstMetricOutcome.PASS.name())
                .value(Map.of("v", new BigDecimal("1000000")))
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .build();
        assertThat(evaluator.evaluateTurnoverEligibility(
                Map.of(GstMetricService.TRAILING_12M, fail), new BigDecimal("50000000")).outcome())
                .isEqualTo(RuleOutcome.FAIL.name());
    }

    @Test
    void varianceRule_mapsMetricOutcomes() {
        CiMetricResult match = CiMetricResult.builder()
                .metricCode(GstMetricService.VARIANCE)
                .metricVersion("V1")
                .outcome(GstMetricOutcome.MATCH.name())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .build();
        assertThat(evaluator.evaluateVariance(Map.of(GstMetricService.VARIANCE, match)).outcome())
                .isEqualTo(RuleOutcome.PASS.name());

        CiMetricResult conflict = CiMetricResult.builder()
                .metricCode(GstMetricService.VARIANCE)
                .metricVersion("V1")
                .outcome(GstMetricOutcome.CONFLICT.name())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .build();
        assertThat(evaluator.evaluateVariance(Map.of(GstMetricService.VARIANCE, conflict)).outcome())
                .isEqualTo(RuleOutcome.FAIL.name());
    }
}
