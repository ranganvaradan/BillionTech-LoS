package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.service.CanonicalTaxRuleEvaluator;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalTaxRuleEvaluatorTest {

    private final CanonicalTaxRuleEvaluator evaluator = new CanonicalTaxRuleEvaluator();

    @Test
    void dqAvailableAndMinIncome() {
        CiItrReturn r = CiItrReturn.builder()
                .id(UUID.randomUUID())
                .assessmentYear("2025-26")
                .itrForm("ITR_6")
                .effective(true)
                .panLast4("0052")
                .build();
        Map<String, CiMetricResult> metrics = Map.of(
                TaxMetricService.TOTAL_INCOME_LATEST, CiMetricResult.builder()
                        .metricCode(TaxMetricService.TOTAL_INCOME_LATEST)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("500000")))
                        .build(),
                TaxMetricService.TURNOVER_LATEST, CiMetricResult.builder()
                        .metricCode(TaxMetricService.TURNOVER_LATEST)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.PASS.name())
                        .value(Map.of("v", new BigDecimal("500000")))
                        .build(),
                TaxMetricService.XSRC_AIS_INCOME, CiMetricResult.builder()
                        .metricCode(TaxMetricService.XSRC_AIS_INCOME)
                        .metricVersion("V1")
                        .outcome(TaxMetricOutcome.DATA_INSUFFICIENT.name())
                        .build());

        var results = evaluator.evaluateAll(
                List.of(r), metrics, new BigDecimal("300000"), null, true, "0052",
                LocalDate.of(2026, 6, 1));

        assertThat(results.stream().filter(x -> CanonicalTaxRuleEvaluator.DQ_ITR_AVAILABLE.equals(x.ruleId()))
                .findFirst().orElseThrow().outcome()).isEqualTo(RuleOutcome.PASS.name());
        assertThat(results.stream().filter(x -> CanonicalTaxRuleEvaluator.ITR_MIN_ANNUAL_INCOME.equals(x.ruleId()))
                .findFirst().orElseThrow().outcome()).isEqualTo(RuleOutcome.PASS.name());
        assertThat(results.stream().filter(x -> CanonicalTaxRuleEvaluator.XSRC_ITR_AIS_INCOME_VARIANCE.equals(x.ruleId()))
                .findFirst().orElseThrow().outcome()).isEqualTo(RuleOutcome.DATA_INSUFFICIENT.name());
        assertThat(results.stream().filter(x -> CanonicalTaxRuleEvaluator.DQ_ITR_PAN_MATCH.equals(x.ruleId()))
                .findFirst().orElseThrow().outcome()).isEqualTo(RuleOutcome.PASS.name());
    }

    @Test
    void presumptive_profitabilityNotApplicable() {
        CiItrReturn r = CiItrReturn.builder()
                .assessmentYear("2025-26")
                .itrForm("ITR_4")
                .effective(true)
                .build();
        var result = evaluator.evaluateProfitability(List.of(r), Map.of(), true);
        assertThat(result.outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE.name());
        var nw = evaluator.evaluateNetWorth(List.of(r), Map.of());
        assertThat(nw.outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE.name());
    }
}
