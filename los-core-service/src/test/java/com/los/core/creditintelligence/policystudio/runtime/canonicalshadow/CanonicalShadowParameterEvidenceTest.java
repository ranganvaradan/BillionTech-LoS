package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalShadowParameterEvidenceTest {

    @Test
    void everyParticipatingOperand_hasCpesValueNotJustPrimaryActualExecution() {
        CanonicalParameterExecutionService cpes =
                ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        CanonicalPolicyRuntime runtime = new CanonicalPolicyRuntime(cpes);
        UUID reportId = UUID.randomUUID();
        EvaluationContext spine = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact(BureauMetricService.OVERDUE_AGE_MONTHS, 0)
                .fact(BureauMetricService.RECENT_INQUIRIES_90D, 1)
                .build();
        CanonicalPolicyResult policy = runtime.evaluate(new CanonicalPolicyRuntime.PolicyRequest(
                "northwind-policy",
                "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec(
                        "two-operands",
                        PolicyDsl.and(
                                PolicyDsl.lte(PolicyDsl.metric(BureauMetricService.OVERDUE_AGE_MONTHS),
                                        Map.of("const", 0)),
                                PolicyDsl.lte(PolicyDsl.metric(BureauMetricService.RECENT_INQUIRIES_90D),
                                        Map.of("const", 3))))),
                spine,
                LocalDate.of(2026, 1, 15)));
        assertThat(policy.ruleResults()).hasSize(1);
        assertThat(policy.ruleResults().get(0).canonicalParameterIds())
                .contains(BureauMetricService.OVERDUE_AGE_MONTHS, BureauMetricService.RECENT_INQUIRIES_90D);
        String primary = policy.ruleResults().get(0).actualExecution() == null
                ? null : policy.ruleResults().get(0).actualExecution().canonicalParameterId();

        CanonicalApplicationConfiguration freeze = freeze(reportId);
        List<Map<String, Object>> rows = CanonicalShadowParameterEvidence.rows(policy, freeze, spine, cpes);
        assertThat(rows).hasSize(2);
        Map<String, Map<String, Object>> byId = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byId.put(String.valueOf(row.get("parameterId")), row);
        }
        Map<String, Object> overdue = byId.get(BureauMetricService.OVERDUE_AGE_MONTHS);
        Map<String, Object> inquiries = byId.get(BureauMetricService.RECENT_INQUIRIES_90D);
        assertThat(overdue.get("canonicalStatus")).isEqualTo("VALUE_AVAILABLE");
        assertThat(inquiries.get("canonicalStatus")).isEqualTo("VALUE_AVAILABLE");
        assertThat(new java.math.BigDecimal(String.valueOf(overdue.get("canonicalValue"))).intValue()).isZero();
        assertThat(new java.math.BigDecimal(String.valueOf(inquiries.get("canonicalValue"))).intValue()).isEqualTo(1);
        assertThat(overdue.get("sourceReportId")).isEqualTo(reportId.toString());
        String secondary = BureauMetricService.OVERDUE_AGE_MONTHS.equals(primary)
                ? BureauMetricService.RECENT_INQUIRIES_90D : BureauMetricService.OVERDUE_AGE_MONTHS;
        assertThat(byId.get(secondary).get("canonicalStatus")).isEqualTo("VALUE_AVAILABLE");
    }

    @Test
    void freezeFromMap_retainsBureauReportId() {
        UUID reportId = UUID.randomUUID();
        CanonicalApplicationConfiguration freeze = freeze(reportId);
        CanonicalApplicationConfiguration roundTrip = CanonicalApplicationConfiguration.fromMap(freeze.toMap());
        assertThat(roundTrip.bureauReportId()).isEqualTo(reportId);
        assertThat(roundTrip.evaluationAsOf()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    private static CanonicalApplicationConfiguration freeze(UUID bureauReportId) {
        return new CanonicalApplicationConfiguration(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "NORTHWIND",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "v1",
                UUID.randomUUID(),
                1,
                true,
                false,
                bureauReportId,
                "GENERIC",
                "v1",
                "v1",
                LocalDate.of(2026, 1, 15),
                List.of(),
                Instant.parse("2026-01-15T00:00:00Z"),
                Map.of("source", "northwind-fixture"));
    }
}
