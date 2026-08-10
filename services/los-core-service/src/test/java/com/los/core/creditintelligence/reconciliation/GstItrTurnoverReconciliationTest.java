package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.ExplanationCode;
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

class GstItrTurnoverReconciliationTest {

    private ReconciliationEvaluator evaluator;
    private CiReconciliationDefinition def;
    private final UUID tenant = UUID.randomUUID();
    private final UUID app = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new ReconciliationEvaluator(
                new PeriodAlignmentService(),
                new VarianceCalculator(),
                new ReconciliationConfidenceCalculator(),
                new ReconciliationExplanationBuilder(),
                new DiscrepancySeverityClassifier(),
                new CreditIntelligenceProperties());
        def = CiReconciliationDefinition.builder()
                .reconciliationCode(ReconciliationConstants.XSRC_GST_ITR_TURNOVER)
                .version("V1")
                .name("GST vs ITR")
                .category("TURNOVER")
                .periodAlignmentStrategy("COMMON_OVERLAP")
                .varianceMethod("SYMMETRIC_PERCENT_DIFFERENCE")
                .warningTolerance(BigDecimal.valueOf(5))
                .materialTolerance(BigDecimal.valueOf(15))
                .leftOperandDefinition(Map.of())
                .rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of())
                .allowedDataStatuses(List.of())
                .metadata(Map.of("toleranceProfile", "turnover"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    @Test
    void exactMatch() {
        var r = evaluate(bd("84000000"), bd("84000000"));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.MATCH.name());
    }

    @Test
    void smallVarianceAcceptable() {
        var r = evaluate(bd("84000000"), bd("81000000"));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.ACCEPTABLE_VARIANCE.name());
    }

    @Test
    void materialVariance() {
        var r = evaluate(bd("100000000"), bd("85000000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATERIAL_VARIANCE.name(),
                ReconciliationOutcome.CONFLICT.name());
    }

    @Test
    void presumptiveItrDataInsufficient() {
        var left = op("gst.turnover.trailing_12m", bd("84000000"), false);
        var right = new OperandResolver.ResolvedOperand(
                true, bd("84000000"), "itr.business.turnover.latest_fy", "V1", null,
                LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31),
                BigDecimal.ONE, BigDecimal.ONE, List.of(), Map.of(), true, "OK");
        var r = evaluator.evaluate(tenant, app, null, null, def, left, right,
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.getExplanationCodes()).contains(ExplanationCode.PRESUMPTIVE_ITR.name());
    }

    @Test
    void missingGst() {
        var r = evaluator.evaluate(tenant, app, null, null, def,
                op("gst.turnover.trailing_12m", null, false),
                op("itr.business.turnover.latest_fy", bd("81000000"), false),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.getDataStatus()).isEqualTo("MISSING_LEFT");
    }

    @Test
    void missingItr() {
        var r = evaluator.evaluate(tenant, app, null, null, def,
                op("gst.turnover.trailing_12m", bd("84000000"), false),
                op("itr.business.turnover.latest_fy", null, false),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.getDataStatus()).isEqualTo("MISSING_RIGHT");
    }

    private com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult evaluate(
            BigDecimal gst, BigDecimal itr) {
        return evaluator.evaluate(tenant, app, null, null, def,
                op("gst.turnover.trailing_12m", gst, false),
                op("itr.business.turnover.latest_fy", itr, false),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
    }

    private OperandResolver.ResolvedOperand op(String code, BigDecimal value, boolean presumptive) {
        LocalDate from = LocalDate.of(2024, 4, 1);
        LocalDate to = LocalDate.of(2025, 3, 31);
        return new OperandResolver.ResolvedOperand(
                value != null, value, code, "V1", null, from, to,
                BigDecimal.ONE, BigDecimal.valueOf(0.9), List.of(), Map.of(), presumptive, "OK");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
