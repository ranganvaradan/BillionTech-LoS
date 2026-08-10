package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.service.CanonicalReconciliationRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalReconciliationRuleEvaluatorTest {

    private CanonicalReconciliationRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CanonicalReconciliationRuleEvaluator();
    }

    @Test
    void matchMapsToPass() {
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.MATCH.name(), false))
                .isEqualTo(RuleOutcome.PASS.name());
    }

    @Test
    void acceptableMapsToPassByDefault() {
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name(), false))
                .isEqualTo(RuleOutcome.PASS.name());
    }

    @Test
    void acceptableMapsToWarnWhenRequested() {
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name(), true))
                .isEqualTo("WARN");
    }

    @Test
    void materialAndConflictRefer() {
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.MATERIAL_VARIANCE.name(), false))
                .isEqualTo(RuleOutcome.REFER.name());
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.CONFLICT.name(), false))
                .isEqualTo(RuleOutcome.REFER.name());
    }

    @Test
    void dataInsufficientMapsToDi() {
        assertThat(CanonicalReconciliationRuleEvaluator.mapToRuleOutcome(
                ReconciliationOutcome.DATA_INSUFFICIENT.name(), false))
                .isEqualTo(RuleOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void evaluateAllProducesShadowRules() {
        List<CiReconciliationResult> results = List.of(
                result(ReconciliationConstants.XSRC_GST_ITR_TURNOVER, ReconciliationOutcome.MATCH),
                result(ReconciliationConstants.XSRC_GST_BANK_TURNOVER, ReconciliationOutcome.ACCEPTABLE_VARIANCE),
                result(ReconciliationConstants.TURNOVER_TRIANGULATION, ReconciliationOutcome.MATCH,
                        Map.of("status", "STRONG_ALIGNMENT")));
        var evals = evaluator.evaluateAll(results);
        assertThat(evals).hasSize(7);
        assertThat(evals.stream().map(CanonicalReconciliationRuleEvaluator.RuleEvalResult::ruleId))
                .contains(
                        ReconciliationConstants.XSRC_GST_ITR_TURNOVER,
                        CanonicalReconciliationRuleEvaluator.XSRC_TURNOVER_TRIANGULATION);
        assertThat(evals.get(0).outcome()).isEqualTo(RuleOutcome.PASS.name());
    }

    private CiReconciliationResult result(String code, ReconciliationOutcome outcome) {
        return result(code, outcome, Map.of());
    }

    private CiReconciliationResult result(String code, ReconciliationOutcome outcome, Map<String, Object> meta) {
        return CiReconciliationResult.builder()
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .reconciliationCode(code)
                .definitionVersion("V1")
                .outcome(outcome.name())
                .percentageVariance(BigDecimal.ONE)
                .dataStatus("COMPLETE")
                .subjectMatchStatus("UNKNOWN")
                .explanationCodes(List.of())
                .probableCauses(List.of())
                .evidenceRefs(List.of())
                .evidenceGroupIds(List.of())
                .leftSourceRefs(List.of())
                .rightSourceRefs(List.of())
                .executedAt(Instant.now())
                .trace(Map.of())
                .metadata(meta)
                .build();
    }
}
