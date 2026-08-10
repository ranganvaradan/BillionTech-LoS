package com.los.core.creditintelligence.tax.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.ReturnVersionType;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.provider.KarzaItrCanonicalExtractor;
import com.los.core.creditintelligence.tax.util.ItrFormNormalizer;
import com.los.core.creditintelligence.tax.util.ItrFreshnessEvaluator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shadow-only ITR / AIS / 26AS rules. Production CreditControl gap defaults remain unchanged.
 */
@Component
public class CanonicalTaxRuleEvaluator {

    public static final String DQ_ITR_AVAILABLE = "DQ_ITR_AVAILABLE";
    public static final String DQ_ITR_FRESHNESS = "DQ_ITR_FRESHNESS";
    public static final String DQ_ITR_PAN_MATCH = "DQ_ITR_PAN_MATCH";
    public static final String ITR_MIN_ANNUAL_INCOME = "ITR_MIN_ANNUAL_INCOME";
    public static final String ITR_MIN_BUSINESS_TURNOVER = "ITR_MIN_BUSINESS_TURNOVER";
    public static final String ITR_PROFITABILITY = "ITR_PROFITABILITY";
    public static final String ITR_NET_WORTH = "ITR_NET_WORTH";
    public static final String ITR_FILING_CONSISTENCY = "ITR_FILING_CONSISTENCY";
    public static final String ITR_REVISED_RETURN_REVIEW = "ITR_REVISED_RETURN_REVIEW";
    public static final String ITR_OUTSTANDING_TAX_DEMAND = "ITR_OUTSTANDING_TAX_DEMAND";
    public static final String XSRC_ITR_26AS_TDS_VARIANCE = "XSRC_ITR_26AS_TDS_VARIANCE";
    public static final String XSRC_ITR_AIS_INCOME_VARIANCE = "XSRC_ITR_AIS_INCOME_VARIANCE";

    public record RuleEvalResult(
            String ruleId,
            String ruleVersion,
            String outcome,
            Object value,
            Object threshold,
            Map<String, Object> versions,
            Map<String, Object> evidence) {
    }

    public List<RuleEvalResult> evaluateAll(
            List<CiItrReturn> returns,
            Map<String, CiMetricResult> metricsByCode,
            BigDecimal minIncomeThreshold,
            BigDecimal minTurnoverThreshold,
            boolean minPatPositive,
            String expectedPanLast4,
            LocalDate asOf) {

        List<RuleEvalResult> out = new ArrayList<>();
        out.add(evaluateDqAvailable(returns, metricsByCode));
        out.add(evaluateFreshness(returns, asOf));
        out.add(evaluatePanMatch(returns, expectedPanLast4));
        out.add(evaluateMinIncome(metricsByCode, minIncomeThreshold));
        out.add(evaluateMinTurnover(metricsByCode, minTurnoverThreshold));
        out.add(evaluateProfitability(returns, metricsByCode, minPatPositive));
        out.add(evaluateNetWorth(returns, metricsByCode));
        out.add(evaluateFilingConsistency(metricsByCode));
        out.add(evaluateRevisedReturn(returns));
        out.add(evaluateOutstandingDemand(metricsByCode));
        out.add(evaluateVariance(metricsByCode, TaxMetricService.XSRC_26AS_TDS, XSRC_ITR_26AS_TDS_VARIANCE));
        out.add(evaluateVariance(metricsByCode, TaxMetricService.XSRC_AIS_INCOME, XSRC_ITR_AIS_INCOME_VARIANCE));
        return out;
    }

    public RuleEvalResult evaluateDqAvailable(List<CiItrReturn> returns, Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        boolean available = returns != null && returns.stream()
                .anyMatch(r -> r.isEffective() && !"PULL".equals(r.getAssessmentYear())
                        && !"UNKNOWN".equals(r.getAssessmentYear()));
        if (!available) {
            return new RuleEvalResult(DQ_ITR_AVAILABLE, DQ_ITR_AVAILABLE + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), false, true, versions,
                    Map.of("reason", "NO_EFFECTIVE_RETURN"));
        }
        return new RuleEvalResult(DQ_ITR_AVAILABLE, DQ_ITR_AVAILABLE + "_V1",
                RuleOutcome.PASS.name(), true, true, versions,
                Map.of("returnCount", returns.size()));
    }

    public RuleEvalResult evaluateFreshness(List<CiItrReturn> returns, LocalDate asOf) {
        Map<String, Object> versions = frozenVersions();
        CiItrReturn latest = latestEffective(returns);
        if (latest == null) {
            return new RuleEvalResult(DQ_ITR_FRESHNESS, DQ_ITR_FRESHNESS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "NO_RETURN"));
        }
        var freshness = ItrFreshnessEvaluator.evaluate(asOf, latest.getAssessmentYear());
        String outcome = freshness.fresh() ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(DQ_ITR_FRESHNESS, DQ_ITR_FRESHNESS + "_V1",
                outcome, freshness.actualLatestAy(), freshness.expectedLatestAy(), versions,
                freshness.evidence());
    }

    public RuleEvalResult evaluatePanMatch(List<CiItrReturn> returns, String expectedPanLast4) {
        Map<String, Object> versions = frozenVersions();
        CiItrReturn latest = latestEffective(returns);
        if (latest == null || latest.getPanLast4() == null) {
            return new RuleEvalResult(DQ_ITR_PAN_MATCH, DQ_ITR_PAN_MATCH + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, expectedPanLast4, versions,
                    Map.of("reason", "PAN_NOT_EXTRACTED"));
        }
        if (expectedPanLast4 == null || expectedPanLast4.isBlank()) {
            return new RuleEvalResult(DQ_ITR_PAN_MATCH, DQ_ITR_PAN_MATCH + "_V1",
                    RuleOutcome.REFER.name(), latest.getPanLast4(), null, versions,
                    Map.of("reason", "EXPECTED_PAN_NOT_PROVIDED", "panLast4", latest.getPanLast4()));
        }
        String expected = expectedPanLast4.length() <= 4
                ? expectedPanLast4
                : expectedPanLast4.substring(expectedPanLast4.length() - 4);
        boolean match = Objects.equals(latest.getPanLast4().toUpperCase(), expected.toUpperCase());
        return new RuleEvalResult(DQ_ITR_PAN_MATCH, DQ_ITR_PAN_MATCH + "_V1",
                match ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name(),
                latest.getPanLast4(), expected, versions,
                Map.of("match", match));
    }

    public RuleEvalResult evaluateMinIncome(Map<String, CiMetricResult> metricsByCode, BigDecimal threshold) {
        Map<String, Object> versions = frozenVersions();
        BigDecimal thr = threshold != null ? threshold : new BigDecimal("300000");
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(TaxMetricService.TOTAL_INCOME_LATEST) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_MIN_ANNUAL_INCOME, ITR_MIN_ANNUAL_INCOME + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "INCOME_METRIC_DI",
                            "note", "Shadow only — production SCF_GAP_ITR_INCOME unchanged"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(ITR_MIN_ANNUAL_INCOME, ITR_MIN_ANNUAL_INCOME + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.compareTo(thr) >= 0 ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(ITR_MIN_ANNUAL_INCOME, ITR_MIN_ANNUAL_INCOME + "_V1",
                outcome, value, thr, versions, Map.of("shadowOnly", true));
    }

    public RuleEvalResult evaluateMinTurnover(Map<String, CiMetricResult> metricsByCode, BigDecimal threshold) {
        Map<String, Object> versions = frozenVersions();
        if (threshold == null) {
            return new RuleEvalResult(ITR_MIN_BUSINESS_TURNOVER, ITR_MIN_BUSINESS_TURNOVER + "_V1",
                    RuleOutcome.REFER.name(), null, null, versions,
                    Map.of("reason", "THRESHOLD_NOT_CONFIGURED"));
        }
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(TaxMetricService.TURNOVER_LATEST) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_MIN_BUSINESS_TURNOVER, ITR_MIN_BUSINESS_TURNOVER + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, threshold, versions,
                    Map.of("reason", "TURNOVER_DI"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(ITR_MIN_BUSINESS_TURNOVER, ITR_MIN_BUSINESS_TURNOVER + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, threshold, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.compareTo(threshold) >= 0 ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(ITR_MIN_BUSINESS_TURNOVER, ITR_MIN_BUSINESS_TURNOVER + "_V1",
                outcome, value, threshold, versions, Map.of());
    }

    public RuleEvalResult evaluateProfitability(
            List<CiItrReturn> returns, Map<String, CiMetricResult> metricsByCode, boolean minPatPositive) {
        Map<String, Object> versions = frozenVersions();
        CiItrReturn latest = latestEffective(returns);
        if (latest != null && ItrFormNormalizer.isPresumptiveForm(
                ItrFormNormalizer.normalize(latest.getItrForm()))) {
            return new RuleEvalResult(ITR_PROFITABILITY, ITR_PROFITABILITY + "_V1",
                    RuleOutcome.NOT_APPLICABLE.name(), null, minPatPositive, versions,
                    Map.of("reason", "PRESUMPTIVE_RETURN"));
        }
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(TaxMetricService.PAT_MARGIN) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_PROFITABILITY, ITR_PROFITABILITY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, minPatPositive, versions,
                    Map.of("reason", "PAT_MARGIN_DI"));
        }
        if (TaxMetricOutcome.NOT_APPLICABLE.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_PROFITABILITY, ITR_PROFITABILITY + "_V1",
                    RuleOutcome.NOT_APPLICABLE.name(), null, minPatPositive, versions,
                    Map.of("reason", "METRIC_NA"));
        }
        BigDecimal margin = unwrapBd(m.getValue());
        if (margin == null) {
            return new RuleEvalResult(ITR_PROFITABILITY, ITR_PROFITABILITY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, minPatPositive, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        boolean pass = !minPatPositive || margin.compareTo(BigDecimal.ZERO) > 0;
        return new RuleEvalResult(ITR_PROFITABILITY, ITR_PROFITABILITY + "_V1",
                pass ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name(),
                margin, minPatPositive, versions, Map.of());
    }

    public RuleEvalResult evaluateNetWorth(List<CiItrReturn> returns, Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiItrReturn latest = latestEffective(returns);
        if (latest != null && ItrFormNormalizer.isPresumptiveForm(
                ItrFormNormalizer.normalize(latest.getItrForm()))) {
            return new RuleEvalResult(ITR_NET_WORTH, ITR_NET_WORTH + "_V1",
                    RuleOutcome.NOT_APPLICABLE.name(), null, null, versions,
                    Map.of("reason", "PRESUMPTIVE_NO_BS"));
        }
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(TaxMetricService.TOL_TNW) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_NET_WORTH, ITR_NET_WORTH + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "TOL_TNW_DI"));
        }
        if (TaxMetricOutcome.NOT_APPLICABLE.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_NET_WORTH, ITR_NET_WORTH + "_V1",
                    RuleOutcome.NOT_APPLICABLE.name(), null, null, versions,
                    Map.of("reason", "METRIC_NA"));
        }
        return new RuleEvalResult(ITR_NET_WORTH, ITR_NET_WORTH + "_V1",
                RuleOutcome.PASS.name(), unwrapBd(m.getValue()), null, versions, Map.of());
    }

    public RuleEvalResult evaluateFilingConsistency(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(TaxMetricService.RETURN_CONSISTENCY) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ITR_FILING_CONSISTENCY, ITR_FILING_CONSISTENCY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, 60, versions,
                    Map.of("reason", "METRIC_MISSING"));
        }
        Integer score = unwrapInt(m.getValue());
        if (score == null) {
            return new RuleEvalResult(ITR_FILING_CONSISTENCY, ITR_FILING_CONSISTENCY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, 60, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        return new RuleEvalResult(ITR_FILING_CONSISTENCY, ITR_FILING_CONSISTENCY + "_V1",
                score >= 60 ? RuleOutcome.PASS.name() : RuleOutcome.REFER.name(),
                score, 60, versions, Map.of());
    }

    public RuleEvalResult evaluateRevisedReturn(List<CiItrReturn> returns) {
        Map<String, Object> versions = frozenVersions();
        if (returns == null || returns.isEmpty()) {
            return new RuleEvalResult(ITR_REVISED_RETURN_REVIEW, ITR_REVISED_RETURN_REVIEW + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "NO_RETURNS"));
        }
        boolean revised = returns.stream().anyMatch(r ->
                ReturnVersionType.REVISED.name().equals(r.getReturnVersionType())
                        || ReturnVersionType.UPDATED.name().equals(r.getReturnVersionType()));
        return new RuleEvalResult(ITR_REVISED_RETURN_REVIEW, ITR_REVISED_RETURN_REVIEW + "_V1",
                revised ? RuleOutcome.REFER.name() : RuleOutcome.PASS.name(),
                revised, false, versions,
                Map.of("revisedPresent", revised));
    }

    public RuleEvalResult evaluateOutstandingDemand(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        // Demand is on tax summary evidence via tax ratios DI when missing — treat as REFER if unknown
        return new RuleEvalResult(ITR_OUTSTANDING_TAX_DEMAND, ITR_OUTSTANDING_TAX_DEMAND + "_V1",
                RuleOutcome.DATA_INSUFFICIENT.name(), null, BigDecimal.ZERO, versions,
                Map.of("reason", "DEMAND_FIELD_FUTURE_READY",
                        "note", "Karza return-forms fixture typically lacks outstanding demand"));
    }

    public RuleEvalResult evaluateVariance(
            Map<String, CiMetricResult> metricsByCode, String metricCode, String ruleId) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(metricCode) : null;
        if (m == null || TaxMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ruleId, ruleId + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "METRIC_DATA_INSUFFICIENT"));
        }
        String metricOutcome = m.getOutcome();
        String ruleOutcome;
        if (TaxMetricOutcome.MATCH.name().equals(metricOutcome)
                || TaxMetricOutcome.ACCEPTABLE_VARIANCE.name().equals(metricOutcome)) {
            ruleOutcome = RuleOutcome.PASS.name();
        } else if (TaxMetricOutcome.MATERIAL_VARIANCE.name().equals(metricOutcome)
                || TaxMetricOutcome.CONFLICT.name().equals(metricOutcome)) {
            ruleOutcome = RuleOutcome.FAIL.name();
        } else {
            ruleOutcome = RuleOutcome.REFER.name();
        }
        return new RuleEvalResult(ruleId, ruleId + "_V1",
                ruleOutcome, metricOutcome, null, versions,
                Map.of("varianceOutcome", metricOutcome));
    }

    public static Map<String, Object> frozenVersions() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("parserVersion", KarzaItrCanonicalExtractor.PARSER_VERSION);
        v.put("normalizerVersion", TaxNormalizationService.NORMALIZER_VERSION);
        v.put("metricVersion", TaxMetricService.METRIC_VERSION);
        v.put("freshnessPolicy", ItrFreshnessEvaluator.class.getSimpleName());
        return v;
    }

    private static CiItrReturn latestEffective(List<CiItrReturn> returns) {
        if (returns == null) {
            return null;
        }
        return returns.stream()
                .filter(CiItrReturn::isEffective)
                .filter(r -> !"PULL".equals(r.getAssessmentYear()) && !"UNKNOWN".equals(r.getAssessmentYear()))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal unwrapBd(Map<String, Object> value) {
        if (value == null || value.get("v") == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value.get("v")));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer unwrapInt(Map<String, Object> value) {
        if (value == null || value.get("v") == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value.get("v"))).intValue();
        } catch (Exception e) {
            return null;
        }
    }
}
