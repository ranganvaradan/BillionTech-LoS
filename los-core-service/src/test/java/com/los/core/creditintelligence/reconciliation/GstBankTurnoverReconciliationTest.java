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

class GstBankTurnoverReconciliationTest {

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
        def = baseDef(ReconciliationConstants.XSRC_GST_BANK_TURNOVER);
    }

    @Test
    void operatingReceiptsAlign() {
        var r = evaluate(bd("77000000"), bd("77000000"));
        assertThat(r.getOutcome()).isEqualTo(ReconciliationOutcome.MATCH.name());
    }

    @Test
    void selfTransfersExcludedStillComparable() {
        // Bank already adjusted; amounts align after exclusion
        var left = op("gst.turnover.trailing_12m", bd("80000000"),
                LocalDate.of(2024, 8, 1), LocalDate.of(2025, 7, 31));
        var right = new OperandResolver.ResolvedOperand(
                true, bd("78000000"), "banking.adjusted_business_credits_12m", "V1", null,
                LocalDate.of(2024, 8, 1), LocalDate.of(2025, 7, 31),
                BigDecimal.ONE, BigDecimal.valueOf(0.9), List.of(),
                Map.of("excluded", List.of("SELF_TRANSFER")), false, "OK");
        var r = evaluator.evaluate(tenant, app, null, null, def, left, right,
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATCH.name(),
                ReconciliationOutcome.ACCEPTABLE_VARIANCE.name());
    }

    @Test
    void incompleteBankCoverage() {
        var left = op("gst.turnover.trailing_12m", bd("84000000"),
                LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31));
        var right = op("banking.adjusted_business_credits_12m", bd("70000000"),
                LocalDate.of(2024, 10, 1), LocalDate.of(2025, 3, 31));
        var r = evaluator.evaluate(tenant, app, null, null, def, left, right,
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
        assertThat(r.getOutcome()).isNotEqualTo(ReconciliationOutcome.ERROR.name());
        // May be DI due to overlap rules or PARTIAL with variance
        assertThat(r.getDataStatus()).isIn("COMPLETE", "PARTIAL", "PERIOD_MISMATCH", "LOW_CONFIDENCE");
    }

    @Test
    void materialGstBankVariance() {
        var r = evaluate(bd("100000000"), bd("70000000"));
        assertThat(r.getOutcome()).isIn(
                ReconciliationOutcome.MATERIAL_VARIANCE.name(),
                ReconciliationOutcome.CONFLICT.name());
    }

    private com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult evaluate(
            BigDecimal gst, BigDecimal bank) {
        return evaluator.evaluate(tenant, app, null, null, def,
                op("gst.turnover.trailing_12m", gst, LocalDate.of(2024, 8, 1), LocalDate.of(2025, 7, 31)),
                op("banking.adjusted_business_credits_12m", bank,
                        LocalDate.of(2024, 8, 1), LocalDate.of(2025, 7, 31)),
                SubjectMatchStatus.UNKNOWN, LocalDate.of(2025, 8, 1));
    }

    private static OperandResolver.ResolvedOperand op(
            String code, BigDecimal value, LocalDate from, LocalDate to) {
        return new OperandResolver.ResolvedOperand(
                value != null, value, code, "V1", null, from, to,
                BigDecimal.ONE, BigDecimal.valueOf(0.9), List.of(), Map.of(), false, "OK");
    }

    private static CiReconciliationDefinition baseDef(String code) {
        return CiReconciliationDefinition.builder()
                .reconciliationCode(code).version("V1").name(code).category("TURNOVER")
                .periodAlignmentStrategy("COMMON_OVERLAP")
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

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
