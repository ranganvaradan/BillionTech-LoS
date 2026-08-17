package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.derived.BusinessCalculationAssistant;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedExpressionEvaluator;
import com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedTypeModel;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerType;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-2 — generic collection-capable SafeDerived extension goldens (Tests A–L).
 * No parameter-specific Java calculators. No production definition mutation.
 */
class Wave2GenericCalculationEngineTest {

    private static final String TRADELINES = "fixture.tradelines";
    private static final String HISTORY = "fixture.payment_history";
    private static final String SYN_A = "fixture.synthetic.scalar_a";
    private static final String SYN_B = "fixture.synthetic.derived_b";

    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        Map<String, Object> exprA = Map.of("op", "REF", "id", "fixture.raw.n");
        CiGacatDerivedCalculationDefinition defA = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("aaaa0000-0000-4000-8000-0000000000a1"))
                .canonicalParameterId(SYN_A)
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(exprA)
                .dependencyIds(List.of("fixture.raw.n"))
                .versionNo(1)
                .build();
        Map<String, Object> exprB = Map.of(
                "op", "MUL",
                "left", Map.of("op", "REF", "id", SYN_A),
                "right", Map.of("op", "CONST", "value", 2));
        CiGacatDerivedCalculationDefinition defB = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("bbbb0000-0000-4000-8000-0000000000b2"))
                .canonicalParameterId(SYN_B)
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(exprB)
                .dependencyIds(List.of(SYN_A))
                .versionNo(1)
                .build();
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
        Map<String, Object> dpdExpr = BusinessCalculationAssistant.countPeriodsMatchingExpression(
                BusinessCalculationAssistant.HISTORY_PAYMENT, "GTE", 30, 6);
        CiGacatDerivedCalculationDefinition dpd = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000030"))
                .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(dpdExpr)
                .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                .versionNo(1)
                .build();

        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> {
            if (SYN_A.equals(id)) return Optional.of(defA);
            if (SYN_B.equals(id)) return Optional.of(defB);
            if ("bureau.credit_after_overdue.clean_history_months".equals(id)) return Optional.of(clean);
            if ("bureau.dpd_30_plus_count_6m".equals(id)) return Optional.of(dpd);
            return Optional.empty();
        });
        // Register fixture RAW facts for synthetic deps + collections used only in unit eval
        spine.registry().registerExact("fixture.raw.n",
                new com.los.core.creditintelligence.policystudio.parameters.execution.RawFactProducer(
                        Set.of("fixture.raw.n")));
        ExecutionCapabilityAuthority.install(spine);
    }

    @Test
    void testA_filterProjectSum() {
        Map<String, Object> expr = filterProjectSum(TRADELINES, "CREDIT_CARD", "overdue_amount");
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(TRADELINES, sampleTradelines()));
        assertThat(r.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_OK);
        assertThat(((Number) r.value()).doubleValue()).isEqualTo(7500.0d);
        assertThat(SafeDerivedExpressionEvaluator.operatorChain(expr))
                .contains("FILTER", "PROJECT", "SUM");
    }

    @Test
    void testB_filterAndCount() {
        Map<String, Object> where = Map.of(
                "op", "AND",
                "left", Map.of(
                        "op", "EQ",
                        "left", Map.of("op", "FIELD", "field", "status"),
                        "right", Map.of("op", "CONST", "value", "ACTIVE")),
                "right", Map.of(
                        "op", "EQ",
                        "left", Map.of("op", "FIELD", "field", "secured"),
                        "right", Map.of("op", "CONST", "value", false)));
        Map<String, Object> expr = Map.of(
                "op", "COUNT",
                "of", Map.of(
                        "op", "FILTER",
                        "from", Map.of("op", "REF", "id", TRADELINES),
                        "where", where));
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(TRADELINES, sampleLoans()));
        assertThat(((Number) r.value()).longValue()).isEqualTo(2L);
    }

    @Test
    void testC_historyFilterDistinctCount_withoutCountPeriodsMatching() {
        Map<String, Object> expr = Map.of(
                "op", "COUNT",
                "of", Map.of(
                        "op", "DISTINCT",
                        "of", Map.of(
                                "op", "PROJECT",
                                "field", "month",
                                "from", Map.of(
                                        "op", "FILTER",
                                        "from", Map.of(
                                                "op", "TRAILING_WINDOW",
                                                "from", Map.of("op", "REF", "id", HISTORY),
                                                "windowMonths", 6,
                                                "dateField", "month",
                                                "asOf", Map.of("op", "EVAL_AS_OF")),
                                        "where", Map.of(
                                                "op", "GTE",
                                                "left", Map.of("op", "FIELD", "field", "dpd"),
                                                "right", Map.of("op", "CONST", "value", 30))))));
        assertThat(SafeDerivedExpressionEvaluator.operatorChain(expr))
                .doesNotContain("COUNT_PERIODS_MATCHING");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(HISTORY, sampleHistory());
        inputs.put(SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, LocalDate.of(2026, 6, 30));
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, inputs);
        assertThat(r.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_OK);
        assertThat(((Number) r.value()).longValue()).isEqualTo(3L);
    }

    @Test
    void testD_maxOverHistory() {
        Map<String, Object> expr = Map.of(
                "op", "MAX",
                "of", Map.of(
                        "op", "PROJECT",
                        "field", "dpd",
                        "from", Map.of("op", "REF", "id", HISTORY)));
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(HISTORY, sampleHistory()));
        assertThat(((Number) r.value()).doubleValue()).isEqualTo(60.0d);
    }

    @Test
    void testE_avg() {
        Map<String, Object> expr = Map.of(
                "op", "AVG",
                "of", Map.of("op", "CONST", "value", List.of(100, 200, 300)));
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of());
        assertThat(((Number) r.value()).doubleValue()).isEqualTo(200.0d);
    }

    @Test
    void testF_anyAll() {
        List<Map<String, Object>> rows = List.of(
                Map.of("dpd", 0), Map.of("dpd", 10), Map.of("dpd", 35));
        Map<String, Object> any = Map.of(
                "op", "ANY",
                "of", Map.of("op", "REF", "id", HISTORY),
                "where", Map.of(
                        "op", "GTE",
                        "left", Map.of("op", "FIELD", "field", "dpd"),
                        "right", Map.of("op", "CONST", "value", 30)));
        Map<String, Object> allGe0 = Map.of(
                "op", "ALL",
                "of", Map.of("op", "REF", "id", HISTORY),
                "where", Map.of(
                        "op", "GTE",
                        "left", Map.of("op", "FIELD", "field", "dpd"),
                        "right", Map.of("op", "CONST", "value", 0)));
        Map<String, Object> allLt30 = Map.of(
                "op", "ALL",
                "of", Map.of("op", "REF", "id", HISTORY),
                "where", Map.of(
                        "op", "LT",
                        "left", Map.of("op", "FIELD", "field", "dpd"),
                        "right", Map.of("op", "CONST", "value", 30)));
        assertThat(SafeDerivedExpressionEvaluator.evaluate(any, Map.of(HISTORY, rows)).value()).isEqualTo(true);
        assertThat(SafeDerivedExpressionEvaluator.evaluate(allGe0, Map.of(HISTORY, rows)).value()).isEqualTo(true);
        assertThat(SafeDerivedExpressionEvaluator.evaluate(allLt30, Map.of(HISTORY, rows)).value()).isEqualTo(false);
    }

    @Test
    void testG_emptyFilteredSumIsZero() {
        List<Map<String, Object>> noCc = List.of(
                Map.of("account_type", "HOME_LOAN", "overdue_amount", 10000),
                Map.of("account_type", "PERSONAL_LOAN", "overdue_amount", 3000));
        Map<String, Object> expr = filterProjectSum(TRADELINES, "CREDIT_CARD", "overdue_amount");
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of(TRADELINES, noCc));
        assertThat(r.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_OK);
        assertThat(((Number) r.value()).doubleValue()).isEqualTo(0.0d);
    }

    @Test
    void testH_missingCollectionNotZero() {
        Map<String, Object> expr = filterProjectSum(TRADELINES, "CREDIT_CARD", "overdue_amount");
        var r = SafeDerivedExpressionEvaluator.evaluate(expr, Map.of());
        assertThat(r.status()).isEqualTo(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT);
        assertThat(r.value()).isNull();
    }

    @Test
    void testI_derivedOnDerivedViaCpes() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("fixture.raw.n", 21)
                .build();
        ExecutionResult b = spine.resolveAndExecute(SYN_B, ctx);
        assertThat(b.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(((Number) b.value()).doubleValue()).isEqualTo(42.0d);
        assertThat(b.dependencies()).contains(SYN_A);
        assertThat(b.provenance().get("dependencyProvenance")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> depProv = (List<Map<String, Object>>) b.provenance().get("dependencyProvenance");
        assertThat(depProv.stream().anyMatch(d -> SYN_A.equals(d.get("canonicalParameterId"))
                || SYN_A.equals(d.get("canonicalId")))).isTrue();
    }

    @Test
    void testJ_typeSafetyRejects() {
        Map<String, SafeDerivedTypeModel.ExprType> schema = SafeDerivedTypeModel.schema(Map.of(
                "account_type", "ENUM",
                "overdue_amount", "MONEY",
                "dpd", "INTEGER",
                "month", "YEAR_MONTH"));

        assertThat(SafeDerivedExpressionEvaluator.validate(
                Map.of("op", "SUM", "of", Map.of("op", "CONST", "value", List.of("a", "b"), "valueType", "STRING")),
                Set.of(), schema)).anyMatch(e -> e.toLowerCase().contains("string"));

        assertThat(SafeDerivedExpressionEvaluator.validate(
                Map.of("op", "SUM", "of", Map.of("op", "CONST", "value", List.of(true, false), "valueType", "BOOLEAN")),
                Set.of(), schema)).anyMatch(e -> e.toLowerCase().contains("boolean"));

        var moneyMonths = Map.of(
                "op", "ADD",
                "left", Map.of("op", "CONST", "value", 100, "valueType", "MONEY"),
                "right", Map.of("op", "CONST", "value", 3, "valueType", "MONTH"));
        assertThat(SafeDerivedExpressionEvaluator.validate(moneyMonths, Set.of()))
                .anyMatch(e -> e.toLowerCase().contains("money") || e.toLowerCase().contains("month"));
        assertThat(SafeDerivedExpressionEvaluator.evaluate(moneyMonths, Map.of()).status())
                .isEqualTo(SafeDerivedExpressionEvaluator.STATUS_INVALID);

        Map<String, Object> unknownField = Map.of(
                "op", "PROJECT",
                "field", "no_such_field",
                "from", Map.of("op", "REF", "id", TRADELINES));
        assertThat(SafeDerivedExpressionEvaluator.validate(unknownField, Set.of(TRADELINES), schema))
                .anyMatch(e -> e.toLowerCase().contains("unknown field"));

        var dateMoney = Map.of(
                "op", "GTE",
                "left", Map.of("op", "CONST", "value", "2026-01", "valueType", "DATE"),
                "right", Map.of("op", "CONST", "value", 100, "valueType", "MONEY"));
        assertThat(SafeDerivedExpressionEvaluator.evaluate(dateMoney, Map.of()).status())
                .isEqualTo(SafeDerivedExpressionEvaluator.STATUS_INVALID);

        assertThat(SafeDerivedExpressionEvaluator.validate(
                Map.of("op", "AVG", "of", Map.of("op", "CONST", "value", List.of("A", "B"), "valueType", "ENUM")),
                Set.of())).anyMatch(e -> e.toLowerCase().contains("enum") || e.toLowerCase().contains("string"));
    }

    @Test
    void testK_temporalDeterminism_noWallClock() {
        Map<String, Object> expr = Map.of(
                "op", "COUNT",
                "of", Map.of(
                        "op", "TRAILING_WINDOW",
                        "from", Map.of("op", "REF", "id", HISTORY),
                        "windowMonths", 3,
                        "dateField", "month",
                        "asOf", Map.of("op", "EVAL_AS_OF")));
        Map<String, Object> in1 = new LinkedHashMap<>();
        in1.put(HISTORY, sampleHistory());
        in1.put(SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, LocalDate.of(2026, 6, 30));
        Map<String, Object> in2 = new LinkedHashMap<>();
        in2.put(HISTORY, sampleHistory());
        in2.put(SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, LocalDate.of(2026, 3, 31));
        long c1 = ((Number) SafeDerivedExpressionEvaluator.evaluate(expr, in1).value()).longValue();
        long c2 = ((Number) SafeDerivedExpressionEvaluator.evaluate(expr, in2).value()).longValue();
        assertThat(c1).isEqualTo(3L); // Apr,May,Jun
        assertThat(c2).isEqualTo(3L); // Jan,Feb,Mar
        assertThat(c1).isEqualTo(c2); // same size windows; content differs — prove asOf shifts window:
        // Count months with dpd>=30 in each window
        Map<String, Object> filtered = Map.of(
                "op", "COUNT",
                "of", Map.of(
                        "op", "FILTER",
                        "from", Map.of(
                                "op", "TRAILING_WINDOW",
                                "from", Map.of("op", "REF", "id", HISTORY),
                                "windowMonths", 3,
                                "asOf", Map.of("op", "EVAL_AS_OF")),
                        "where", Map.of(
                                "op", "GTE",
                                "left", Map.of("op", "FIELD", "field", "dpd"),
                                "right", Map.of("op", "CONST", "value", 30))));
        long f1 = ((Number) SafeDerivedExpressionEvaluator.evaluate(filtered, in1).value()).longValue();
        long f2 = ((Number) SafeDerivedExpressionEvaluator.evaluate(filtered, in2).value()).longValue();
        assertThat(f1).isEqualTo(1L); // Jun dpd10 only? Apr=0 May=30 Jun=10 → May only = 1
        assertThat(f2).isEqualTo(2L); // Jan0 Feb35 Mar60 → 2
        assertThat(f1).isNotEqualTo(f2);
    }

    @Test
    void testL_existingAuthoredCompatibility() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact(BusinessCalculationAssistant.HISTORY_PAYMENT, List.of(
                        Map.of("month", "2026-03", "dpd", 45),
                        Map.of("month", "2026-07", "dpd", 0)))
                .build();
        ExecutionResult clean = spine.resolveAndExecute(
                "bureau.credit_after_overdue.clean_history_months", ctx);
        assertThat(clean.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(clean.producerType()).isEqualTo(ProducerType.BUILT_IN);

        ExecutionResult dpd = spine.resolveAndExecute("bureau.dpd_30_plus_count_6m", ctx);
        assertThat(dpd.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(dpd.producerType()).isEqualTo(ProducerType.BUILT_IN);
    }

    @Test
    void assistant_ccOverdue_noLongerClaimsExecutableCanCalculate() {
        var target = PolicyStudioConvergencePresenter.registry()
                .findById("bureau.cc_overdue_amount").orElseThrow();
        var r = BusinessCalculationAssistant.investigate(
                target, PolicyStudioConvergencePresenter.registry(), "", Map.of()).orElseThrow();
        assertThat(r.businessOutcome()).isNotEqualTo(BusinessCalculationAssistant.OUTCOME_CAN_CALCULATE);
        assertThat(r.knownExistingCalculation()).isFalse();
        assertThat(r.proposedExpression()).isNull();
    }

    @Test
    void noParameterSpecificBranchesInEvaluatorSource() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/parameters/derived/SafeDerivedExpressionEvaluator.java"));
        assertThat(src).doesNotContain("bureau.cc_overdue_amount");
        assertThat(src).doesNotContain("bureau.dpd_30_plus_count_6m");
        assertThat(src).doesNotContain("canonicalId ==");
        assertThat(src).doesNotContain("canonicalParameterId ==");
    }

    private static Map<String, Object> filterProjectSum(String collectionId, String accountType, String amountField) {
        return Map.of(
                "op", "SUM",
                "of", Map.of(
                        "op", "PROJECT",
                        "field", amountField,
                        "from", Map.of(
                                "op", "FILTER",
                                "from", Map.of("op", "REF", "id", collectionId),
                                "where", Map.of(
                                        "op", "EQ",
                                        "left", Map.of("op", "FIELD", "field", "account_type"),
                                        "right", Map.of("op", "CONST", "value", accountType)))));
    }

    private static List<Map<String, Object>> sampleTradelines() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("account_type", "HOME_LOAN", "overdue_amount", 10000));
        rows.add(Map.of("account_type", "CREDIT_CARD", "overdue_amount", 5000));
        rows.add(Map.of("account_type", "CREDIT_CARD", "overdue_amount", 2500));
        rows.add(Map.of("account_type", "PERSONAL_LOAN", "overdue_amount", 3000));
        return rows;
    }

    private static List<Map<String, Object>> sampleLoans() {
        return List.of(
                Map.of("status", "ACTIVE", "secured", false),
                Map.of("status", "ACTIVE", "secured", false),
                Map.of("status", "CLOSED", "secured", false),
                Map.of("status", "ACTIVE", "secured", true));
    }

    private static List<Map<String, Object>> sampleHistory() {
        return List.of(
                Map.of("month", "2026-01", "dpd", 0),
                Map.of("month", "2026-02", "dpd", 35),
                Map.of("month", "2026-03", "dpd", 60),
                Map.of("month", "2026-04", "dpd", 0),
                Map.of("month", "2026-05", "dpd", 30),
                Map.of("month", "2026-06", "dpd", 10));
    }
}
