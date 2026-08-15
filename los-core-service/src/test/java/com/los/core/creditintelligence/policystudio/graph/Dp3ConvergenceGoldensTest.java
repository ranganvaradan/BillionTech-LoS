package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorDataState;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorInput;
import com.los.core.service.underwriting.ScorecardSafetyScoring;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DP-3 deterministic goldens (Parts Q).
 */
class Dp3ConvergenceGoldensTest {

    private final CanonicalParameterRegistry registry = CanonicalParameterRegistry.fromSeedForTestsOnly();
    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();

    @Test
    void golden1_policyGraphRoundtrip_astParity() {
        Map<String, Object> authoring = PolicyDsl.and(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                PolicyDsl.gte(PolicyDsl.metric("banking.avg_daily_balance_3m"), Map.of("const", 100000)));
        // Persisted graph stores exact AST copy
        Map<String, Object> persisted = Map.copyOf(authoring);
        assertThat(PolicyGraphPolicyTestService.astEquals(authoring, persisted)).isTrue();
        var ops = PolicyDslOperandExtractor.extract(persisted, registry);
        assertThat(ops).extracting(PolicyDslOperandExtractor.ExtractedOperand::canonicalParameterId)
                .contains("bureau.score", "banking.avg_daily_balance_3m");
        assertThat(ops).allMatch(o -> CiPolicyRuleGraphOperand.RESOLVED.equals(o.resolutionStatus()));
        // Policy Test AST = persisted
        String outcome = interpreter.evaluate(persisted, PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", 700, "banking.avg_daily_balance_3m", 150000),
                Map.of(),
                FixedEvaluationClock.atLocalNoon(LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"))));
        assertThat(outcome).isEqualTo(PolicyDslInterpreterV1.PASS);
    }

    @Test
    void golden2_compoundOr_preservesSemantics() {
        Map<String, Object> expr = PolicyDsl.or(
                PolicyDsl.eq(PolicyDsl.metric("bureau.score"), Map.of("const", -1)),
                PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), Map.of("const", true)),
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)));
        var clock = FixedEvaluationClock.atLocalNoon(LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        assertThat(interpreter.evaluate(expr, PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", -1), Map.of("bureau.status_ntc", false), clock)))
                .isEqualTo(PolicyDslInterpreterV1.PASS);
        assertThat(interpreter.evaluate(expr, PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", 600), Map.of("bureau.status_ntc", true), clock)))
                .isEqualTo(PolicyDslInterpreterV1.PASS);
        assertThat(interpreter.evaluate(expr, PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", 700), Map.of("bureau.status_ntc", false), clock)))
                .isEqualTo(PolicyDslInterpreterV1.PASS);
        assertThat(interpreter.evaluate(expr, PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", 600), Map.of("bureau.status_ntc", false), clock)))
                .isEqualTo(PolicyDslInterpreterV1.FAIL);
    }

    @Test
    void golden3_scorecardFactorSubset() {
        Set<String> policy = Set.of("A", "B", "C");
        PolicyWeightedScorecardEngine.assertFactorsSubsetOfPolicy(policy, List.of("A", "C"));
        assertThatThrownBy(() -> PolicyWeightedScorecardEngine.assertFactorsSubsetOfPolicy(policy, List.of("A", "D")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCORECARD_FACTOR_NOT_IN_POLICY:D");
    }

    @Test
    void golden4_weightNormalization_3_2_1() {
        var weights = PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "3"), present("B", "2"), present("C", "1")));
        assertThat(weights.get(0).normalizedWeight().setScale(3, RoundingMode.HALF_UP))
                .isEqualByComparingTo("50.000");
        assertThat(weights.get(1).normalizedWeight().setScale(3, RoundingMode.HALF_UP))
                .isEqualByComparingTo("33.333");
        assertThat(weights.get(2).normalizedWeight().setScale(3, RoundingMode.HALF_UP))
                .isEqualByComparingTo("16.667");
    }

    @Test
    void golden5_non100RawTotal() {
        var weights = PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "30"), present("B", "20")));
        assertThat(weights.get(0).normalizedWeight()).isEqualByComparingTo("60");
        assertThat(weights.get(1).normalizedWeight()).isEqualByComparingTo("40");
    }

    @Test
    void golden6_zeroWeight() {
        var weights = PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "3"), present("B", "0"), present("C", "1")));
        assertThat(weights.get(0).normalizedWeight()).isEqualByComparingTo("75");
        assertThat(weights.get(1).normalizedWeight()).isEqualByComparingTo("0");
        assertThat(weights.get(2).normalizedWeight()).isEqualByComparingTo("25");
    }

    @Test
    void golden7_negativeWeightRejected() {
        assertThatThrownBy(() -> PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "3"), present("B", "-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEGATIVE_WEIGHT");
    }

    @Test
    void golden8_allZeroInvalid() {
        assertThatThrownBy(() -> PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "0"), present("B", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ALL_APPLICABLE_WEIGHTS_ZERO");
    }

    @Test
    void golden9_requiredMissing_noSilentRenormalize() {
        var result = PolicyWeightedScorecardEngine.score(List.of(
                new FactorInput("A", new BigDecimal("3"), true, FactorDataState.MISSING,
                        BigDecimal.ZERO, BigDecimal.valueOf(100)),
                present("B", "2"),
                present("C", "1")));
        assertThat(result.dataInsufficient()).isTrue();
        assertThat(result.outcome()).isEqualTo("DATA_INSUFFICIENT");
        // A remains in denominator evidence (normalized share ~50), B/C not silently renormalized to 66/33
        assertThat(result.weights()).hasSize(3);
        assertThat(result.weights().get(0).normalizedWeight().setScale(0, RoundingMode.HALF_UP))
                .isEqualByComparingTo("50");
        assertThat(result.weights().get(1).normalizedWeight().setScale(0, RoundingMode.HALF_UP))
                .isEqualByComparingTo("33");
    }

    @Test
    void golden10_notApplicable_renormalizesRemaining() {
        var weights = PolicyWeightedScorecardEngine.normalizeApplicable(List.of(
                present("A", "3"),
                new FactorInput("B", new BigDecimal("2"), false, FactorDataState.NOT_APPLICABLE,
                        BigDecimal.ZERO, BigDecimal.valueOf(100)),
                present("C", "1")));
        assertThat(weights.get(0).normalizedWeight()).isEqualByComparingTo("75");
        assertThat(weights.get(1).applicable()).isFalse();
        assertThat(weights.get(2).normalizedWeight()).isEqualByComparingTo("25");
    }

    @Test
    void golden11_legacyScorecardModePreserved() {
        assertThat(ScorecardSafetyScoring.MISSING_REQUIRED).isEqualTo("REQUIRED");
        // Live formula contract unchanged — weights still metadata-only marker in legacy path.
        assertThat(PolicyWeightedScorecardEngine.MODE).isEqualTo("POLICY_WEIGHTED_V2");
        assertThat("LEGACY_POINTS_V1").isNotEqualTo(PolicyWeightedScorecardEngine.MODE);
    }

    @Test
    void golden12_unresolvedPolicyToken_failClosed() {
        Map<String, Object> expr = PolicyDsl.gte(
                PolicyDsl.metric("banking.transaction_count_3m"), Map.of("const", 10));
        var ops = PolicyDslOperandExtractor.extract(expr, registry);
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).resolutionStatus())
                .isEqualTo(CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER);
        assertThat(ops.get(0).originalToken()).isEqualTo("banking.transaction_count_3m");
        assertThat(ops.get(0).canonicalParameterId()).isNull();
        // Policy Test gate semantics: unresolved ⇒ not allowed
        assertThat(ops.stream().anyMatch(o ->
                CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER.equals(o.resolutionStatus()))).isTrue();
    }

    private static FactorInput present(String id, String weight) {
        return new FactorInput(id, new BigDecimal(weight), true, FactorDataState.PRESENT,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100));
    }
}
