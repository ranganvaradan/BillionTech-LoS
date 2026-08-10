package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.PeriodAlignmentStrategy;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PERIOD_ALIGNMENT_V1 — records requested/actual/overlap/coverage; marks incomparable periods.
 */
@Component
public class PeriodAlignmentService {

    public record PeriodWindow(LocalDate from, LocalDate to) {
        public boolean isValid() {
            return from != null && to != null && !to.isBefore(from);
        }

        public long monthsInclusive() {
            if (!isValid()) {
                return 0;
            }
            return ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
        }
    }

    public record AlignmentResult(
            boolean comparable,
            PeriodAlignmentStrategy strategy,
            String methodVersion,
            PeriodWindow requested,
            PeriodWindow actualLeft,
            PeriodWindow actualRight,
            PeriodWindow overlap,
            BigDecimal coverage,
            List<String> monthsCompared,
            List<String> excludedMonths,
            Map<String, Object> trace) {
    }

    public AlignmentResult align(
            PeriodAlignmentStrategy strategy,
            PeriodWindow left,
            PeriodWindow right,
            PeriodWindow requested) {

        PeriodAlignmentStrategy strat = strategy != null ? strategy : PeriodAlignmentStrategy.COMMON_OVERLAP;
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("method", ReconciliationConstants.PERIOD_ALIGNMENT_V1);
        trace.put("strategy", strat.name());

        if (left == null || right == null || !left.isValid() || !right.isValid()) {
            return incomparable(strat, requested, left, right, "MISSING_PERIOD_BOUNDS", trace);
        }

        return switch (strat) {
            case EXACT_PERIOD -> alignExact(requested, left, right, strat, trace);
            case FINANCIAL_YEAR -> alignFinancialYear(requested, left, right, strat, trace);
            case TRAILING_12_MONTHS -> alignTrailing(requested, left, right, strat, 12, trace);
            case YTD_COMPARABLE -> alignYtd(requested, left, right, strat, trace);
            case LATEST_COMPLETE_COMMON_PERIOD -> alignLatestCommon(requested, left, right, strat, trace);
            case COMMON_OVERLAP -> alignOverlap(requested, left, right, strat, trace);
        };
    }

    private AlignmentResult alignExact(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, Map<String, Object> trace) {
        boolean same = left.from().equals(right.from()) && left.to().equals(right.to());
        if (!same) {
            return incomparable(strat, requested, left, right, "EXACT_PERIOD_MISMATCH", trace);
        }
        return comparable(strat, requested, left, right, left, BigDecimal.ONE, monthsBetween(left), List.of(), trace);
    }

    private AlignmentResult alignFinancialYear(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, Map<String, Object> trace) {
        // Allow FY windows that share the same FY end year (Apr–Mar India FY heuristic)
        int leftFyEnd = fiscalYearEndYear(left.to());
        int rightFyEnd = fiscalYearEndYear(right.to());
        if (leftFyEnd != rightFyEnd) {
            return incomparable(strat, requested, left, right, "FINANCIAL_YEAR_MISMATCH", trace);
        }
        PeriodWindow overlap = overlapOf(left, right);
        if (overlap == null) {
            return incomparable(strat, requested, left, right, "NO_FY_OVERLAP", trace);
        }
        BigDecimal coverage = coverageRatio(left, right, overlap);
        return comparable(strat, requested, left, right, overlap, coverage, monthsBetween(overlap), List.of(), trace);
    }

    private AlignmentResult alignTrailing(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, int months, Map<String, Object> trace) {
        PeriodWindow overlap = overlapOf(left, right);
        if (overlap == null || overlap.monthsInclusive() < Math.min(6, months / 2)) {
            return incomparable(strat, requested, left, right, "INSUFFICIENT_TRAILING_OVERLAP", trace);
        }
        BigDecimal coverage = BigDecimal.valueOf(overlap.monthsInclusive())
                .divide(BigDecimal.valueOf(months), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        List<String> excluded = new ArrayList<>();
        if (coverage.compareTo(BigDecimal.ONE) < 0) {
            excluded.add("PARTIAL_TRAILING_COVERAGE");
        }
        return comparable(strat, requested, left, right, overlap, coverage, monthsBetween(overlap), excluded, trace);
    }

    private AlignmentResult alignYtd(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, Map<String, Object> trace) {
        PeriodWindow overlap = overlapOf(left, right);
        if (overlap == null) {
            return incomparable(strat, requested, left, right, "NO_YTD_OVERLAP", trace);
        }
        BigDecimal coverage = coverageRatio(left, right, overlap);
        return comparable(strat, requested, left, right, overlap, coverage, monthsBetween(overlap), List.of(), trace);
    }

    private AlignmentResult alignLatestCommon(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, Map<String, Object> trace) {
        PeriodWindow overlap = overlapOf(left, right);
        if (overlap == null) {
            return incomparable(strat, requested, left, right, "NO_COMMON_PERIOD", trace);
        }
        // Shrink to latest complete month within overlap
        LocalDate to = overlap.to().withDayOfMonth(1).minusDays(1);
        if (to.isBefore(overlap.from())) {
            to = overlap.to();
        }
        PeriodWindow latest = new PeriodWindow(overlap.from(), to.isBefore(overlap.from()) ? overlap.to() : overlap.to());
        BigDecimal coverage = coverageRatio(left, right, latest);
        return comparable(strat, requested, left, right, latest, coverage, monthsBetween(latest), List.of(), trace);
    }

    private AlignmentResult alignOverlap(
            PeriodWindow requested, PeriodWindow left, PeriodWindow right,
            PeriodAlignmentStrategy strat, Map<String, Object> trace) {
        PeriodWindow overlap = overlapOf(left, right);
        if (overlap == null || overlap.monthsInclusive() < 1) {
            return incomparable(strat, requested, left, right, "NO_COMMON_OVERLAP", trace);
        }
        BigDecimal coverage = coverageRatio(left, right, overlap);
        List<String> excluded = new ArrayList<>();
        if (left.monthsInclusive() > overlap.monthsInclusive()) {
            excluded.add("LEFT_MONTHS_OUTSIDE_OVERLAP");
        }
        if (right.monthsInclusive() > overlap.monthsInclusive()) {
            excluded.add("RIGHT_MONTHS_OUTSIDE_OVERLAP");
        }
        // Require at least 50% coverage of the shorter window to be comparable
        long shorter = Math.min(left.monthsInclusive(), right.monthsInclusive());
        if (shorter > 0 && overlap.monthsInclusive() * 2 < shorter) {
            return incomparable(strat, requested, left, right, "INSUFFICIENT_OVERLAP_COVERAGE", trace);
        }
        return comparable(strat, requested, left, right, overlap, coverage, monthsBetween(overlap), excluded, trace);
    }

    private static AlignmentResult comparable(
            PeriodAlignmentStrategy strat,
            PeriodWindow requested,
            PeriodWindow left,
            PeriodWindow right,
            PeriodWindow overlap,
            BigDecimal coverage,
            List<String> months,
            List<String> excluded,
            Map<String, Object> trace) {
        trace.put("comparable", true);
        return new AlignmentResult(
                true, strat, ReconciliationConstants.PERIOD_ALIGNMENT_V1,
                requested, left, right, overlap, coverage, months, excluded, trace);
    }

    private static AlignmentResult incomparable(
            PeriodAlignmentStrategy strat,
            PeriodWindow requested,
            PeriodWindow left,
            PeriodWindow right,
            String reason,
            Map<String, Object> trace) {
        trace.put("comparable", false);
        trace.put("reason", reason);
        return new AlignmentResult(
                false, strat, ReconciliationConstants.PERIOD_ALIGNMENT_V1,
                requested, left, right, null, BigDecimal.ZERO, List.of(), List.of(reason), trace);
    }

    static PeriodWindow overlapOf(PeriodWindow a, PeriodWindow b) {
        if (a == null || b == null || !a.isValid() || !b.isValid()) {
            return null;
        }
        LocalDate from = a.from().isAfter(b.from()) ? a.from() : b.from();
        LocalDate to = a.to().isBefore(b.to()) ? a.to() : b.to();
        if (to.isBefore(from)) {
            return null;
        }
        return new PeriodWindow(from, to);
    }

    static BigDecimal coverageRatio(PeriodWindow left, PeriodWindow right, PeriodWindow overlap) {
        long denom = Math.max(Math.min(left.monthsInclusive(), right.monthsInclusive()), 1);
        return BigDecimal.valueOf(overlap.monthsInclusive())
                .divide(BigDecimal.valueOf(denom), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
    }

    static List<String> monthsBetween(PeriodWindow w) {
        List<String> out = new ArrayList<>();
        if (w == null || !w.isValid()) {
            return out;
        }
        YearMonth cursor = YearMonth.from(w.from());
        YearMonth end = YearMonth.from(w.to());
        while (!cursor.isAfter(end)) {
            out.add(cursor.toString());
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    /** Indian FY ends 31-Mar; year of FY end = calendar year if month>=4 else calendar year. */
    static int fiscalYearEndYear(LocalDate d) {
        if (d == null) {
            return 0;
        }
        return d.getMonthValue() >= 4 ? d.getYear() + 1 : d.getYear();
    }

    /** Default trailing-12 window ending at asOf (inclusive month). */
    public static PeriodWindow trailing12(LocalDate asOf) {
        LocalDate end = asOf != null ? asOf : LocalDate.now();
        LocalDate start = end.minusMonths(11).withDayOfMonth(1);
        return new PeriodWindow(start, end);
    }

    public static PeriodWindow financialYearEnding(int fyEndYear) {
        return new PeriodWindow(LocalDate.of(fyEndYear - 1, 4, 1), LocalDate.of(fyEndYear, 3, 31));
    }
}
