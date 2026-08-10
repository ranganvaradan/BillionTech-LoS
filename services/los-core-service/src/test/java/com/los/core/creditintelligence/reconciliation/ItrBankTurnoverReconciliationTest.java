package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.SubjectMatchStatus;
import com.los.core.creditintelligence.reconciliation.service.DiscrepancySeverityClassifier;
import com.los.core.creditintelligence.reconciliation.service.OperandResolver;
import com.los.core.creditintelligence.reconciliation.service.PeriodAlignmentService;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationConfidenceCalculator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationEvaluator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationExplanationBuilder;
import com.los.core.creditintelligence.reconciliation.service.VarianceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItrBankTurnoverReconciliationTest {

    private ReconciliationEvaluator evaluator;
    private CiReconciliationDefinition def;
    private final UUID tenant = UUID.randomUUID();
    private final UUID app = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new ReconciliationEvaluator(
                new PeriodAlignmentService(), new VarianceCalculator(),
                new ReconciliationConfidenceCalculator(), new ReconciliationExplanationBuilder(),
                new DiscrepancySeverityClassifier(), new CreditIntelligenceProperties());
        def = CiReconciliationDefinition.builder()
                .reconciliationCode(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER)
                .version("V1").name("ITR vs Bank").category("TURNOVER")
                .periodAlignmentStrategy("FINANCIAL_YEAR")
                .varianceMethod("SYMMETRIC_PERCENT_DIFFERENCE")
                .warningTolerance(BigDecimal.valueOf(5))
                .materialTolerance(BigDecimal.valueOf(15))
                .leftOperandDefinition(Map.of()).rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of()).allowedDataStatuses(List.of())
                .metadata(Map.of("toleranceProfile", "turnover"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    @Test
    void aligned() {
        var r = evaluate(bd("81000000"), bd("80000000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATCH.name(),
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name());
    }

    @Test
    void receivablesTimingDifference() {
        var r = evaluate(bd("81000000"), bd("75000000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name(),
                ReconciliationOutcome.MATERIAL_VARIANCE.name());
        assertThat(r.isHumanReviewRequired()
                || ReconciliationOutcome.ACCEPTABLE_VARIANCE.name().equals(r.getOutcome())).isTrue();
    }

    @Test
    void missingBusinessTurnover() {
        var r = evaluator.evaluate(tenant, app, null, null, def,
                op(null),
                op(bd("80000000")),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
    }

    private com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult evaluate(
            BigDecimal itr, BigDecimal bank) {
        return evaluator.evaluate(tenant, app, null, null, def, op(itr), op(bank),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
    }

    private OperandResolver.ResolvedOperand op(BigDecimal value) {
        return new OperandResolver.ResolvedOperand(
                value != null, value, "x", "V1", null,
                LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31),
                BigDecimal.ONE, BigDecimal.ONE, List.of(), Map.of(), false, "OK");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
