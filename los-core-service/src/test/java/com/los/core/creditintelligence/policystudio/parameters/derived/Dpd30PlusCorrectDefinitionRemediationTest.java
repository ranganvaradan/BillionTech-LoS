package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerType;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.service.readiness.DataParametersCapabilitySemantics;
import com.los.core.service.underwriting.CanonicalScorecardValueResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DPD-30-PLUS-CORRECT-DEFINITION-REMEDIATION-1 — COUNT_PERIODS_MATCHING via spine;
 * wrong MONTHS_SINCE definition must not execute or suppress CTA.
 */
class Dpd30PlusCorrectDefinitionRemediationTest {

    static final String TARGET = "bureau.dpd_30_plus_count_6m";
    static final String CLEAN = "bureau.credit_after_overdue.clean_history_months";

    static final Map<String, Object> COUNT_EXPR = Map.of(
            "op", "COUNT_PERIODS_MATCHING",
            "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
            "matchField", "dpd",
            "matchOp", "GTE",
            "matchValue", 30,
            "dateField", "month",
            "windowMonths", 6,
            "distinctPeriods", true,
            "asOf", Map.of("op", "EVAL_AS_OF"));

    static final Map<String, Object> WRONG_MONTHS_EXPR = Map.of(
            "op", "MONTHS_SINCE_LAST_MATCH",
            "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
            "matchField", "dpd",
            "matchOp", "GTE",
            "matchValue", 30,
            "dateField", "month",
            "asOf", Map.of("op", "EVAL_AS_OF"));

    static final Map<String, Object> CLEAN_EXPR = Map.of(
            "op", "MONTHS_SINCE_LAST_MATCH",
            "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
            "matchField", "dpd",
            "matchOp", "GT",
            "matchValue", 0,
            "dateField", "month",
            "asOf", Map.of("op", "EVAL_AS_OF"));

    private final Map<String, CiGacatDerivedCalculationDefinition> store = new ConcurrentHashMap<>();
    private DerivedCalculationDefinitionService definitions;
    private CanonicalParameterExecutionService spine;
    private AuthoredDerivedCalculationSupport overlaySupport;

    @BeforeEach
    void setUp() {
        store.clear();
        definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });

        putDef(CLEAN, CLEAN_EXPR, "Months of clean history after overdue", 3);

        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);

        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();
    }

    @AfterEach
    void tearDown() {
        overlaySupport.unregister();
        ExecutionCapabilityAuthority.clear();
    }

    private void putDef(String id, Map<String, Object> expr, String description, int version) {
        store.put(id, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(id)
                .status(DerivedCalculationDefinitionService.STATUS_DEFINED)
                .expressionJson(new LinkedHashMap<>(expr))
                .dependencyIds(List.of("bureau.tradeline.payment_history"))
                .description(description)
                .versionNo(version)
                .build());
    }

    private EvaluationContext ctxWithHistory(List<Map<String, Object>> history, LocalDate asOf) {
        return EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(asOf)
                .fact("bureau.tradeline.payment_history", history)
                .build();
    }

    private List<Map<String, Object>> goldenSixMonthHistory() {
        // asOf 2026-08 → window months Mar..Aug (6 months inclusive)
        List<Map<String, Object>> h = new ArrayList<>();
        h.add(Map.of("month", "2026-03", "dpd", 0));   // month 1
        h.add(Map.of("month", "2026-04", "dpd", 35));  // month 2 — count
        h.add(Map.of("month", "2026-05", "dpd", 60));  // month 3 — count
        h.add(Map.of("month", "2026-06", "dpd", 0));   // month 4
        h.add(Map.of("month", "2026-07", "dpd", 30));  // month 5 — count
        h.add(Map.of("month", "2026-08", "dpd", 10));  // month 6
        return h;
    }

    @Test
    void phase1_assistantAndEvaluatorSupportCountPeriods() {
        var target = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertThat(r.businessOutcome()).isEqualTo(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE);
        assertThat(r.proposedExpression().get("op")).isEqualTo("COUNT_PERIODS_MATCHING");
        assertThat(r.proposedExpression().get("windowMonths")).isEqualTo(6);
        assertThat(r.proposedExpression().get("distinctPeriods")).isEqualTo(true);
        assertThat(r.proposedExpression().get("matchValue")).isEqualTo(30);

        var eval = SafeDerivedExpressionEvaluator.evaluate(
                r.proposedExpression(),
                Map.of(
                        "bureau.tradeline.payment_history", goldenSixMonthHistory(),
                        SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-08-15"));
        assertThat(eval.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_OK);
        assertThat(((Number) eval.value()).longValue()).isEqualTo(3L);
    }

    @Test
    void wrongMonthsDefinition_notSpineCapable_andDoesNotHideCta() {
        putDef(TARGET, WRONG_MONTHS_EXPR, "wrong", 1);

        assertThat(spine.hasExecutionCapability(TARGET,
                EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build())).isTrue();
        ExecutionResult er = spine.resolveAndExecute(TARGET, ctxWithHistory(goldenSixMonthHistory(),
                LocalDate.of(2026, 8, 15)));
        assertThat(er.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(er.valueAvailable()).isFalse();
        assertThat(er.producerType()).isEqualTo(ProducerType.BUILT_IN);

        Map<String, Object> face = new LinkedHashMap<>();
        face.put("parameterId", TARGET);
        face.put("calculationRequired", true);
        face.put("needsConfiguration", true);
        face.put("executionReadinessCause", "CALCULATION_REQUIRED");
        AuthoredDerivedCalculationSupport.overlayOperand(face);
        assertThat(face.get("calculationRequired")).isEqualTo(true);
        assertThat(face.get("calculationDefined")).isEqualTo(false);
        assertThat(face.get("calculationDefinedButNotExecutable")).isEqualTo(true);
        assertThat(face.get("policyTestReady")).isEqualTo(false);
    }

    @Test
    void correctCountDefinition_valueAvailable_three() {
        putDef(TARGET, COUNT_EXPR,
                "Counts the number of distinct reported months in the previous 6 months "
                        + "where days past due was 30 or more.",
                2);

        EvaluationContext ctx = ctxWithHistory(goldenSixMonthHistory(), LocalDate.of(2026, 8, 15));
        assertThat(spine.hasExecutionCapability(TARGET, ctx)).isTrue();
        ExecutionResult er = spine.resolveAndExecute(TARGET, ctx);
        assertThat(er.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(er.producerType()).isEqualTo(ProducerType.BUILT_IN);

        EvaluationContext withExact = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 15))
                .fact(TARGET, 3L)
                .build();
        ExecutionResult exact = spine.resolveAndExecute(TARGET, withExact);
        assertThat(exact.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(((Number) exact.value()).longValue()).isEqualTo(3L);
        assertThat(exact.producerType()).isEqualTo(ProducerType.BUILT_IN);

        Map<String, Object> face = new LinkedHashMap<>();
        face.put("parameterId", TARGET);
        face.put("calculationRequired", true);
        face.put("needsConfiguration", true);
        face.put("executionReadinessCause", "CALCULATION_REQUIRED");
        AuthoredDerivedCalculationSupport.overlayOperand(face);
        assertThat(face.get("calculationRequired")).isEqualTo(false);
        assertThat(face.get("calculationDefined")).isEqualTo(true);
        assertThat(String.valueOf(face.get("howCalculated"))).containsIgnoringCase("distinct reported months");
    }

    @Test
    void edges_zero_exclude29_include30_distinct_window_missingHistory() {
        putDef(TARGET, COUNT_EXPR, "count 30+", 1);
        LocalDate asOf = LocalDate.of(2026, 8, 15);

        var evalZero = SafeDerivedExpressionEvaluator.evaluate(
                COUNT_EXPR,
                Map.of(
                        "bureau.tradeline.payment_history", List.of(
                                Map.of("month", "2026-06", "dpd", 0),
                                Map.of("month", "2026-07", "dpd", 10),
                                Map.of("month", "2026-08", "dpd", 29)),
                        SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-08-15"));
        assertThat(((Number) evalZero.value()).longValue()).isEqualTo(0L);

        var evalEdge = SafeDerivedExpressionEvaluator.evaluate(
                COUNT_EXPR,
                Map.of(
                        "bureau.tradeline.payment_history", List.of(
                                Map.of("month", "2026-06", "dpd", 29),
                                Map.of("month", "2026-07", "dpd", 30),
                                Map.of("month", "2026-08", "dpd", 45)),
                        SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-08-15"));
        assertThat(((Number) evalEdge.value()).longValue()).isEqualTo(2L);

        var evalDup = SafeDerivedExpressionEvaluator.evaluate(
                COUNT_EXPR,
                Map.of(
                        "bureau.tradeline.payment_history", List.of(
                                Map.of("month", "2026-07", "dpd", 40),
                                Map.of("month", "2026-07", "dpd", 90),
                                Map.of("month", "2026-08", "dpd", 35)),
                        SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-08-15"));
        assertThat(((Number) evalDup.value()).longValue()).isEqualTo(2L);

        var evalWin = SafeDerivedExpressionEvaluator.evaluate(
                COUNT_EXPR,
                Map.of(
                        "bureau.tradeline.payment_history", List.of(
                                Map.of("month", "2026-02", "dpd", 99),
                                Map.of("month", "2026-03", "dpd", 40)),
                        SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, "2026-08-15"));
        assertThat(((Number) evalWin.value()).longValue()).isEqualTo(1L);

        ExecutionResult miss = spine.resolveAndExecute(TARGET,
                EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST)
                        .evaluationAsOf(asOf).build());
        assertThat(miss.valueAvailable()).isFalse();
        assertThat(miss.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(miss.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void crossSurface_sameCapabilityAndValue() {
        putDef(TARGET, COUNT_EXPR, "Counts distinct reported months… DPD 30+", 2);
        EvaluationContext pt = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 15))
                .fact(TARGET, 3L)
                .build();
        EvaluationContext w6 = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .evaluationAsOf(LocalDate.of(2026, 8, 15))
                .facts(pt.facts())
                .build();
        EvaluationContext uw = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 15))
                .facts(pt.facts())
                .build();

        ExecutionResult rPt = spine.resolveAndExecute(TARGET, pt);
        ExecutionResult rW6 = spine.resolveAndExecute(TARGET, w6);
        ExecutionResult rUw = spine.resolveAndExecute(TARGET, uw);
        var sc = CanonicalScorecardValueResolver.resolveCanonical(TARGET, uw);

        assertThat(rPt.value()).isEqualTo(rW6.value());
        assertThat(rPt.value()).isEqualTo(rUw.value());
        assertThat(sc.valueAvailable()).isTrue();
        assertThat(sc.producerType()).isEqualTo(ProducerType.BUILT_IN.name());

        var def = PolicyStudioConvergencePresenter.registry().findById(TARGET).orElseThrow();
        Map<String, Object> dp = DataParametersCapabilitySemantics.project(def);
        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project(def);
        assertThat(dp.get("policyTestReady")).isEqualTo(true);
        assertThat(proj.get("policyTestReady")).isEqualTo(true);
    }

    @Test
    void cleanHistory_regression_stillMonthsSince() {
        EvaluationContext ctx = ctxWithHistory(List.of(
                Map.of("month", "2026-01", "dpd", 45),
                Map.of("month", "2026-08", "dpd", 0)
        ), LocalDate.of(2026, 8, 1));
        ExecutionResult r = spine.resolveAndExecute(CLEAN, ctx);
        assertThat(r.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(r.producerType()).isEqualTo(ProducerType.BUILT_IN);
        assertThat(store.get(CLEAN).getExpressionJson().get("op")).isEqualTo("MONTHS_SINCE_LAST_MATCH");
    }

    @Test
    void unsupportedThree_remainHonest() {
        for (String id : List.of(
                "bureau.cc_overdue_amount",
                "bureau.overdue.amount",
                "bureau.overdue.age_months")) {
            assertThat(spine.hasExecutionCapability(id,
                    EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build()))
                    .as(id).isTrue();
        }
    }
}
