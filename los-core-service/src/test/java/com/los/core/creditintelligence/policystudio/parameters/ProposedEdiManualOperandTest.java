package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item 70 — Proposed EDI is GACAT MANUAL; operand faces must be MANUAL-authorised, not unresolved.
 */
class ProposedEdiManualOperandTest {

    @BeforeEach
    void setUp() {
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
    }

    @Test
    void capacityRule_proposedEdiIsManualAuthorisedNotUnresolved() {
        CiPolicyRuleCandidate r = capacityRule("BANK_STARTER_ADB_GTE_EDI");
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                r.getSystemRuleId(),
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                r.getMetadata(),
                Map.of());
        assertThat(ops).anyMatch(o -> "proposed_edi".equals(o.get("operandKey"))
                && "application.proposed_edi".equals(o.get("parameterId"))
                && ParameterResolutionSupport.STATUS_MANUAL.equals(o.get("status"))
                && !Boolean.TRUE.equals(o.get("unresolved")));
        assertThat(PolicyExecutionReadiness.operandBlocksExecution(
                ops.stream().filter(o -> "proposed_edi".equals(o.get("operandKey"))).findFirst().orElseThrow()))
                .isFalse();
        assertThat(PolicyExecutionReadiness.executionBlockersForRule(r))
                .noneMatch(b -> String.valueOf(b.getOrDefault("reason", "")).toLowerCase()
                        .contains("no runtime source"));
    }

    private static CiPolicyRuleCandidate capacityRule(String systemId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("businessTitle", "Starter — banking capacity (ADB ≥ EDI)");
        meta.put("catalogueBacked", true);
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId(systemId)
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "banking.avg_daily_balance_3m"),
                        "right", Map.of("metric", "application.proposed_edi")))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(meta)
                .build();
    }
}
