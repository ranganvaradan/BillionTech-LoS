package com.los.core.creditintelligence.policystudio.graph;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDslOperandExtractorTest {

    private final CanonicalParameterRegistry registry = new CanonicalParameterRegistry();

    @Test
    void extractsExactMetricFromAst() {
        Map<String, Object> expr = Map.of(
                "op", "COMPARE",
                "left", Map.of("metric", "bureau.score"),
                "right", Map.of("const", 700));
        var ops = PolicyDslOperandExtractor.extract(expr, registry);
        assertThat(ops).anyMatch(o -> "bureau.score".equals(o.canonicalParameterId()));
    }

    @Test
    void extractsMetadataParameterIdsWhenAstEmpty() {
        Map<String, Object> meta = Map.of(
                "parameterId", "bureau.credit_after_overdue.clean_history_months",
                "disposition", "ACCEPTED");
        var ops = PolicyDslOperandExtractor.extractFromRuleMetadata(meta, registry);
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).canonicalParameterId())
                .isEqualTo("bureau.credit_after_overdue.clean_history_months");
        assertThat(ops.get(0).resolutionStatus()).isEqualTo(CiPolicyRuleGraphOperand.RESOLVED);
    }

    @Test
    void doesNotFuzzyMapUnknownTokens() {
        Map<String, Object> meta = Map.of("parameterId", "not.a.real.parameter");
        var ops = PolicyDslOperandExtractor.extractFromRuleMetadata(meta, registry);
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).canonicalParameterId()).isNull();
        assertThat(ops.get(0).resolutionStatus())
                .isEqualTo(CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER);
    }
}
