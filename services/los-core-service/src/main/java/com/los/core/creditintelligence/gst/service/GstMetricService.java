package com.los.core.creditintelligence.gst.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.gst.domain.CiGstPeriodFinancials;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.CiGstReturnPeriod;
import com.los.core.creditintelligence.gst.domain.GstFilingStatus;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.domain.GstReturnType;
import com.los.core.creditintelligence.gst.repository.CiGstPeriodFinancialsRepository;
import com.los.core.creditintelligence.gst.util.GstFilingStatusNormalizer;
import com.los.core.creditintelligence.gst.util.GstFreshnessEvaluator;
import com.los.core.creditintelligence.gst.util.GstPeriodUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes and persists GST metrics into shared {@link CiMetricResult} table.
 */
@Service
@RequiredArgsConstructor
public class GstMetricService {

    public static final String METRIC_VERSION = "V1";
    public static final String TRAILING_12M = "gst.turnover.trailing_12m";
    public static final String TRAILING_3M = "gst.turnover.trailing_3m";
    public static final String TRAILING_6M = "gst.turnover.trailing_6m";
    public static final String CURRENT_FY_YTD = "gst.turnover.current_fy_ytd";
    public static final String ANNUALIZED = "gst.turnover.annualized_current_run_rate";
    public static final String TIMELINESS = "gst.filing.timeliness_score";
    public static final String VARIANCE = "gst.gstr1_gstr3b_turnover_variance";
    public static final String MISSING_12M = "gst.return.missing_count_12m";
    public static final String LATE_12M = "gst.return.late_count_12m";
    public static final String MAX_DELAY_12M = "gst.return.max_delay_days_12m";

    private final CiMetricResultRepository metricResultRepository;
    private final CiGstPeriodFinancialsRepository financialsRepository;
    private final CreditIntelligenceProperties properties;

    public record PeriodBundle(
            CiGstRegistration registration,
            List<CiGstReturnPeriod> periods,
            Map<UUID, CiGstPeriodFinancials> financialsByPeriodId) {
    }

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            UUID tenantId,
            UUID applicationId,
            UUID sourceRecordId,
            List<PeriodBundle> bundles,
            LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        List<CiMetricResult> results = new ArrayList<>();
        results.add(persist(computeTrailingTurnover(tenantId, applicationId, sourceRecordId, bundles, date, 12, TRAILING_12M, true)));
        results.add(persist(computeTrailingTurnover(tenantId, applicationId, sourceRecordId, bundles, date, 3, TRAILING_3M, false)));
        results.add(persist(computeTrailingTurnover(tenantId, applicationId, sourceRecordId, bundles, date, 6, TRAILING_6M, false)));
        results.add(persist(computeCurrentFyYtd(tenantId, applicationId, sourceRecordId, bundles, date)));
        results.add(persist(computeAnnualized(tenantId, applicationId, sourceRecordId, bundles, date)));
        results.add(persist(computeTimeliness(tenantId, applicationId, sourceRecordId, bundles, date)));
        results.add(persist(computeVariance(tenantId, applicationId, sourceRecordId, bundles, date)));
        results.add(persist(computeFilingCount(tenantId, applicationId, sourceRecordId, bundles, date, MISSING_12M, "MISSING")));
        results.add(persist(computeFilingCount(tenantId, applicationId, sourceRecordId, bundles, date, LATE_12M, "LATE")));
        results.add(persist(computeMaxDelay(tenantId, applicationId, sourceRecordId, bundles, date)));
        return results;
    }

    public List<CiMetricResult> findForApplication(UUID applicationId) {
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("gst."))
                .toList();
    }

    public CiMetricResult computeTrailingTurnover(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf, int months, String code, boolean enforceMinCompleteness) {

        CreditIntelligenceProperties.Canonicalization.Gst cfg = gstCfg();
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, cfg.getFilingLagDays());
        List<String> expected = GstPeriodUtils.trailingMonths(end, months);
        Set<String> expectedSet = new HashSet<>(expected);

        BigDecimal sum = BigDecimal.ZERO;
        Set<String> available = new HashSet<>();
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("windowMonths", months);
        evidence.put("windowEnd", end.toString());
        evidence.put("preferReturnType", GstReturnType.GSTR1.name());
        evidence.put("aggregation", "SUM_ACTIVE_GSTIN_PERIODS");

        if (bundles == null || bundles.isEmpty()) {
            evidence.put("reason", "NO_GST_REGISTRATION");
            return result(tenantId, applicationId, sourceRecordId, code,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, excluded, evidence);
        }

        for (PeriodBundle bundle : bundles) {
            if (!isActiveRegistration(bundle.registration())) {
                excluded.add(Map.of("gstin", bundle.registration().getGstin(), "reason", "INACTIVE_GSTIN"));
                continue;
            }
            Map<String, PeriodAmount> gstr1ByPeriod = indexTurnovers(bundle, GstReturnType.GSTR1.name());
            for (String period : expected) {
                PeriodAmount pa = gstr1ByPeriod.get(period);
                if (pa == null) {
                    continue;
                }
                // Valid zero: turnoverPresent with value 0 is included
                if (pa.amount() == null && !pa.zeroKnown()) {
                    excluded.add(Map.of("gstin", bundle.registration().getGstin(),
                            "period", period, "reason", "AMOUNT_MISSING"));
                    continue;
                }
                BigDecimal amt = pa.amount() != null ? pa.amount() : BigDecimal.ZERO;
                available.add(period);
                sum = sum.add(amt);
                included.add(Map.of(
                        "gstin", bundle.registration().getGstin(),
                        "period", period,
                        "returnType", GstReturnType.GSTR1.name(),
                        "taxableTurnover", amt.toPlainString()));
            }
        }

        List<String> missing = expected.stream().filter(p -> !available.contains(p)).toList();
        double completeness = expected.isEmpty() ? 0.0 : (double) available.size() / expected.size();
        evidence.put("periodsExpected", expected.size());
        evidence.put("periodsAvailable", available.size());
        evidence.put("periodsMissing", missing);
        evidence.put("completeness", completeness);
        evidence.put("gstinCount", bundles.size());

        int minMonths = Math.max(1, (int) Math.ceil(months * cfg.getMinCompletenessForTrailing12m()));
        if (months == 12) {
            minMonths = Math.max(9, minMonths); // e.g. 9 months minimum for 12m window
        }
        boolean di = enforceMinCompleteness
                && (completeness < cfg.getMinCompletenessForTrailing12m() || available.size() < minMonths);

        if (di || available.isEmpty() && enforceMinCompleteness) {
            evidence.put("reason", available.isEmpty() ? "NO_PERIOD_AMOUNTS" : "INCOMPLETE_WINDOW");
            return result(tenantId, applicationId, sourceRecordId, code,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, excluded, evidence);
        }
        if (available.isEmpty()) {
            evidence.put("reason", "NO_PERIOD_AMOUNTS");
            return result(tenantId, applicationId, sourceRecordId, code,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, excluded, evidence);
        }

        evidence.put("sum", sum.toPlainString());
        return result(tenantId, applicationId, sourceRecordId, code,
                GstMetricOutcome.PASS.name(), valueOf(sum), "OK", included, excluded, evidence);
    }

    public CiMetricResult computeCurrentFyYtd(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf) {
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> expected = GstPeriodUtils.currentFyYtdPeriods(end);
        return sumOverExpected(tenantId, applicationId, sourceRecordId, bundles, expected, CURRENT_FY_YTD, false);
    }

    public CiMetricResult computeAnnualized(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf) {
        int minMonths = gstCfg().getMinMonthsForAnnualization();
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> ytd = GstPeriodUtils.currentFyYtdPeriods(end);
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("minMonthsForAnnualization", minMonths);
        evidence.put("method", "GST_ANNUALIZATION_V1");
        evidence.put("neverSubstitutesTrailing12m", true);

        BigDecimal sum = BigDecimal.ZERO;
        int monthsUsed = 0;
        List<Object> included = new ArrayList<>();
        for (PeriodBundle bundle : safe(bundles)) {
            if (!isActiveRegistration(bundle.registration())) {
                continue;
            }
            Map<String, PeriodAmount> byPeriod = indexTurnovers(bundle, GstReturnType.GSTR1.name());
            for (String period : ytd) {
                PeriodAmount pa = byPeriod.get(period);
                if (pa == null || (pa.amount() == null && !pa.zeroKnown())) {
                    continue;
                }
                BigDecimal amt = pa.amount() != null ? pa.amount() : BigDecimal.ZERO;
                sum = sum.add(amt);
                monthsUsed++;
                included.add(Map.of("gstin", bundle.registration().getGstin(),
                        "period", period, "taxableTurnover", amt.toPlainString()));
            }
        }
        // For multi-GSTIN, monthsUsed counts period-gstin pairs; use unique periods
        Set<String> uniquePeriods = included.stream()
                .map(o -> ((Map<?, ?>) o).get("period"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        int uniqueMonths = uniquePeriods.size();
        evidence.put("monthsUsed", uniqueMonths);
        evidence.put("periodsExpectedYtd", ytd.size());
        evidence.put("ytdSum", sum.toPlainString());

        boolean missingMaterial = uniqueMonths < minMonths
                || (ytd.size() > 0 && (double) uniqueMonths / ytd.size() < gstCfg().getMinCompletenessForTrailing12m());
        if (uniqueMonths < minMonths || missingMaterial) {
            evidence.put("reason", "ANNUALIZATION_GATED");
            return result(tenantId, applicationId, sourceRecordId, ANNUALIZED,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, List.of(), evidence);
        }
        BigDecimal annualized = sum.multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(uniqueMonths), 2, RoundingMode.HALF_UP);
        evidence.put("annualized", annualized.toPlainString());
        return result(tenantId, applicationId, sourceRecordId, ANNUALIZED,
                GstMetricOutcome.PASS.name(), valueOf(annualized), "OK", included, List.of(), evidence);
    }

    public CiMetricResult computeTimeliness(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf) {
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> expected = GstPeriodUtils.trailingMonths(end, 6);
        int onTime = 0;
        int late = 0;
        int missing = 0;
        List<Object> included = new ArrayList<>();
        for (String period : expected) {
            FilingAgg agg = aggregateFiling(bundles, period, GstReturnType.GSTR1.name());
            if (agg == FilingAgg.ON_TIME) {
                onTime++;
            } else if (agg == FilingAgg.LATE) {
                late++;
            } else {
                missing++;
            }
            included.add(Map.of("period", period, "status", agg.name()));
        }
        // score = 100 - 10*late - 20*missing, floored at 0
        int score = Math.max(0, 100 - (late * 10) - (missing * 20));
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("windowMonths", 6);
        evidence.put("onTime", onTime);
        evidence.put("late", late);
        evidence.put("missing", missing);
        evidence.put("score", score);
        return result(tenantId, applicationId, sourceRecordId, TIMELINESS,
                GstMetricOutcome.PASS.name(), valueOf(score), "OK", included, List.of(), evidence);
    }

    public CiMetricResult computeVariance(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf) {
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> expected = GstPeriodUtils.trailingMonths(end, 12);
        double warningPct = gstCfg().getGstr1Gstr3bVarianceWarningPct();
        double materialPct = gstCfg().getGstr1Gstr3bVarianceMaterialPct();

        BigDecimal gstr1Sum = BigDecimal.ZERO;
        BigDecimal gstr3bSum = BigDecimal.ZERO;
        int overlap = 0;
        List<Object> included = new ArrayList<>();
        for (PeriodBundle bundle : safe(bundles)) {
            Map<String, PeriodAmount> g1 = indexTurnovers(bundle, GstReturnType.GSTR1.name());
            Map<String, PeriodAmount> g3 = indexTurnovers(bundle, GstReturnType.GSTR3B.name());
            for (String period : expected) {
                PeriodAmount a = g1.get(period);
                PeriodAmount b = g3.get(period);
                if (a == null || b == null) {
                    continue;
                }
                if ((a.amount() == null && !a.zeroKnown()) || (b.amount() == null && !b.zeroKnown())) {
                    continue;
                }
                BigDecimal av = a.amount() != null ? a.amount() : BigDecimal.ZERO;
                BigDecimal bv = b.amount() != null ? b.amount() : BigDecimal.ZERO;
                gstr1Sum = gstr1Sum.add(av);
                gstr3bSum = gstr3bSum.add(bv);
                overlap++;
                included.add(Map.of(
                        "gstin", bundle.registration().getGstin(),
                        "period", period,
                        "gstr1", av.toPlainString(),
                        "gstr3b", bv.toPlainString()));
            }
        }

        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("overlapPeriods", overlap);
        evidence.put("gstr1Sum", gstr1Sum.toPlainString());
        evidence.put("gstr3bSum", gstr3bSum.toPlainString());
        evidence.put("warningPct", warningPct);
        evidence.put("materialPct", materialPct);

        if (overlap == 0) {
            evidence.put("reason", "NO_OVERLAPPING_PERIODS");
            return result(tenantId, applicationId, sourceRecordId, VARIANCE,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, List.of(), evidence);
        }

        BigDecimal base = gstr1Sum.abs().max(BigDecimal.ONE);
        BigDecimal diff = gstr1Sum.subtract(gstr3bSum).abs();
        double pct = diff.multiply(BigDecimal.valueOf(100))
                .divide(base, 4, RoundingMode.HALF_UP).doubleValue();
        evidence.put("variancePct", pct);

        String outcome;
        if (pct <= 0.01) {
            outcome = GstMetricOutcome.MATCH.name();
        } else if (pct <= warningPct) {
            outcome = GstMetricOutcome.ACCEPTABLE_VARIANCE.name();
        } else if (pct <= materialPct) {
            outcome = GstMetricOutcome.MATERIAL_VARIANCE.name();
        } else {
            outcome = GstMetricOutcome.CONFLICT.name();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("v", pct);
        value.put("gstr1Sum", gstr1Sum.toPlainString());
        value.put("gstr3bSum", gstr3bSum.toPlainString());
        return result(tenantId, applicationId, sourceRecordId, VARIANCE,
                outcome, value, "OK", included, List.of(), evidence);
    }

    public CiMetricResult computeFilingCount(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf, String code, String mode) {
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> expected = GstPeriodUtils.trailingMonths(end, 12);
        int count = 0;
        List<Object> included = new ArrayList<>();
        for (String period : expected) {
            FilingAgg agg = aggregateFiling(bundles, period, GstReturnType.GSTR1.name());
            if ("MISSING".equals(mode) && agg == FilingAgg.MISSING) {
                count++;
                included.add(Map.of("period", period));
            } else if ("LATE".equals(mode) && agg == FilingAgg.LATE) {
                count++;
                included.add(Map.of("period", period));
            }
        }
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("count", count);
        evidence.put("mode", mode);
        return result(tenantId, applicationId, sourceRecordId, code,
                GstMetricOutcome.PASS.name(), valueOf(count), "OK", included, List.of(), evidence);
    }

    public CiMetricResult computeMaxDelay(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, LocalDate asOf) {
        YearMonth end = GstPeriodUtils.expectedLatestCompletedReturnPeriod(asOf, gstCfg().getFilingLagDays());
        List<String> expected = GstPeriodUtils.trailingMonths(end, 12);
        int maxDelay = 0;
        List<Object> included = new ArrayList<>();
        for (PeriodBundle bundle : safe(bundles)) {
            for (CiGstReturnPeriod p : safePeriods(bundle)) {
                if (!GstReturnType.GSTR1.name().equals(p.getReturnType())) {
                    continue;
                }
                if (!expected.contains(p.getPeriodYyyyMm())) {
                    continue;
                }
                if (p.getFilingDelayDays() != null && p.getFilingDelayDays() > maxDelay) {
                    maxDelay = p.getFilingDelayDays();
                    included.add(Map.of("period", p.getPeriodYyyyMm(),
                            "delayDays", p.getFilingDelayDays(),
                            "gstin", bundle.registration().getGstin()));
                }
            }
        }
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, asOf);
        evidence.put("maxDelayDays", maxDelay);
        return result(tenantId, applicationId, sourceRecordId, MAX_DELAY_12M,
                GstMetricOutcome.PASS.name(), valueOf(maxDelay), "OK", included, List.of(), evidence);
    }

    private CiMetricResult sumOverExpected(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<PeriodBundle> bundles, List<String> expected, String code, boolean enforceCompleteness) {
        BigDecimal sum = BigDecimal.ZERO;
        Set<String> available = new HashSet<>();
        List<Object> included = new ArrayList<>();
        for (PeriodBundle bundle : safe(bundles)) {
            if (!isActiveRegistration(bundle.registration())) {
                continue;
            }
            Map<String, PeriodAmount> byPeriod = indexTurnovers(bundle, GstReturnType.GSTR1.name());
            for (String period : expected) {
                PeriodAmount pa = byPeriod.get(period);
                if (pa == null || (pa.amount() == null && !pa.zeroKnown())) {
                    continue;
                }
                BigDecimal amt = pa.amount() != null ? pa.amount() : BigDecimal.ZERO;
                available.add(period);
                sum = sum.add(amt);
                included.add(Map.of("gstin", bundle.registration().getGstin(),
                        "period", period, "taxableTurnover", amt.toPlainString()));
            }
        }
        Map<String, Object> evidence = baseEvidence(sourceRecordId, bundles, null);
        evidence.put("periodsExpected", expected.size());
        evidence.put("periodsAvailable", available.size());
        if (available.isEmpty()) {
            evidence.put("reason", "NO_PERIOD_AMOUNTS");
            return result(tenantId, applicationId, sourceRecordId, code,
                    GstMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                    included, List.of(), evidence);
        }
        return result(tenantId, applicationId, sourceRecordId, code,
                GstMetricOutcome.PASS.name(), valueOf(sum), "OK", included, List.of(), evidence);
    }

    private Map<String, PeriodAmount> indexTurnovers(PeriodBundle bundle, String returnType) {
        Map<String, PeriodAmount> map = new HashMap<>();
        for (CiGstReturnPeriod p : safePeriods(bundle)) {
            if (!returnType.equals(p.getReturnType()) || !p.isEffective()) {
                continue;
            }
            CiGstPeriodFinancials fin = bundle.financialsByPeriodId() != null
                    ? bundle.financialsByPeriodId().get(p.getId()) : null;
            if (fin == null && p.getId() != null) {
                fin = financialsRepository.findByReturnPeriodId(p.getId()).orElse(null);
            }
            boolean zeroKnown = fin != null && fin.getTaxableTurnover() != null
                    && fin.getTaxableTurnover().compareTo(BigDecimal.ZERO) == 0;
            boolean hasAmount = fin != null && fin.getTaxableTurnover() != null;
            // Period exists in filing without financials → not a valid zero
            map.put(p.getPeriodYyyyMm(), new PeriodAmount(
                    hasAmount ? fin.getTaxableTurnover() : null,
                    zeroKnown || hasAmount));
        }
        return map;
    }

    private FilingAgg aggregateFiling(List<PeriodBundle> bundles, String period, String returnType) {
        boolean anyFiled = false;
        boolean anyLate = false;
        boolean anyPresent = false;
        for (PeriodBundle bundle : safe(bundles)) {
            for (CiGstReturnPeriod p : safePeriods(bundle)) {
                if (!p.isEffective() || !returnType.equals(p.getReturnType())
                        || !period.equals(p.getPeriodYyyyMm())) {
                    continue;
                }
                anyPresent = true;
                GstFilingStatus st;
                try {
                    st = GstFilingStatus.valueOf(p.getFilingStatus());
                } catch (Exception e) {
                    st = GstFilingStatus.UNKNOWN;
                }
                if (GstFilingStatusNormalizer.isFiledLike(st)) {
                    anyFiled = true;
                    if (st == GstFilingStatus.LATE_FILED
                            || (p.getFilingDelayDays() != null && p.getFilingDelayDays() > 0)) {
                        anyLate = true;
                    }
                }
            }
        }
        if (!anyPresent || !anyFiled) {
            return FilingAgg.MISSING;
        }
        return anyLate ? FilingAgg.LATE : FilingAgg.ON_TIME;
    }

    private static boolean isActiveRegistration(CiGstRegistration reg) {
        if (reg == null || reg.getRegistrationStatus() == null) {
            return true;
        }
        String s = reg.getRegistrationStatus().toUpperCase();
        return !"INACTIVE".equals(s) && !"CANCELLED".equals(s) && !"SUSPENDED".equals(s);
    }

    private CreditIntelligenceProperties.Canonicalization.Gst gstCfg() {
        CreditIntelligenceProperties.Canonicalization.Gst g =
                properties.getCanonicalization().getGst();
        return g != null ? g : new CreditIntelligenceProperties.Canonicalization.Gst();
    }

    private CiMetricResult persist(CiMetricResult r) {
        return metricResultRepository.save(r);
    }

    private static CiMetricResult result(
            UUID tenantId, UUID applicationId, UUID sourceRecordId, String code,
            String outcome, Map<String, Object> value, String quality,
            List<Object> included, List<Object> excluded, Map<String, Object> evidence) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("domain", "GST");
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

    private static Map<String, Object> baseEvidence(UUID sourceRecordId, List<PeriodBundle> bundles, LocalDate asOf) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("metricVersion", METRIC_VERSION);
        if (sourceRecordId != null) {
            e.put("sourceRecordId", sourceRecordId.toString());
        }
        e.put("registrationCount", bundles != null ? bundles.size() : 0);
        if (bundles != null && !bundles.isEmpty()) {
            YearMonth latest = null;
            for (PeriodBundle b : bundles) {
                for (CiGstReturnPeriod p : safePeriods(b)) {
                    if (!GstFilingStatusNormalizer.isFiledLike(safeStatus(p))) {
                        continue;
                    }
                    YearMonth ym = GstPeriodUtils.parseToYearMonth(p.getPeriodYyyyMm()).orElse(null);
                    if (ym != null && (latest == null || ym.isAfter(latest))) {
                        latest = ym;
                    }
                }
            }
            if (latest != null) {
                LocalDate asOfDate = asOf != null ? asOf : LocalDate.now();
                var freshness = GstFreshnessEvaluator.evaluate(asOfDate, latest,
                        // default lag; caller config applied elsewhere
                        20);
                e.put("freshness", freshness.evidence());
            }
        }
        return e;
    }

    private static GstFilingStatus safeStatus(CiGstReturnPeriod p) {
        try {
            return GstFilingStatus.valueOf(p.getFilingStatus());
        } catch (Exception e) {
            return GstFilingStatus.UNKNOWN;
        }
    }

    private static List<PeriodBundle> safe(List<PeriodBundle> bundles) {
        return bundles != null ? bundles : List.of();
    }

    private static List<CiGstReturnPeriod> safePeriods(PeriodBundle b) {
        return b != null && b.periods() != null ? b.periods() : List.of();
    }

    private enum FilingAgg { ON_TIME, LATE, MISSING }

    /** amount may be null when unknown; zeroKnown true means period has explicit 0 turnover. */
    private record PeriodAmount(BigDecimal amount, boolean zeroKnown) {
    }
}
