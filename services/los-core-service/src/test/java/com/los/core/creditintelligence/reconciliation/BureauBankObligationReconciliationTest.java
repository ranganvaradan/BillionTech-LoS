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

class BureauBankObligationReconciliationTest {

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
                .reconciliationCode(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION)
                .version("V1").name("Bureau vs Bank").category("OBLIGATION")
                .periodAlignmentStrategy("EXACT_PERIOD")
                .varianceMethod("SYMMETRIC_PERCENT_DIFFERENCE")
                .warningTolerance(BigDecimal.valueOf(5))
                .materialTolerance(BigDecimal.valueOf(20))
                .leftOperandDefinition(Map.of()).rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of()).allowedDataStatuses(List.of())
                .metadata(Map.of("toleranceProfile", "obligation"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    @Test
    void exactMatch() {
        var r = evaluate(bd("182000"), bd("182000"));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.MATCH.name());
    }

    @Test
    void nearAmount() {
        var r = evaluate(bd("182000"), bd("176000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATCH.name(),
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name());
    }

    @Test
    void bureauMissing() {
        LocalDate d = LocalDate.of(2025, 8, 1);
        var r = evaluator.evaluate(tenant, app, null, null, def,
                op(null, d), op(bd("176000"), d),
                SubjectMatchStatus.UNKNOWN, d);
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void bankEmiMissing() {
        LocalDate d = LocalDate.of(2025, 8, 1);
        var r = evaluator.evaluate(tenant, app, null, null, def,
                op(bd("182000"), d), op(null, d),
                SubjectMatchStatus.UNKNOWN, d);
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void materialObligationGap() {
        var r = evaluate(bd("200000"), bd("100000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATERIAL_VARIANCE.name(),
                ReconciliationOutcome.CONFLICT.name());
    }

    private com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult evaluate(
            BigDecimal bureau, BigDecimal bank) {
        LocalDate d = LocalDate.of(2025, 8, 1);
        return evaluator.evaluate(tenant, app, null, null, def,
                op(bureau, d), op(bank, d), SubjectMatchStatus.UNKNOWN, d);
    }

    private OperandResolver.ResolvedOperand op(BigDecimal value, LocalDate d) {
        return new OperandResolver.ResolvedOperand(
                value != null, value, "obl", "V1", null,
                d.withDayOfMonth(1), d, BigDecimal.ONE, BigDecimal.ONE, List.of(), Map.of(), false, "OK");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
