package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 1 spine: exact-ID producers, authored recursion, semantic refusal for invalid dpd def.
 */
class CanonicalParameterExecutionServiceTest {

    private DerivedCalculationDefinitionService definitions;
    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());
        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
    }

    @Test
    void rawScoreExecutesFromFactsOnly() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 720)
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.score", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(r.value()).isEqualTo(720);
        assertThat(r.producerType()).isEqualTo(ProducerType.RAW);
        assertThat(r.capability()).isTrue();
        assertThat(r.exactProducerPath()).contains("RawFactProducer");
    }

    @Test
    void builtInInquiriesCapableButDataMissingWithoutFacts() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        assertThat(spine.hasExecutionCapability("bureau.recent_inquiries_90d", ctx)).isTrue();
        ExecutionResult r = spine.resolveAndExecute("bureau.recent_inquiries_90d", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.capability()).isTrue();
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void builtInExactIdFromFacts() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .fact("bureau.settled_account_count", 2)
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.settled_account_count", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(r.value()).isEqualTo(2);
        assertThat(r.provenance().get("metricCode")).isEqualTo("bureau.settled_account_count");
    }

    @Test
    void missingProducerNotExecutableEvenIfCatalogueWouldClaimImplemented() {
        EvaluationContext ctx = EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build();
        ExecutionResult overdue = spine.resolveAndExecute("bureau.thin_file_indicator", ctx);
        assertThat(overdue.status()).isEqualTo(ExecutionStatus.NOT_EXECUTABLE);
        assertThat(overdue.capability()).isFalse();

        ExecutionResult cc = spine.resolveAndExecute("bureau.cc_overdue_amount", ctx);
        assertThat(cc.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(cc.capability()).isTrue();

        ExecutionResult age = spine.resolveAndExecute("bureau.overdue.age_months", ctx);
        assertThat(age.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
    }

    @Test
    void authoredCleanHistoryExecutesWhenPaymentHistorySupplied() {
        Map<String, Object> expr = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GT",
                "matchValue", 0,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        CiGacatDerivedCalculationDefinition def = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(expr)
                .dependencyIds(List.of("bureau.tradeline.payment_history"))
                .versionNo(1)
                .build();
        when(definitions.latestFor(eq("bureau.credit_after_overdue.clean_history_months"), any()))
                .thenReturn(Optional.of(def));

        List<Map<String, Object>> history = List.of(
                Map.of("month", "2025-01-01", "dpd", 30),
                Map.of("month", "2025-06-01", "dpd", 0),
                Map.of("month", "2026-01-01", "dpd", 0));
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.tradeline.payment_history", history)
                .build();

        ExecutionResult r = spine.resolveAndExecute(
                "bureau.credit_after_overdue.clean_history_months", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
        assertThat(r.capability()).isTrue();

        EvaluationContext withExact = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.credit_after_overdue.clean_history_months", 6)
                .build();
        ExecutionResult exact = spine.resolveAndExecute(
                "bureau.credit_after_overdue.clean_history_months", withExact);
        assertThat(exact.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(exact.value()).isEqualTo(6);
        assertThat(exact.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void authoredDpd30WithMonthsSinceIsNotExecutable() {
        Map<String, Object> badExpr = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GTE",
                "matchValue", 30,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        CiGacatDerivedCalculationDefinition def = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                .status(DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY)
                .expressionJson(badExpr)
                .dependencyIds(List.of("bureau.tradeline.payment_history"))
                .versionNo(1)
                .build();
        when(definitions.latestFor(eq("bureau.dpd_30_plus_count_6m"), any()))
                .thenReturn(Optional.of(def));

        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.tradeline.payment_history", List.of(Map.of("month", "2026-01-01", "dpd", 30)))
                .build();

        assertThat(spine.hasExecutionCapability("bureau.dpd_30_plus_count_6m", ctx)).isTrue();
        ExecutionResult r = spine.resolveAndExecute("bureau.dpd_30_plus_count_6m", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.capability()).isTrue();
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void relatedIdCannotSatisfyRequestedId() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .fact("bureau.max_dpd_6m", 45)
                .build();
        // max_dpd must not satisfy overdue amount
        ExecutionResult r = spine.resolveAndExecute("bureau.overdue.amount", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.capability()).isTrue();
        assertThat(r.valueAvailable()).isFalse();
        assertThat(spine.resolveAndExecute("bureau.max_dpd_6m", ctx).valueAvailable()).isTrue();
    }
}
