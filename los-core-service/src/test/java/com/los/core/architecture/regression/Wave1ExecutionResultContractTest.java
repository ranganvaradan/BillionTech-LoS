package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.derived.BusinessCalculationAssistant;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalExecutionContractProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerType;
import com.los.core.requirement.W6CanonicalParameterExecutor;
import com.los.core.service.underwriting.CanonicalScorecardValueResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-1 — cross-producer / cross-consumer execution-result contract.
 * No new calculation capability.
 */
class Wave1ExecutionResultContractTest {

    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        Map<String, Object> cleanExpr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                BusinessCalculationAssistant.HISTORY_PAYMENT, 0);
        CiGacatDerivedCalculationDefinition clean = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000001"))
                .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(cleanExpr)
                .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                .versionNo(1)
                .build();
        spine = ExecutionSpineProducerBootstrap.standalone((id, t) ->
                "bureau.credit_after_overdue.clean_history_months".equals(id)
                        ? Optional.of(clean) : Optional.empty());
        ExecutionCapabilityAuthority.install(spine);
    }

    @Test
    void raw_zeroIsValueAvailable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 0)
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.score", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(r.valueAvailable()).isTrue();
        assertThat(r.capability()).isTrue();
        assertThat(((Number) r.value()).intValue()).isEqualTo(0);
        assertThat(r.mode()).isEqualTo(EvaluationMode.POLICY_TEST);
        assertThat(r.evaluationAsOf()).isEqualTo(LocalDate.of(2026, 8, 1));
        // Wave-8: certification vocabulary is UNCERTIFIED (Wave-1 used NOT_ESTABLISHED synonym)
        assertThat(r.toCanonicalContractMap().get("certificationStatus")).isEqualTo("UNCERTIFIED");
        assertThat(r.toCanonicalContractMap().get("certificationStatusWave1Alias")).isEqualTo("NOT_ESTABLISHED");
    }

    @Test
    void raw_missingIsDataNotAvailable_capabilityTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.score", ctx);
        assertThat(r.capability()).isTrue();
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.valueAvailable()).isFalse();
        assertThat(r.missingReason()).isNotBlank();
    }

    @Test
    void manual_missingIsInputRequired() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult r = spine.resolveAndExecute("application.tenure_months", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.INPUT_REQUIRED);
        assertThat(r.capability()).isTrue();
        assertThat(r.producerTypeFamily()).isEqualTo("MANUAL");
    }

    @Test
    void builtIn_missingSource_capabilityTrue_dataNotAvailable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.max_dpd_6m", ctx);
        assertThat(r.capability()).isTrue();
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.producerTypeFamily()).isEqualTo("BUILT_IN");
    }

    @Test
    void bankingBuiltIn_emptyContext_capabilityTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        assertThat(spine.hasExecutionCapability("banking.avg_daily_balance_3m", ctx)).isTrue();
        ExecutionResult r = spine.resolveAndExecute("banking.avg_daily_balance_3m", ctx);
        assertThat(r.capability()).isTrue();
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
    }

    @Test
    void authored_definitionMissing_calculationNotDefinedOrNotExecutable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.tradeline.payment_history", List.of(Map.of("month", "2026-01", "dpd", 0)))
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.dpd_30_plus_count_6m", ctx);
        assertThat(r.capability()).isTrue();
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void authored_dependencyMissing_dependencyNotAvailable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.credit_after_overdue.clean_history_months", ctx);
        assertThat(r.capability()).isTrue();
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void unsupported_notExecutable_capabilityFalse() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.thin_file_indicator", ctx);
        assertThat(r.capability()).isFalse();
        assertThat(r.status()).isEqualTo(ExecutionStatus.NOT_EXECUTABLE);
    }

    @Test
    void policyTestSimulation_doesNotFlipCapabilityTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .input("bureau.thin_file_indicator", 9999)
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.thin_file_indicator", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(r.capability()).isFalse();
        assertThat(r.simulatedValue()).isTrue();
        assertThat(r.provenance().get("simulatedValueDoesNotImplyExecutable")).isEqualTo(true);
    }

    @Test
    void consumers_shareSameContractSemantics() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 710)
                .build();
        ExecutionResult er = spine.resolveAndExecute("bureau.score", ctx);
        Map<String, Object> contract = CanonicalExecutionContractProjection.project(er);

        var scorecard = CanonicalScorecardValueResolver.fromExecution(er);
        assertThat(scorecard.status()).isEqualTo(er.status());
        assertThat(scorecard.capability()).isEqualTo(er.capability());
        assertThat(scorecard.valueAvailable()).isEqualTo(er.valueAvailable());

        W6CanonicalParameterExecutor w6 = new W6CanonicalParameterExecutor();
        var view = w6.execute("bureau.score", ctx);
        assertThat(view.status()).isEqualTo(er.status());
        assertThat(view.capability()).isEqualTo(er.capability());
        assertThat(view.requirementSatisfied()).isEqualTo(er.valueAvailable());

        Map<String, Object> gate3 = ParameterExecutabilitySupport.evaluate("bureau.score");
        assertThat(gate3.get("catalogueFlagsAreNotExecutionAuthority")).isEqualTo(true);
        assertThat(gate3.get("productionReady")).isEqualTo(false);

        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project("bureau.score");
        assertThat(proj.get("catalogueFlagsAreNotExecutionAuthority")).isEqualTo(true);
        assertThat(proj.get("productionReady")).isEqualTo(false);

        assertThat(contract.get("status")).isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(contract.get("capability")).isEqualTo(true);
    }

    @Test
    void emptyValidCollection_isValueAvailable() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.tradeline.payment_history", List.of())
                .build();
        ExecutionResult r = spine.resolveAndExecute("bureau.tradeline.payment_history", ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(r.valueAvailable()).isTrue();
        assertThat((List<?>) r.value()).isEmpty();
    }

    @Test
    void execution_nullContext_isError() {
        ExecutionResult r = spine.resolveAndExecute("bureau.score", null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.ERROR);
        assertThat(r.valueAvailable()).isFalse();
    }
}
