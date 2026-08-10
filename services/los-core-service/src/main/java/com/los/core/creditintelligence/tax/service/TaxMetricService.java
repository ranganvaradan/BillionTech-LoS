package com.los.core.creditintelligence.tax.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.tax.domain.CiAisSummary;
import com.los.core.creditintelligence.tax.domain.CiForm26AsSummary;
import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import com.los.core.creditintelligence.tax.domain.ItrForm;
import com.los.core.creditintelligence.tax.domain.ReturnVersionType;
import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.util.ItrFormNormalizer;
import com.los.core.creditintelligence.tax.util.ItrFreshnessEvaluator;
import com.los.core.creditintelligence.tax.util.TaxYearUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes and persists ITR / cross-source tax metrics into shared {@link CiMetricResult}.
 */
@Service
@RequiredArgsConstructor
public class TaxMetricService {

    public static final String METRIC_VERSION = "V1";

    public static final String TURNOVER_LATEST = "itr.business.turnover.latest_fy";
    public static final String TURNOVER_PREV = "itr.business.turnover.previous_fy";
    public static final String TURNOVER_CAGR_2Y = "itr.business.turnover.cagr_2y";
    public static final String TURNOVER_CAGR_3Y = "itr.business.turnover.cagr_3y";
    public static final String TOTAL_INCOME_LATEST = "itr.total_income.latest_fy";
    public static final String BUSINESS_INCOME_LATEST = "itr.business_income.latest_fy";
    public static final String AVG_INCOME_2Y = "itr.average_total_income_2y";
    public static final String AVG_INCOME_3Y = "itr.average_total_income_3y";
    public static final String INCOME_GROWTH_YOY = "itr.income_growth_yoy";
    public static final String EBITDA_MARGIN = "itr.ebitda_margin";
    public static final String PAT_MARGIN = "itr.pat_margin";
    public static final String NET_PROFIT_GROWTH = "itr.net_profit_growth";
    public static final String DEBT_EQUITY = "itr.debt_equity";
    public static final String TOL_TNW = "itr.tol_tnw";
    public static final String EFFECTIVE_TAX_RATE = "itr.effective_tax_rate";
    public static final String TAX_PAID_TO_INCOME = "itr.tax_paid_to_income";
    public static final String TDS_TO_INCOME = "itr.tds_to_income";
    public static final String FILING_TIMELINESS = "itr.filing_timeliness";
    public static final String RETURN_CONSISTENCY = "itr.return_consistency_score";
    public static final String XSRC_26AS_TDS = "xsrc.itr_26as_tds_variance";
    public static final String XSRC_AIS_INCOME = "xsrc.itr_ais_income_variance";
    public static final String PAT_ABS = "itr.business.pat.latest_fy";
    public static final String EBITDA_ABS = "itr.business.ebitda.latest_fy";
    public static final String TOL_ABS = "itr.balance.tol.latest_fy";
    public static final String TNW_ABS = "itr.balance.tnw.latest_fy";

    private final CiMetricResultRepository metricResultRepository;
    private final CreditIntelligenceProperties properties;

    public record ReturnBundle(
            CiItrReturn itrReturn,
            CiItrIncome income,
            CiItrBusinessFinancials business,
            List<CiItrPresumptiveIncome> presumptive,
            CiItrTaxSummary taxSummary) {
    }

    public record CrossSourceContext(
            List<CiAisSummary> aisSummaries,
            List<CiForm26AsSummary> form26AsSummaries) {
    }

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            UUID tenantId,
            UUID applicationId,
            UUID sourceRecordId,
            List<ReturnBundle> bundles,
            CrossSourceContext crossSource,
            LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        List<ReturnBundle> effective = sortedEffective(bundles);
        List<CiMetricResult> results = new ArrayList<>();

        results.add(persist(turnoverAt(tenantId, applicationId, sourceRecordId, effective, 0, TURNOVER_LATEST)));
        results.add(persist(turnoverAt(tenantId, applicationId, sourceRecordId, effective, 1, TURNOVER_PREV)));
        results.add(persist(cagr(tenantId, applicationId, sourceRecordId, effective, 2, TURNOVER_CAGR_2Y)));
        results.add(persist(cagr(tenantId, applicationId, sourceRecordId, effective, 3, TURNOVER_CAGR_3Y)));
        results.add(persist(incomeMetric(tenantId, applicationId, sourceRecordId, effective, true, TOTAL_INCOME_LATEST)));
        results.add(persist(incomeMetric(tenantId, applicationId, sourceRecordId, effective, false, BUSINESS_INCOME_LATEST)));
        results.add(persist(averageIncome(tenantId, applicationId, sourceRecordId, effective, 2, AVG_INCOME_2Y)));
        results.add(persist(averageIncome(tenantId, applicationId, sourceRecordId, effective, 3, AVG_INCOME_3Y)));
        results.add(persist(incomeGrowth(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(margin(tenantId, applicationId, sourceRecordId, effective, true)));
        results.add(persist(margin(tenantId, applicationId, sourceRecordId, effective, false)));
        results.add(persist(patGrowth(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(debtEquity(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(tolTnw(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(taxRatio(tenantId, applicationId, sourceRecordId, effective, EFFECTIVE_TAX_RATE, true)));
        results.add(persist(taxRatio(tenantId, applicationId, sourceRecordId, effective, TAX_PAID_TO_INCOME, false)));
        results.add(persist(tdsToIncome(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(filingTimeliness(tenantId, applicationId, sourceRecordId, effective, date)));
        results.add(persist(returnConsistency(tenantId, applicationId, sourceRecordId, effective)));
        results.add(persist(variance26As(tenantId, applicationId, sourceRecordId, effective, crossSource)));
        results.add(persist(varianceAis(tenantId, applicationId, sourceRecordId, effective, crossSource)));
        results.add(persist(absoluteBiz(tenantId, applicationId, sourceRecordId, effective, true, PAT_ABS)));
        results.add(persist(absoluteBiz(tenantId, applicationId, sourceRecordId, effective, false, EBITDA_ABS)));
        results.add(persist(absoluteBs(tenantId, applicationId, sourceRecordId, effective, true, TOL_ABS)));
        results.add(persist(absoluteBs(tenantId, applicationId, sourceRecordId, effective, false, TNW_ABS)));
        return results;
    }

    public List<CiMetricResult> findForApplication(UUID applicationId) {
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null
                        && (m.getMetricCode().startsWith("itr.")
                        || m.getMetricCode().startsWith("xsrc.itr_")))
                .toList();
    }

    private List<ReturnBundle> sortedEffective(List<ReturnBundle> bundles) {
        if (bundles == null) {
            return List.of();
        }
        return bundles.stream()
                .filter(b -> b != null && b.itrReturn() != null && b.itrReturn().isEffective())
                .sorted(Comparator.comparing(
                        (ReturnBundle b) -> b.itrReturn().getAssessmentYear() != null
                                ? b.itrReturn().getAssessmentYear() : "",
                        Comparator.reverseOrder()))
                .toList();
    }

    private CiMetricResult turnoverAt(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, int index, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.size() <= index) {
            evidence.put("reason", "INSUFFICIENT_YEARS");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(index);
        BigDecimal turnover = turnoverOf(b);
        if (turnover == null) {
            evidence.put("reason", "TURNOVER_MISSING");
            evidence.put("assessmentYear", b.itrReturn().getAssessmentYear());
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        evidence.put("assessmentYear", b.itrReturn().getAssessmentYear());
        evidence.put("financialYear", b.itrReturn().getFinancialYear());
        return ok(tenantId, applicationId, sourceRecordId, code, turnover, evidence);
    }

    private CiMetricResult cagr(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, int years, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        evidence.put("minYears", years);
        int minConfigured = Math.max(years, taxCfg().getMinYearsForGrowth());
        if (effective.size() < minConfigured) {
            evidence.put("reason", "INSUFFICIENT_YEARS");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal latest = turnoverOf(effective.get(0));
        BigDecimal oldest = turnoverOf(effective.get(years - 1));
        if (latest == null || oldest == null || oldest.compareTo(BigDecimal.ZERO) <= 0) {
            evidence.put("reason", "TURNOVER_MISSING_OR_NON_POSITIVE_BASE");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        double ratio = latest.doubleValue() / oldest.doubleValue();
        double cagr = Math.pow(ratio, 1.0 / (years - 1)) - 1.0;
        BigDecimal value = BigDecimal.valueOf(cagr).setScale(6, RoundingMode.HALF_UP);
        evidence.put("latest", latest.toPlainString());
        evidence.put("oldest", oldest.toPlainString());
        return ok(tenantId, applicationId, sourceRecordId, code, value, evidence);
    }

    private CiMetricResult incomeMetric(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, boolean total, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            evidence.put("reason", "NO_RETURNS");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(0);
        BigDecimal v = total ? totalIncomeOf(b) : businessIncomeOf(b);
        if (v == null) {
            evidence.put("reason", "INCOME_MISSING");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        evidence.put("assessmentYear", b.itrReturn().getAssessmentYear());
        return ok(tenantId, applicationId, sourceRecordId, code, v, evidence);
    }

    private CiMetricResult averageIncome(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, int years, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.size() < years) {
            evidence.put("reason", "INSUFFICIENT_YEARS");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = 0; i < years; i++) {
            BigDecimal v = totalIncomeOf(effective.get(i));
            if (v != null) {
                sum = sum.add(v);
                count++;
            }
        }
        if (count < years) {
            evidence.put("reason", "INCOME_MISSING_IN_WINDOW");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(years), 2, RoundingMode.HALF_UP);
        return ok(tenantId, applicationId, sourceRecordId, code, avg, evidence);
    }

    private CiMetricResult incomeGrowth(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.size() < 2) {
            evidence.put("reason", "INSUFFICIENT_YEARS");
            return di(tenantId, applicationId, sourceRecordId, INCOME_GROWTH_YOY, evidence);
        }
        BigDecimal latest = totalIncomeOf(effective.get(0));
        BigDecimal prev = totalIncomeOf(effective.get(1));
        if (latest == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", "INCOME_MISSING_OR_ZERO_BASE");
            return di(tenantId, applicationId, sourceRecordId, INCOME_GROWTH_YOY, evidence);
        }
        BigDecimal growth = latest.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
        return ok(tenantId, applicationId, sourceRecordId, INCOME_GROWTH_YOY, growth, evidence);
    }

    private CiMetricResult margin(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, boolean ebitda) {
        String code = ebitda ? EBITDA_MARGIN : PAT_MARGIN;
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            evidence.put("reason", "NO_RETURNS");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(0);
        if (isPresumptive(b)) {
            evidence.put("reason", "PRESUMPTIVE_RETURN");
            return na(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal turnover = turnoverOf(b);
        BigDecimal num = ebitda
                ? (b.business() != null ? b.business().getEbitda() : null)
                : (b.business() != null ? b.business().getProfitAfterTax() : null);
        if (turnover == null || turnover.compareTo(BigDecimal.ZERO) == 0 || num == null) {
            evidence.put("reason", "MISSING_TURNOVER_OR_MARGIN_NUMERATOR");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal margin = num.divide(turnover, 6, RoundingMode.HALF_UP);
        return ok(tenantId, applicationId, sourceRecordId, code, margin, evidence);
    }

    private CiMetricResult patGrowth(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.size() < 2) {
            evidence.put("reason", "INSUFFICIENT_YEARS");
            return di(tenantId, applicationId, sourceRecordId, NET_PROFIT_GROWTH, evidence);
        }
        if (isPresumptive(effective.get(0))) {
            evidence.put("reason", "PRESUMPTIVE_RETURN");
            return na(tenantId, applicationId, sourceRecordId, NET_PROFIT_GROWTH, evidence);
        }
        BigDecimal latest = effective.get(0).business() != null
                ? effective.get(0).business().getProfitAfterTax() : null;
        BigDecimal prev = effective.get(1).business() != null
                ? effective.get(1).business().getProfitAfterTax() : null;
        if (latest == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", "PAT_MISSING_OR_ZERO_BASE");
            return di(tenantId, applicationId, sourceRecordId, NET_PROFIT_GROWTH, evidence);
        }
        BigDecimal growth = latest.subtract(prev).divide(prev.abs(), 6, RoundingMode.HALF_UP);
        return ok(tenantId, applicationId, sourceRecordId, NET_PROFIT_GROWTH, growth, evidence);
    }

    private CiMetricResult debtEquity(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            return di(tenantId, applicationId, sourceRecordId, DEBT_EQUITY, evidence);
        }
        ReturnBundle b = effective.get(0);
        if (isPresumptive(b) || b.business() == null || !hasBs(b)) {
            evidence.put("reason", "NOT_APPLICABLE_NO_BS_OR_PRESUMPTIVE");
            return na(tenantId, applicationId, sourceRecordId, DEBT_EQUITY, evidence);
        }
        BigDecimal debt = firstNonNull(b.business().getTotalBorrowings(), b.business().getTotalLiabilities());
        BigDecimal equity = b.business().getNetWorth();
        if (debt == null || equity == null || equity.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", "BS_FIELDS_MISSING");
            return di(tenantId, applicationId, sourceRecordId, DEBT_EQUITY, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, DEBT_EQUITY,
                debt.divide(equity, 6, RoundingMode.HALF_UP), evidence);
    }

    private CiMetricResult tolTnw(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            return di(tenantId, applicationId, sourceRecordId, TOL_TNW, evidence);
        }
        ReturnBundle b = effective.get(0);
        if (isPresumptive(b) || b.business() == null || !hasBs(b)) {
            evidence.put("reason", "NOT_APPLICABLE_NO_BS_OR_PRESUMPTIVE");
            return na(tenantId, applicationId, sourceRecordId, TOL_TNW, evidence);
        }
        BigDecimal tol = b.business().getTotalLiabilities();
        BigDecimal tnw = b.business().getNetWorth();
        if (tol == null || tnw == null || tnw.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", "BS_FIELDS_MISSING");
            return di(tenantId, applicationId, sourceRecordId, TOL_TNW, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, TOL_TNW,
                tol.divide(tnw, 6, RoundingMode.HALF_UP), evidence);
    }

    private CiMetricResult taxRatio(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, String code, boolean liability) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty() || effective.get(0).taxSummary() == null) {
            evidence.put("reason", "TAX_SUMMARY_MISSING");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(0);
        BigDecimal income = totalIncomeOf(b);
        BigDecimal tax = liability
                ? firstNonNull(b.taxSummary().getTaxLiability(), b.taxSummary().getTaxPayable())
                : b.taxSummary().getTaxPaid();
        if (income == null || income.compareTo(BigDecimal.ZERO) == 0 || tax == null) {
            evidence.put("reason", "TAX_OR_INCOME_MISSING");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, code,
                tax.divide(income, 6, RoundingMode.HALF_UP), evidence);
    }

    private CiMetricResult tdsToIncome(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty() || effective.get(0).taxSummary() == null
                || effective.get(0).taxSummary().getTds() == null) {
            evidence.put("reason", "TDS_MISSING");
            return di(tenantId, applicationId, sourceRecordId, TDS_TO_INCOME, evidence);
        }
        BigDecimal income = totalIncomeOf(effective.get(0));
        if (income == null || income.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", "INCOME_MISSING");
            return di(tenantId, applicationId, sourceRecordId, TDS_TO_INCOME, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, TDS_TO_INCOME,
                effective.get(0).taxSummary().getTds().divide(income, 6, RoundingMode.HALF_UP), evidence);
    }

    private CiMetricResult filingTimeliness(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, LocalDate asOf) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            evidence.put("reason", "NO_RETURNS");
            return di(tenantId, applicationId, sourceRecordId, FILING_TIMELINESS, evidence);
        }
        String latestAy = effective.get(0).itrReturn().getAssessmentYear();
        var freshness = ItrFreshnessEvaluator.evaluate(asOf, latestAy);
        evidence.putAll(freshness.evidence());
        int score = freshness.fresh() ? 100 : 40;
        if (effective.get(0).itrReturn().getFilingDate() != null && freshness.expectedDueDate() != null
                && !effective.get(0).itrReturn().getFilingDate().isAfter(freshness.expectedDueDate())) {
            score = Math.max(score, 90);
        }
        String outcome = freshness.fresh() ? TaxMetricOutcome.PASS.name() : TaxMetricOutcome.REFER.name();
        return result(tenantId, applicationId, sourceRecordId, FILING_TIMELINESS, outcome,
                valueOf(score), outcome, List.of(), List.of(), evidence);
    }

    private CiMetricResult returnConsistency(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            return di(tenantId, applicationId, sourceRecordId, RETURN_CONSISTENCY, evidence);
        }
        int score = 100;
        long revised = effective.stream()
                .filter(b -> ReturnVersionType.REVISED.name().equals(b.itrReturn().getReturnVersionType())
                        || ReturnVersionType.UPDATED.name().equals(b.itrReturn().getReturnVersionType()))
                .count();
        if (revised > 0) {
            score -= (int) Math.min(40, revised * 15);
        }
        long missingTurnover = effective.stream().filter(b -> turnoverOf(b) == null).count();
        if (missingTurnover > 0) {
            score -= (int) Math.min(30, missingTurnover * 10);
        }
        score = Math.max(0, score);
        evidence.put("revisedCount", revised);
        evidence.put("missingTurnoverCount", missingTurnover);
        return ok(tenantId, applicationId, sourceRecordId, RETURN_CONSISTENCY, score, evidence);
    }

    private CiMetricResult variance26As(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, CrossSourceContext cross) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        boolean available = cross != null && cross.form26AsSummaries() != null
                && !cross.form26AsSummaries().isEmpty()
                && cross.form26AsSummaries().stream().anyMatch(s -> s.getTotalTds() != null);
        if (!available) {
            evidence.put("reason", "FORM26AS_MISSING");
            return di(tenantId, applicationId, sourceRecordId, XSRC_26AS_TDS, evidence);
        }
        BigDecimal itrTds = effective.isEmpty() || effective.get(0).taxSummary() == null
                ? null : effective.get(0).taxSummary().getTds();
        BigDecimal asTds = cross.form26AsSummaries().get(0).getTotalTds();
        if (itrTds == null || asTds == null) {
            evidence.put("reason", "TDS_VALUE_MISSING");
            return di(tenantId, applicationId, sourceRecordId, XSRC_26AS_TDS, evidence);
        }
        return varianceResult(tenantId, applicationId, sourceRecordId, XSRC_26AS_TDS,
                itrTds, asTds, taxCfg().getItr26asVarianceWarningPct(),
                taxCfg().getItr26asVarianceMaterialPct(), evidence);
    }

    private CiMetricResult varianceAis(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, CrossSourceContext cross) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        boolean available = cross != null && cross.aisSummaries() != null
                && !cross.aisSummaries().isEmpty()
                && cross.aisSummaries().stream().anyMatch(s -> s.getTotalReportedValue() != null);
        if (!available) {
            evidence.put("reason", "AIS_MISSING");
            return di(tenantId, applicationId, sourceRecordId, XSRC_AIS_INCOME, evidence);
        }
        BigDecimal itrIncome = effective.isEmpty() ? null : totalIncomeOf(effective.get(0));
        BigDecimal ais = cross.aisSummaries().get(0).getTotalReportedValue();
        if (itrIncome == null || ais == null) {
            evidence.put("reason", "INCOME_VALUE_MISSING");
            return di(tenantId, applicationId, sourceRecordId, XSRC_AIS_INCOME, evidence);
        }
        return varianceResult(tenantId, applicationId, sourceRecordId, XSRC_AIS_INCOME,
                itrIncome, ais, taxCfg().getItrAisVarianceWarningPct(),
                taxCfg().getItrAisVarianceMaterialPct(), evidence);
    }

    private CiMetricResult varianceResult(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code,
            BigDecimal left, BigDecimal right, double warnPct, double materialPct,
            Map<String, Object> evidence) {
        BigDecimal base = right.abs().max(BigDecimal.ONE);
        BigDecimal pct = left.subtract(right).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 4, RoundingMode.HALF_UP);
        evidence.put("left", left.toPlainString());
        evidence.put("right", right.toPlainString());
        evidence.put("variancePct", pct.toPlainString());
        evidence.put("warnPct", warnPct);
        evidence.put("materialPct", materialPct);
        String outcome;
        if (pct.doubleValue() <= 0.01) {
            outcome = TaxMetricOutcome.MATCH.name();
        } else if (pct.doubleValue() <= warnPct) {
            outcome = TaxMetricOutcome.ACCEPTABLE_VARIANCE.name();
        } else if (pct.doubleValue() <= materialPct) {
            outcome = TaxMetricOutcome.MATERIAL_VARIANCE.name();
        } else {
            outcome = TaxMetricOutcome.CONFLICT.name();
        }
        return result(tenantId, applicationId, sourceRecordId, code, outcome,
                valueOf(pct), outcome, List.of(), List.of(), evidence);
    }

    private CiMetricResult absoluteBiz(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, boolean pat, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(0);
        if (isPresumptive(b)) {
            evidence.put("reason", "PRESUMPTIVE_RETURN");
            return na(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal v = null;
        if (b.business() != null) {
            v = pat ? b.business().getProfitAfterTax() : b.business().getEbitda();
        }
        if (v == null) {
            evidence.put("reason", "FIELD_MISSING");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, code, v, evidence);
    }

    private CiMetricResult absoluteBs(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<ReturnBundle> effective, boolean tol, String code) {
        Map<String, Object> evidence = baseEvidence(sourceRecordId, effective);
        if (effective.isEmpty()) {
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        ReturnBundle b = effective.get(0);
        if (isPresumptive(b) || !hasBs(b)) {
            evidence.put("reason", "NOT_APPLICABLE_NO_BS_OR_PRESUMPTIVE");
            return na(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        BigDecimal v = tol ? b.business().getTotalLiabilities() : b.business().getNetWorth();
        if (v == null) {
            evidence.put("reason", "FIELD_MISSING");
            return di(tenantId, applicationId, sourceRecordId, code, evidence);
        }
        return ok(tenantId, applicationId, sourceRecordId, code, v, evidence);
    }

    private static BigDecimal turnoverOf(ReturnBundle b) {
        if (b == null || b.business() == null) {
            return null;
        }
        return firstNonNull(b.business().getSalesTurnover(), b.business().getGrossReceipts());
    }

    private static BigDecimal totalIncomeOf(ReturnBundle b) {
        if (b == null) {
            return null;
        }
        if (b.income() != null) {
            BigDecimal t = firstNonNull(b.income().getTotalIncome(), b.income().getGrossTotalIncome(),
                    b.income().getBusinessProfessionIncome());
            if (t != null) {
                return t;
            }
        }
        return turnoverOf(b);
    }

    private static BigDecimal businessIncomeOf(ReturnBundle b) {
        if (b == null) {
            return null;
        }
        if (b.income() != null && b.income().getBusinessProfessionIncome() != null) {
            return b.income().getBusinessProfessionIncome();
        }
        return turnoverOf(b);
    }

    private static boolean isPresumptive(ReturnBundle b) {
        if (b == null || b.itrReturn() == null) {
            return false;
        }
        if (b.presumptive() != null && !b.presumptive().isEmpty()) {
            return true;
        }
        ItrForm form = ItrFormNormalizer.normalize(b.itrReturn().getItrForm());
        return ItrFormNormalizer.isPresumptiveForm(form);
    }

    private static boolean hasBs(ReturnBundle b) {
        if (b.business() == null) {
            return false;
        }
        return b.business().getTotalLiabilities() != null || b.business().getNetWorth() != null;
    }

    private static BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private CreditIntelligenceProperties.Canonicalization.Tax taxCfg() {
        CreditIntelligenceProperties.Canonicalization.Tax t =
                properties.getCanonicalization().getTax();
        return t != null ? t : new CreditIntelligenceProperties.Canonicalization.Tax();
    }

    private CiMetricResult persist(CiMetricResult r) {
        return metricResultRepository.save(r);
    }

    private static CiMetricResult di(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code, Map<String, Object> evidence) {
        return result(tenantId, applicationId, sourceRecordId, code,
                TaxMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                List.of(), List.of(), evidence);
    }

    private static CiMetricResult na(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code, Map<String, Object> evidence) {
        return result(tenantId, applicationId, sourceRecordId, code,
                TaxMetricOutcome.NOT_APPLICABLE.name(), null, "NOT_APPLICABLE",
                List.of(), List.of(), evidence);
    }

    private static CiMetricResult ok(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code,
            Number value, Map<String, Object> evidence) {
        return result(tenantId, applicationId, sourceRecordId, code,
                TaxMetricOutcome.PASS.name(), valueOf(value), "OK",
                List.of(), List.of(), evidence);
    }

    private static CiMetricResult result(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code,
            String outcome, Map<String, Object> value, String quality,
            List<Object> included, List<Object> excluded, Map<String, Object> evidence) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("domain", "TAX");
        meta.put("selection", TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1);
        if (sourceRecordId != null) {
            meta.put("sourceRecordId", sourceRecordId.toString());
        }
        return CiMetricResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .metricCode(code)
                .metricVersion(METRIC_VERSION)
                .outcome(outcome)
                .value(value)
                .dataQualityStatus(quality)
                .includedReferences(included != null ? included : List.of())
                .excludedReferences(excluded != null ? excluded : List.of())
                .sourceRecordIds(sourceRecordId != null ? List.of(sourceRecordId.toString()) : List.of())
                .evidence(evidence != null ? evidence : Map.of())
                .metadata(meta)
                .build();
    }

    private static Map<String, Object> valueOf(Number n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", n instanceof BigDecimal bd ? bd : n);
        return m;
    }

    private static Map<String, Object> baseEvidence(UUID sourceRecordId, List<ReturnBundle> effective) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("metricVersion", METRIC_VERSION);
        e.put("policy", TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1);
        if (sourceRecordId != null) {
            e.put("sourceRecordId", sourceRecordId.toString());
        }
        e.put("effectiveReturnCount", effective != null ? effective.size() : 0);
        if (effective != null && !effective.isEmpty()) {
            e.put("latestAy", effective.get(0).itrReturn().getAssessmentYear());
            var fyLabel = effective.get(0).itrReturn().getFinancialYear();
            if (fyLabel != null) {
                e.put("trailingFy", TaxYearUtils.trailingFinancialYears(fyLabel, Math.min(3, effective.size())));
            }
        }
        return e;
    }
}
