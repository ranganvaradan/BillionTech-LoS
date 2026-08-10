package com.los.core.creditintelligence.tax.util;

import com.los.core.creditintelligence.tax.domain.TaxConstants;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Policy {@link TaxConstants#ITR_FRESHNESS_POLICY_V1}: calendar-aware expectation
 * of the latest filed assessment year (not simplistic age-only).
 *
 * <p>Latest completed FY ends 31 Mar. Non-audit ITR due date treated as 31 Jul of the AY start year.
 * Before that due date (+ optional grace), the expected latest filed AY is the prior one.
 */
public final class ItrFreshnessEvaluator {

    public static final int DEFAULT_NON_AUDIT_DUE_MONTH = 7;
    public static final int DEFAULT_NON_AUDIT_DUE_DAY = 31;
    public static final int DEFAULT_GRACE_DAYS = 15;

    public record FreshnessResult(
            boolean fresh,
            String expectedLatestAy,
            String actualLatestAy,
            LocalDate expectedDueDate,
            String policyVersion,
            Map<String, Object> evidence) {
    }

    private ItrFreshnessEvaluator() {
    }

    public static FreshnessResult evaluate(LocalDate asOf, String actualLatestAy) {
        return evaluate(asOf, actualLatestAy, DEFAULT_GRACE_DAYS, false);
    }

    public static FreshnessResult evaluate(
            LocalDate asOf, String actualLatestAy, int graceDays, boolean auditApplicable) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("policy", TaxConstants.ITR_FRESHNESS_POLICY_V1);
        evidence.put("asOf", date.toString());
        evidence.put("graceDays", graceDays);
        evidence.put("auditApplicable", auditApplicable);

        String expectedAy = expectedLatestAssessmentYear(date, graceDays, auditApplicable);
        LocalDate due = dueDateForAy(expectedAy, auditApplicable);
        evidence.put("expectedLatestAy", expectedAy);
        evidence.put("actualLatestAy", actualLatestAy);
        evidence.put("expectedDueDate", due != null ? due.toString() : null);

        if (actualLatestAy == null || actualLatestAy.isBlank()) {
            evidence.put("reason", "NO_FILED_RETURN");
            return new FreshnessResult(false, expectedAy, null, due,
                    TaxConstants.ITR_FRESHNESS_POLICY_V1, evidence);
        }

        Optional<String> actualNorm = TaxYearUtils.normalizeYearLabel(actualLatestAy);
        Optional<String> expectedNorm = TaxYearUtils.normalizeYearLabel(expectedAy);
        if (actualNorm.isEmpty() || expectedNorm.isEmpty()) {
            evidence.put("reason", "UNPARSEABLE_AY");
            return new FreshnessResult(false, expectedAy, actualLatestAy, due,
                    TaxConstants.ITR_FRESHNESS_POLICY_V1, evidence);
        }

        boolean fresh = TaxYearUtils.compareYearLabels(actualNorm.get(), expectedNorm.get()) >= 0;
        evidence.put("reason", fresh ? "WITHIN_EXPECTED_AY" : "STALE_VS_EXPECTED_AY");
        return new FreshnessResult(fresh, expectedNorm.get(), actualNorm.get(), due,
                TaxConstants.ITR_FRESHNESS_POLICY_V1, evidence);
    }

    /**
     * Latest completed FY: if asOf is on/after 1 Apr of calendar year C, FY ending Mar C is complete
     * (label (C-1)-C). Its AY is C-(C+1). Before the AY due date (+ grace), expected filed AY is prior.
     */
    public static String expectedLatestAssessmentYear(LocalDate asOf, int graceDays, boolean audit) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        int fyEndCalendarYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        // FY ending Mar fyEndCalendarYear → AY starts fyEndCalendarYear
        String candidateAy = String.format("%d-%02d", fyEndCalendarYear, (fyEndCalendarYear + 1) % 100);
        LocalDate due = dueDateForAy(candidateAy, audit);
        if (due != null) {
            LocalDate gate = due.plusDays(Math.max(0, graceDays));
            if (date.isBefore(gate)) {
                return String.format("%d-%02d", fyEndCalendarYear - 1, fyEndCalendarYear % 100);
            }
        }
        return candidateAy;
    }

    public static LocalDate dueDateForAy(String assessmentYear, boolean audit) {
        Optional<String> norm = TaxYearUtils.normalizeYearLabel(assessmentYear);
        if (norm.isEmpty()) {
            return null;
        }
        int ayStart = Integer.parseInt(norm.get().substring(0, 4));
        if (audit) {
            // Approximate audit due: 31 Oct of AY start year
            return LocalDate.of(ayStart, 10, 31);
        }
        return LocalDate.of(ayStart, DEFAULT_NON_AUDIT_DUE_MONTH, DEFAULT_NON_AUDIT_DUE_DAY);
    }
}
