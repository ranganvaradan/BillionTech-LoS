package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.domain.LiveAccountDefinition;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Computes and persists deterministic bureau metrics with evidence.
 */
@Service
@RequiredArgsConstructor
public class BureauMetricService {

    public static final String METRIC_VERSION = "V1";
    public static final String LIVE_UNSECURED = "bureau.live_unsecured_loan_count";
    public static final String TOTAL_LIVE_EXPOSURE = "bureau.total_live_exposure";
    public static final String SECURED_LIVE_EXPOSURE = "bureau.secured_live_exposure";
    public static final String UNSECURED_LIVE_EXPOSURE = "bureau.unsecured_live_exposure";
    public static final String TOTAL_MONTHLY_OBLIGATION = "bureau.total_monthly_obligation";
    public static final String MAX_DPD_6M = "bureau.max_dpd_6m";
    public static final String MAX_DPD_12M = "bureau.max_dpd_12m";
    public static final String MAX_DPD_24M = "bureau.max_dpd_24m";
    public static final String RECENT_INQUIRIES_90D = "bureau.recent_inquiries_90d";
    public static final String SETTLED_ACCOUNT_COUNT = "bureau.settled_account_count";
    public static final String WRITTEN_OFF_ACCOUNT_COUNT = "bureau.written_off_account_count";
    /** Non-credit-card write-off count — shared Policy Test + live UW authority. */
    public static final String WRITEOFF_NON_CC = "bureau.accounts.writeoff_non_cc";
    /** Credit-card write-off count — sibling of {@link #WRITEOFF_NON_CC}. */
    public static final String WRITEOFF_CC = "bureau.accounts.cc_writeoff";
    /** Canonical NTC / no-hit flag (0/1). Distinct from bureau.score sentinel -1. */
    public static final String STATUS_NTC = "bureau.status_ntc";

    public static final String INQUIRIES_CURRENT_MONTH = "bureau.inquiries.current_month";
    public static final String INQUIRIES_LAST_3M = "bureau.inquiries.last_3m";
    public static final String DPD_30_PLUS_COUNT_6M = "bureau.dpd_30_plus_count_6m";
    public static final String DPD_60_PLUS_COUNT_6M = "bureau.dpd_60_plus_count_6m";
    public static final String DPD_90_PLUS_COUNT_6M = "bureau.dpd_90_plus_count_6m";
    public static final String MONTHS_SINCE_LAST_DELINQUENCY = "bureau.months_since_last_delinquency";
    public static final String OLDEST_TRADELINE_VINTAGE_MONTHS = "bureau.oldest_tradeline_vintage_months";
    public static final String AVERAGE_ACCOUNT_AGE_MONTHS = "bureau.average_account_age_months";
    /** MAX overdue on CREDIT_CARD tradelines. */
    public static final String CC_OVERDUE_AMOUNT = "bureau.cc_overdue_amount";
    /** SUM(bal)/SUM(limit) open LIVE CC only; DI if any missing limit or sum limit=0. */
    public static final String CC_UTILISATION = "bureau.cc_utilisation";
    /** SUM non-CC overdue&gt;0 (explanatory; not BRE exception). */
    public static final String OVERDUE_AMOUNT = "bureau.overdue.amount";
    /** MAX age months of non-CC overdue using overdue EVENT month from PH. */
    public static final String OVERDUE_AGE_MONTHS = "bureau.overdue.age_months";
    public static final String CREDIT_AFTER_OVERDUE_EXISTS = "bureau.credit_after_overdue.exists";
    /** Max consecutive CLEAN months on later loans — explanatory helper, not account-safe for BRE. */
    public static final String CREDIT_AFTER_OVERDUE_CLEAN_HISTORY_MONTHS =
            "bureau.credit_after_overdue.clean_history_months";
    public static final String NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT =
            "bureau.non_cc_overdue_exception_violation_count";
    public static final String SUIT_FILED_ACCOUNT_COUNT = "bureau.suit_filed_account_count";
    public static final String PAN_DISTINCT_COUNT = "bureau.pan_distinct_count";
    public static final String RESTRUCTURED_ACCOUNT_COUNT = "bureau.restructured_account_count";
    public static final String DBT_ACCOUNT_COUNT = "bureau.dbt_account_count";
    public static final String PWOS_ACCOUNT_COUNT = "bureau.pwos_account_count";
    public static final String LSS_ACCOUNT_COUNT = "bureau.lss_account_count";
    /*
     * NOT emitted (Equifax saved samples do not prove the provider status):
     * bureau.account_sold_count, bureau.thin_file_indicator.
     */

    public static final String WRITEOFF_CALCULATOR = "BureauMetricService.computeWriteoffCounts";
    /** Shared calculator id for max DPD windows (6/12/24). */
    public static final String MAX_DPD_CALCULATOR = "BureauMetricService.evaluateMaxDpd";
    public static final String MAX_DPD_CALCULATOR_VERSION = "V1_YEARMONTH_TRAILING";

    private final CiMetricResultRepository metricResultRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final BureauStatusNormalizer statusNormalizer = new BureauStatusNormalizer();

    /**
     * One payment-history month for shared max-DPD evaluation.
     * {@code period} is the provider reporting month (Equifax History48Months → YearMonth; stored as day-1 LocalDate).
     */
    public record PaymentHistoryMonthInput(
            String tradelineRef,
            YearMonth period,
            Integer dpd,
            String providerRawStatus,
            String assetClassificationStatus,
            String suitFiledStatus) {
        public PaymentHistoryMonthInput(String tradelineRef, YearMonth period, Integer dpd) {
            this(tradelineRef, period, dpd, null, null, null);
        }

        public static PaymentHistoryMonthInput of(String ref, LocalDate monthDate, Integer dpd) {
            return of(ref, monthDate, dpd, null, null, null);
        }

        public static PaymentHistoryMonthInput of(
                String ref,
                LocalDate monthDate,
                Integer dpd,
                String providerRawStatus,
                String assetClassificationStatus,
                String suitFiledStatus) {
            if (monthDate == null) {
                return new PaymentHistoryMonthInput(
                        ref, null, dpd, providerRawStatus, assetClassificationStatus, suitFiledStatus);
            }
            return new PaymentHistoryMonthInput(
                    ref, YearMonth.from(monthDate), dpd,
                    providerRawStatus, assetClassificationStatus, suitFiledStatus);
        }
    }

    private static Map<String, Object> phRow(String ref, YearMonth period, Integer dpd, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", ref != null ? ref : "");
        if (period != null) {
            m.put("period", period.toString());
        }
        if (dpd != null) {
            m.put("dpd", dpd);
        }
        if (reason != null) {
            m.put("reason", reason);
        }
        return m;
    }

    public record CcOverdueInput(boolean creditCard, BigDecimal overdueAmount, boolean duplicate) {
        public CcOverdueInput(boolean creditCard, BigDecimal overdueAmount) {
            this(creditCard, overdueAmount, false);
        }
    }

    /**
     * Shared scalar result used by live persist and studio PolicyBureauMetricService delegates.
     */
    public record ScalarEvaluation(
            String outcome,
            Object value,
            String quality,
            Map<String, Object> evidence,
            List<Object> included,
            List<Object> excluded,
            Map<String, Object> valueMap) {

        public Map<String, Object> toStudioMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("outcome", outcome);
            m.put("v", value);
            m.put("dataQualityStatus", quality);
            if (evidence != null) {
                Object reason = evidence.get("reason");
                if (reason != null && BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(outcome)) {
                    m.put("reason", reason);
                }
                if (evidence.get("asOf") != null) {
                    m.put("asOf", evidence.get("asOf"));
                }
                if (evidence.get("windowStart") != null) {
                    m.put("windowStart", evidence.get("windowStart"));
                }
            }
            m.put("evidence", evidence);
            return m;
        }
    }

    /**
     * Shared max-DPD result used by live {@link #computeMaxDpd} and studio PolicyBureauMetricService.
     *
     * <p><b>Business definition (trailing {@code windowMonths} calendar months):</b>
     * <ul>
     *   <li>asOf = evaluation / bureau report date</li>
     *   <li>period = YearMonth of the payment-history entry (provider month key → first-of-month LocalDate → YearMonth)</li>
     *   <li>window = inclusive [{@code YearMonth(asOf) − (windowMonths−1)}, {@code YearMonth(asOf)}]</li>
     *   <li>as-of month is included; periods after as-of month are excluded (no future influence)</li>
     *   <li>exact lower-bound month is included</li>
     *   <li>null/malformed period skipped; null DPD skipped (not treated as zero)</li>
     *   <li>MAX across all tradelines' included months; closed/CC not filtered when history exists</li>
     *   <li>no payment-history rows at all → DATA_INSUFFICIENT (not zero); history present but none in window → PASS 0</li>
     * </ul>
     */
    public record MaxDpdEvaluation(
            String outcome,
            Integer maxDpd,
            String quality,
            YearMonth asOfMonth,
            YearMonth earliestMonth,
            int windowMonths,
            List<Object> included,
            List<Object> excluded,
            Map<String, Object> evidence) {

        public Map<String, Object> toStudioMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(outcome)) {
                m.put("outcome", "DATA_INSUFFICIENT");
                m.put("v", null);
                m.put("quality", quality);
            } else {
                m.put("outcome", "PASS");
                m.put("v", maxDpd != null ? maxDpd : 0);
                m.put("quality", quality);
            }
            m.put("asOfMonth", asOfMonth != null ? asOfMonth.toString() : null);
            m.put("earliestMonth", earliestMonth != null ? earliestMonth.toString() : null);
            m.put("windowMonths", windowMonths);
            m.put("included", included);
            m.put("excluded", excluded);
            m.put("evidence", evidence);
            return m;
        }
    }

    /**
     * Canonical max-DPD calculator — single authority for studio + live.
     */
    public MaxDpdEvaluation evaluateMaxDpd(
            List<PaymentHistoryMonthInput> rows,
            LocalDate asOf,
            int windowMonths) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        if (windowMonths < 1) {
            throw new IllegalArgumentException("windowMonths must be >= 1");
        }
        YearMonth asOfYm = YearMonth.from(effectiveAsOf);
        YearMonth earliest = asOfYm.minusMonths(windowMonths - 1L);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("calculator", MAX_DPD_CALCULATOR);
        evidence.put("calculatorVersion", MAX_DPD_CALCULATOR_VERSION);
        evidence.put("asOf", effectiveAsOf.toString());
        evidence.put("asOfMonth", asOfYm.toString());
        evidence.put("earliestMonth", earliest.toString());
        evidence.put("windowMonths", windowMonths);
        evidence.put("periodRepresentation", "YearMonth");
        evidence.put("windowSemantics", "INCLUSIVE_YEARMONTH_TRAILING_INCLUDING_ASOF_MONTH");

        if (rows == null || rows.isEmpty()) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return new MaxDpdEvaluation(
                    BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null,
                    "DATA_INSUFFICIENT",
                    asOfYm,
                    earliest,
                    windowMonths,
                    List.of(),
                    List.of(),
                    evidence);
        }

        Integer max = null;
        boolean anyValidPeriod = false;
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        for (PaymentHistoryMonthInput row : rows) {
            if (row == null) {
                continue;
            }
            String ref = row.tradelineRef() != null ? row.tradelineRef() : "";
            if (row.period() == null) {
                excluded.add(Map.of("ref", ref, "reason", "MALFORMED_OR_MISSING_PERIOD"));
                continue;
            }
            anyValidPeriod = true;
            YearMonth period = row.period();
            if (period.isBefore(earliest)) {
                excluded.add(phRow(ref, period, row.dpd(), "BEFORE_WINDOW"));
                continue;
            }
            if (period.isAfter(asOfYm)) {
                excluded.add(phRow(ref, period, row.dpd(), "AFTER_ASOF_MONTH"));
                continue;
            }
            if (row.dpd() == null) {
                excluded.add(phRow(ref, period, null, "DPD_MISSING"));
                continue;
            }
            if (max == null || row.dpd() > max) {
                max = row.dpd();
            }
            included.add(phRow(ref, period, row.dpd(), null));
        }

        if (!anyValidPeriod) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return new MaxDpdEvaluation(
                    BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null,
                    "DATA_INSUFFICIENT",
                    asOfYm,
                    earliest,
                    windowMonths,
                    included,
                    excluded,
                    evidence);
        }

        evidence.put("maxDpd", max != null ? max : 0);
        evidence.put("includedCount", included.size());
        evidence.put("excludedCount", excluded.size());
        return new MaxDpdEvaluation(
                BureauMetricOutcome.PASS.name(),
                max != null ? max : 0,
                "OK",
                asOfYm,
                earliest,
                windowMonths,
                included,
                excluded,
                evidence);
    }

    /**
     * Package-visible CLEAN_MONTH predicate (numeric dpd==0 and non-adverse monthly status).
     */
    static boolean isCleanMonth(Integer dpd, String providerRawStatus, String assetClassificationStatus) {
        return BureauCleanMonth.isCleanMonth(dpd, providerRawStatus, assetClassificationStatus);
    }

    public ScalarEvaluation evaluateInquiriesCurrentMonth(List<LocalDate> inquiryDates, LocalDate asOf) {
        return BureauDerivedMetricCalculator.inquiriesCurrentMonth(inquiryDates, asOf);
    }

    public ScalarEvaluation evaluateInquiriesLast3Months(List<LocalDate> inquiryDates, LocalDate asOf) {
        return BureauDerivedMetricCalculator.inquiriesLast3Months(inquiryDates, asOf);
    }

    public ScalarEvaluation evaluateDpdPlusCount(
            List<PaymentHistoryMonthInput> rows, LocalDate asOf, int windowMonths, int threshold) {
        return BureauDerivedMetricCalculator.dpdPlusCount(rows, asOf, windowMonths, threshold);
    }

    public ScalarEvaluation evaluateMonthsSinceLastDelinquency(
            List<PaymentHistoryMonthInput> rows, LocalDate asOf) {
        return BureauDerivedMetricCalculator.monthsSinceLastDelinquency(rows, asOf);
    }

    public ScalarEvaluation evaluateCcOverdueAmount(List<CcOverdueInput> inputs) {
        return BureauDerivedMetricCalculator.ccOverdueAmount(inputs);
    }

    public ScalarEvaluation evaluateCcUtilisation(List<CiBureauTradeline> tradelines) {
        return BureauDerivedMetricCalculator.ccUtilisation(tradelines);
    }

    public ScalarEvaluation evaluatePanDistinctCount(Map<String, Object> reportData) {
        return BureauDerivedMetricCalculator.panDistinctCount(reportData);
    }

    public ScalarEvaluation evaluateEquifaxAdverseAccountCount(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            EquifaxRetailPaymentStatusVocabulary.Family family) {
        return BureauDerivedMetricCalculator.equifaxAdverseAccountCount(tradelines, history, family);
    }

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<String, Object> reportData) {
        List<CiMetricResult> results = new ArrayList<>();
        Map<UUID, List<PaymentHistoryMonthInput>> history = loadHistoryInputs(tradelines);
        results.add(persist(computeStatusNtc(report, reportData)));
        results.add(persist(computeLiveUnsecured(report, tradelines)));
        results.add(persist(computeExposure(report, tradelines, TOTAL_LIVE_EXPOSURE, null)));
        results.add(persist(computeExposure(report, tradelines, SECURED_LIVE_EXPOSURE, true)));
        results.add(persist(computeExposure(report, tradelines, UNSECURED_LIVE_EXPOSURE, false)));
        results.add(persist(computeMonthlyObligation(report, tradelines)));
        results.add(persist(computeMaxDpd(report, tradelines, 6, MAX_DPD_6M, reportData, history)));
        results.add(persist(computeMaxDpd(report, tradelines, 12, MAX_DPD_12M, reportData, history)));
        results.add(persist(computeMaxDpd(report, tradelines, 24, MAX_DPD_24M, reportData, history)));
        results.add(persist(computeInquiries90d(report, reportData)));
        results.add(persist(computeInquiriesCurrentMonth(report, reportData)));
        results.add(persist(computeInquiriesLast3Months(report, reportData)));
        results.add(persist(computeStatusCount(report, tradelines, SETTLED_ACCOUNT_COUNT, true, false)));
        results.add(persist(computeStatusCount(report, tradelines, WRITTEN_OFF_ACCOUNT_COUNT, false, true)));
        results.addAll(persistWriteoffPair(report, computeWriteoffCounts(report, tradelines, reportData)));
        results.add(persist(computeDpdPlusCount(report, tradelines, history, 30, DPD_30_PLUS_COUNT_6M)));
        results.add(persist(computeDpdPlusCount(report, tradelines, history, 60, DPD_60_PLUS_COUNT_6M)));
        results.add(persist(computeDpdPlusCount(report, tradelines, history, 90, DPD_90_PLUS_COUNT_6M)));
        results.add(persist(computeMonthsSinceLastDelinquency(report, tradelines, history, reportData)));
        results.add(persist(computeVintage(report, tradelines, reportData, true, OLDEST_TRADELINE_VINTAGE_MONTHS)));
        results.add(persist(computeVintage(report, tradelines, reportData, false, AVERAGE_ACCOUNT_AGE_MONTHS)));
        results.add(persist(computeCcOverdueAmount(report, tradelines)));
        results.add(persist(computeCcUtilisation(report, tradelines)));
        results.add(persist(computeOverdueAmount(report, tradelines)));
        results.add(persist(computeOverdueAgeMonths(report, tradelines, history, reportData)));
        results.add(persist(computeCreditAfterOverdueExists(report, tradelines, history, reportData)));
        results.add(persist(computeCreditAfterOverdueCleanHistory(report, tradelines, history, reportData)));
        results.add(persist(computeNonCcOverdueExceptionViolations(report, tradelines, history, reportData)));
        results.add(persist(computeSuitFiledAccountCount(report, tradelines, history)));
        results.add(persist(computeEquifaxAdverseAccountCount(
                report, tradelines, history, EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED)));
        results.add(persist(computeEquifaxAdverseAccountCount(
                report, tradelines, history, EquifaxRetailPaymentStatusVocabulary.Family.DBT)));
        results.add(persist(computeEquifaxAdverseAccountCount(
                report, tradelines, history, EquifaxRetailPaymentStatusVocabulary.Family.PWOS)));
        results.add(persist(computeEquifaxAdverseAccountCount(
                report, tradelines, history, EquifaxRetailPaymentStatusVocabulary.Family.LOSS)));
        results.add(persist(computePanDistinctCount(report, reportData)));
        return results;
    }

    public enum WriteOffSignal { YES, NO, UNKNOWN }
    public enum CcClass { CREDIT_CARD, NON_CREDIT_CARD, UNKNOWN }

    /**
     * Normalised account view for the shared write-off calculator (live tradeline or studio fixture).
     */
    public record WriteoffAccountInput(
            String ref,
            String accountStatusRaw,
            BigDecimal writeOffAmount,
            boolean writtenOffFlag,
            String productCategory,
            Boolean creditCardExplicit) {

        public static WriteoffAccountInput fromTradeline(CiBureauTradeline t) {
            return new WriteoffAccountInput(
                    refOf(t),
                    t.getAccountStatus(),
                    t.getWrittenOffAmount(),
                    t.isWrittenOff(),
                    t.getProductCategory(),
                    null);
        }

        /** Studio fixture: explicit CC flag; null productCategory / null creditCard when unknown. */
        public static WriteoffAccountInput fromStudio(
                String ref,
                String statusRaw,
                BigDecimal writeOffAmount,
                String productCategory,
                Boolean creditCard) {
            boolean flag = writeOffAmount != null && writeOffAmount.compareTo(BigDecimal.ZERO) > 0;
            return new WriteoffAccountInput(ref, statusRaw, writeOffAmount, flag, productCategory, creditCard);
        }
    }

    public record WriteoffCountResult(
            String outcome,
            Integer nonCcCount,
            Integer ccCount,
            String dataQualityStatus,
            String reason,
            List<Object> included,
            List<Object> excluded,
            int unknownCount,
            Map<String, Object> evidence) {

        static WriteoffCountResult pass(
                int nonCc, int cc, List<Object> included, List<Object> excluded, int unknown,
                Map<String, Object> evidence) {
            return new WriteoffCountResult(
                    BureauMetricOutcome.PASS.name(), nonCc, cc, "OK", "COUNTED",
                    included, excluded, unknown, evidence);
        }

        static WriteoffCountResult insufficient(String reason, Map<String, Object> evidence) {
            Map<String, Object> e = evidence != null ? new LinkedHashMap<>(evidence) : new LinkedHashMap<>();
            e.put("reason", reason);
            return new WriteoffCountResult(
                    BureauMetricOutcome.DATA_INSUFFICIENT.name(), null, null,
                    "DATA_INSUFFICIENT", reason, List.of(), List.of(), 0, e);
        }

        WriteoffCountResult withRefs(List<Object> included, List<Object> excluded, int unknown) {
            return new WriteoffCountResult(
                    outcome, nonCcCount, ccCount, dataQualityStatus, reason,
                    included, excluded, unknown, evidence);
        }

        /** Studio / Policy Test map shape (outcome, v, dataQualityStatus, evidence). */
        public Map<String, Object> toStudioMap(String canonicalId, Integer count) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (BureauMetricOutcome.PASS.name().equals(outcome)) {
                m.put("outcome", "PASS");
                m.put("v", count);
                m.put("dataQualityStatus", "OK");
            } else {
                m.put("outcome", "DATA_INSUFFICIENT");
                m.put("v", null);
                m.put("dataQualityStatus", "DATA_INSUFFICIENT");
                m.put("reason", reason);
            }
            m.put("canonicalParameterId", canonicalId);
            m.put("calculator", WRITEOFF_CALCULATOR);
            m.put("evidence", evidence);
            m.put("includedReferences", included);
            m.put("excludedReferences", excluded);
            m.put("unknownCount", unknownCount);
            return m;
        }
    }

    /**
     * Shared write-off split (non-CC / CC). Single business calculation for Policy Test and live UW.
     * Distinguishes REAL ZERO from UNKNOWN/MISSING; unknown account type on a write-off fails closed.
     */
    public WriteoffCountResult computeWriteoffCounts(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<String, Object> reportData) {
        Map<String, Object> evidence = baseEvidence(report);
        evidence.put("calculator", WRITEOFF_CALCULATOR);
        evidence.put("metricVersion", METRIC_VERSION);
        evidence.put("asOf", java.time.Instant.now().toString());

        boolean noRecord = reportData != null && Boolean.TRUE.equals(reportData.get("noRecordFound"));
        Integer score = report != null ? report.getScore() : null;
        boolean ntc = noRecord
                || (reportData != null && Boolean.TRUE.equals(reportData.get("statusNtc")))
                || (score != null && score == -1 && noRecord);

        if (report == null) {
            return WriteoffCountResult.insufficient("REPORT_MISSING", evidence);
        }
        if (ntc && (!report.isTradelinesPresent()
                || "EMPTY".equalsIgnoreCase(nullToEmpty(report.getTradelineExtractionStatus()))
                || safe(tradelines).isEmpty())) {
            evidence.put("reason", "NTC_NO_HIT");
            evidence.put("noRecordFound", noRecord);
            evidence.put("score", score);
            // Do not invent a clean zero write-off book for NTC/no-hit.
            return WriteoffCountResult.insufficient("NTC_NO_HIT", evidence);
        }
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            evidence.put("reason", "TRADELINES_NOT_AVAILABLE");
            evidence.put("extractionStatus", report.getTradelineExtractionStatus());
            return WriteoffCountResult.insufficient("PROVIDER_OR_EXTRACTION_UNAVAILABLE", evidence);
        }
        String status = nullToEmpty(report.getTradelineExtractionStatus()).toUpperCase(Locale.ROOT);
        if ("EMPTY".equals(status)) {
            evidence.put("reason", "VALID_ZERO_EMPTY_TRADELINES");
            return WriteoffCountResult.pass(0, 0, List.of(), List.of(), 0, evidence);
        }
        if (tradelines == null || tradelines.isEmpty()) {
            evidence.put("reason", "TRADELINE_LIST_EMPTY_WITHOUT_EMPTY_STATUS");
            return WriteoffCountResult.insufficient("TRADELINES_MISSING", evidence);
        }

        List<WriteoffAccountInput> inputs = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t.getDuplicateOfTradelineId() != null) {
                continue;
            }
            inputs.add(WriteoffAccountInput.fromTradeline(t));
        }
        return evaluateWriteoffInputs(inputs, evidence);
    }

    /**
     * Pure shared evaluator used by live {@link #computeWriteoffCounts} and Policy Studio fixtures.
     */
    public WriteoffCountResult evaluateWriteoffInputs(
            List<WriteoffAccountInput> inputs,
            Map<String, Object> baseEvidence) {
        Map<String, Object> evidence = baseEvidence != null ? new LinkedHashMap<>(baseEvidence) : new LinkedHashMap<>();
        evidence.putIfAbsent("calculator", WRITEOFF_CALCULATOR);
        evidence.putIfAbsent("metricVersion", METRIC_VERSION);

        if (inputs == null) {
            return WriteoffCountResult.insufficient("NO_TRADELINES", evidence);
        }

        int nonCc = 0;
        int cc = 0;
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        int unknownCount = 0;
        List<String> blockers = new ArrayList<>();

        for (WriteoffAccountInput a : inputs) {
            WriteOffSignal wo = classifyWriteOff(a);
            CcClass ccClass = classifyCc(a);
            String ref = a.ref() != null ? a.ref() : "unknown";

            if (wo == WriteOffSignal.UNKNOWN) {
                unknownCount++;
                blockers.add("WRITEOFF_STATUS_UNKNOWN:" + ref);
                        excluded.add(evidenceRow(
                                "ref", ref,
                                "reason", "WRITEOFF_DATA_MISSING",
                                "accountStatus", a.accountStatusRaw(),
                                "writeOffAmount", a.writeOffAmount()));
                continue;
            }
            if (wo == WriteOffSignal.NO) {
                excluded.add(evidenceRow(
                        "ref", ref,
                        "reason", "NOT_WRITTEN_OFF",
                        "productCategory", a.productCategory(),
                        "ccClass", ccClass.name()));
                continue;
            }
            // Write-off YES
            if (ccClass == CcClass.UNKNOWN) {
                unknownCount++;
                blockers.add("ACCOUNT_TYPE_UNKNOWN_ON_WRITEOFF:" + ref);
                excluded.add(evidenceRow(
                        "ref", ref,
                        "reason", "UNKNOWN_ACCOUNT_TYPE_CANNOT_EXCLUDE_CC",
                        "productCategory", a.productCategory(),
                        "writeOff", "YES",
                        "accountStatus", a.accountStatusRaw(),
                        "writeOffAmount", a.writeOffAmount()));
                continue;
            }
            if (ccClass == CcClass.CREDIT_CARD) {
                cc++;
                excluded.add(evidenceRow(
                        "ref", ref,
                        "reason", "CREDIT_CARD_WRITEOFF_EXCLUDED_FROM_NON_CC",
                        "productCategory", a.productCategory(),
                        "writeOff", "YES",
                        "accountStatus", a.accountStatusRaw(),
                        "writeOffAmount", a.writeOffAmount()));
                included.add(evidenceRow(
                        "ref", ref,
                        "bucket", "CC_WRITEOFF",
                        "productCategory", a.productCategory(),
                        "writeOff", "YES",
                        "accountStatus", a.accountStatusRaw(),
                        "writeOffAmount", a.writeOffAmount()));
            } else {
                nonCc++;
                included.add(evidenceRow(
                        "ref", ref,
                        "bucket", "NON_CC_WRITEOFF",
                        "productCategory", a.productCategory(),
                        "writeOff", "YES",
                        "accountStatus", a.accountStatusRaw(),
                        "writeOffAmount", a.writeOffAmount()));
            }
        }

        evidence.put("nonCcCount", nonCc);
        evidence.put("ccCount", cc);
        evidence.put("blockers", blockers);
        if (!blockers.isEmpty()) {
            evidence.put("reason", "PARTIAL_OR_UNKNOWN_WRITEOFF_INPUTS");
            return WriteoffCountResult.insufficient("WRITEOFF_OR_ACCOUNT_TYPE_UNKNOWN", evidence)
                    .withRefs(included, excluded, unknownCount);
        }
        evidence.put("reason", "COUNTED");
        return WriteoffCountResult.pass(nonCc, cc, included, excluded, unknownCount, evidence);
    }

    private WriteOffSignal classifyWriteOff(WriteoffAccountInput a) {
        if (a == null) {
            return WriteOffSignal.UNKNOWN;
        }
        BigDecimal amt = a.writeOffAmount();
        if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
            return WriteOffSignal.YES;
        }
        if (a.writtenOffFlag()) {
            return WriteOffSignal.YES;
        }
        BureauStatusNormalizer.CanonicalStatus st = statusNormalizer.normalize(a.accountStatusRaw());
        if (st == BureauStatusNormalizer.CanonicalStatus.LSS
                || st == BureauStatusNormalizer.CanonicalStatus.PWOS) {
            return WriteOffSignal.YES;
        }
        // Explicit zero amount is positive "no write-off amount" evidence when status is not write-off.
        if (amt != null && amt.compareTo(BigDecimal.ZERO) == 0
                && st != BureauStatusNormalizer.CanonicalStatus.UNKNOWN) {
            return WriteOffSignal.NO;
        }
        if (a.accountStatusRaw() != null && !a.accountStatusRaw().isBlank()
                && st != BureauStatusNormalizer.CanonicalStatus.UNKNOWN) {
            // Known non-write-off status with no positive write-off signal.
            return WriteOffSignal.NO;
        }
        // Missing status and missing/null amount with writtenOff=false → unknown (do not invent zero).
        if ((a.accountStatusRaw() == null || a.accountStatusRaw().isBlank()) && amt == null) {
            return WriteOffSignal.UNKNOWN;
        }
        if (st == BureauStatusNormalizer.CanonicalStatus.UNKNOWN && amt == null && !a.writtenOffFlag()) {
            return WriteOffSignal.UNKNOWN;
        }
        return WriteOffSignal.NO;
    }

    private static CcClass classifyCc(WriteoffAccountInput a) {
        if (a == null) {
            return CcClass.UNKNOWN;
        }
        if (a.creditCardExplicit() != null) {
            return Boolean.TRUE.equals(a.creditCardExplicit()) ? CcClass.CREDIT_CARD : CcClass.NON_CREDIT_CARD;
        }
        String cat = a.productCategory() == null ? "" : a.productCategory().trim().toUpperCase(Locale.ROOT);
        if (cat.isEmpty() || BureauProductCategory.UNKNOWN.name().equals(cat)) {
            return CcClass.UNKNOWN;
        }
        if (BureauProductCategory.CREDIT_CARD.name().equals(cat)) {
            return CcClass.CREDIT_CARD;
        }
        return CcClass.NON_CREDIT_CARD;
    }

    private List<CiMetricResult> persistWriteoffPair(CiBureauReport report, WriteoffCountResult pair) {
        List<CiMetricResult> out = new ArrayList<>();
        out.add(persist(toWriteoffMetric(report, WRITEOFF_NON_CC, pair, pair.nonCcCount())));
        out.add(persist(toWriteoffMetric(report, WRITEOFF_CC, pair, pair.ccCount())));
        return out;
    }

    private static CiMetricResult toWriteoffMetric(
            CiBureauReport report, String code, WriteoffCountResult pair, Integer count) {
        Map<String, Object> evidence = pair.evidence() != null
                ? new LinkedHashMap<>(pair.evidence()) : baseEvidence(report);
        evidence.put("canonicalParameterId", code);
        evidence.put("calculator", WRITEOFF_CALCULATOR);
        return result(
                report,
                code,
                pair.outcome(),
                count == null ? null : valueOf(count),
                pair.dataQualityStatus(),
                pair.included() != null ? pair.included() : List.of(),
                pair.excluded() != null ? pair.excluded() : List.of(),
                pair.unknownCount(),
                evidence);
    }

    private static Map<String, Object> evidenceRow(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Canonical writer for {@code bureau.status_ntc}.
     * <p>
     * Equifax no-hit: {@code creditScore=-1} and/or {@code noRecordFound=true} → NTC true.
     * Positive score → NTC false. Does not invent NTC from missing/malformed score alone.
     * Single authority — studio {@code PolicyBureauMetricService.consumerNtc} delegates here.
     */
    public CiMetricResult computeStatusNtc(CiBureauReport report, Map<String, Object> reportData) {
        Boolean explicit = null;
        if (reportData != null && reportData.get("statusNtc") instanceof Boolean b) {
            explicit = b;
        }
        boolean noRecord = reportData != null && Boolean.TRUE.equals(reportData.get("noRecordFound"));
        StatusNtcEvaluation eval = evaluateStatusNtc(report.getScore(), explicit, noRecord, null);
        Map<String, Object> evidence = baseEvidence(report);
        evidence.putAll(eval.evidence());
        if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())) {
            return insufficient(report, STATUS_NTC, eval.quality());
        }
        return result(report, STATUS_NTC, eval.outcome(),
                valueOf(eval.value01()), eval.quality(), List.of(), List.of(), 0, evidence);
    }

    /**
     * Shared NTC decision — only implementation of bureau.status_ntc business rules.
     *
     * @param statusRaw optional provider status string; normalized NTC/NO_HIT counts as explicit evidence
     *                  (not an inference from score alone).
     */
    public StatusNtcEvaluation evaluateStatusNtc(
            Integer score,
            Boolean explicitStatusNtc,
            Boolean noRecordFound,
            String statusRaw) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("score", score);
        evidence.put("noRecordFound", Boolean.TRUE.equals(noRecordFound));

        Boolean explicit = explicitStatusNtc;
        if (explicit == null && statusRaw != null && !statusRaw.isBlank()) {
            BureauStatusNormalizer.CanonicalStatus st = statusNormalizer.normalize(statusRaw);
            if (st == BureauStatusNormalizer.CanonicalStatus.NTC) {
                explicit = true;
                evidence.put("statusRaw", statusRaw);
                evidence.put("source", "NORMALIZED_STATUS_NTC");
            } else if (st != BureauStatusNormalizer.CanonicalStatus.UNKNOWN) {
                explicit = false;
                evidence.put("statusRaw", statusRaw);
                evidence.put("source", "NORMALIZED_STATUS_NON_NTC");
            } else {
                evidence.put("statusRaw", statusRaw);
            }
        }

        if (explicit != null) {
            evidence.putIfAbsent("source", "EXPLICIT_STATUS_NTC");
            return new StatusNtcEvaluation(
                    BureauMetricOutcome.PASS.name(),
                    explicit ? 1 : 0,
                    "OK",
                    evidence);
        }
        if (Boolean.TRUE.equals(noRecordFound)) {
            evidence.put("source", "EQUIFAX_NO_RECORD");
            return new StatusNtcEvaluation(BureauMetricOutcome.PASS.name(), 1, "OK", evidence);
        }
        // Equifax pairs -1 with noRecordFound; if -1 alone without no-record flag, do not invent NTC
        if (score != null && score == -1) {
            evidence.put("source", "SCORE_SENTINEL_WITHOUT_NO_RECORD_FLAG");
            return new StatusNtcEvaluation(BureauMetricOutcome.PASS.name(), 0, "OK", evidence);
        }
        if (score != null && score > 0) {
            evidence.put("source", "POSITIVE_SCORE");
            return new StatusNtcEvaluation(BureauMetricOutcome.PASS.name(), 0, "OK", evidence);
        }
        evidence.put("reason", "SCORE_MISSING_OR_UNKNOWN");
        return new StatusNtcEvaluation(
                BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                null,
                "SCORE_MISSING_OR_UNKNOWN",
                evidence);
    }

    public record StatusNtcEvaluation(
            String outcome,
            Integer value01,
            String quality,
            Map<String, Object> evidence) {

        /** Studio Policy Test map shape (outcome / value / quality). */
        public Map<String, Object> toStudioMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(outcome)) {
                m.put("outcome", "DATA_INSUFFICIENT");
                m.put("value", null);
                m.put("quality", quality);
            } else {
                m.put("outcome", "PASS");
                m.put("value", value01 != null && value01 != 0);
                m.put("quality", quality);
            }
            if (evidence != null) {
                m.put("evidence", evidence);
            }
            return m;
        }
    }

    public List<CiMetricResult> findForReport(UUID reportId) {
        return metricResultRepository.findByBureauReportId(reportId);
    }

    public CiMetricResult computeLiveUnsecured(CiBureauReport report, List<CiBureauTradeline> tradelines) {
        String status = report.getTradelineExtractionStatus() != null
                ? report.getTradelineExtractionStatus().toUpperCase(Locale.ROOT) : "UNKNOWN";
        boolean extractionBad = "MISSING".equals(status) || "FAILED".equals(status) || "ABSENT".equals(status);

        Map<String, Object> evidence = baseEvidence(report);
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        int unknownCount = 0;

        if (!report.isTradelinesPresent() || extractionBad) {
            evidence.put("reason", "TRADELINES_NOT_AVAILABLE");
            evidence.put("extractionStatus", status);
            return result(report, LIVE_UNSECURED, BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, "DATA_INSUFFICIENT", included, excluded, unknownCount, evidence);
        }

        if (tradelines == null) {
            tradelines = List.of();
        }

        // Valid empty extraction → zero only when parser explicitly marked EMPTY
        // (distinguish genuine empty list from missing/failed extraction).
        if ("EMPTY".equals(status)) {
            evidence.put("reason", "VALID_ZERO_EMPTY_TRADELINES");
            return result(report, LIVE_UNSECURED, BureauMetricOutcome.PASS.name(),
                    valueOf(0), "OK", included, excluded, 0, evidence);
        }
        if (tradelines.isEmpty()) {
            evidence.put("reason", "TRADELINE_LIST_EMPTY_WITHOUT_EMPTY_STATUS");
            evidence.put("extractionStatus", status);
            return result(report, LIVE_UNSECURED, BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, "DATA_INSUFFICIENT", included, excluded, unknownCount, evidence);
        }

        int count = 0;
        for (CiBureauTradeline t : tradelines) {
            String ref = refOf(t);
            if (t.getDuplicateOfTradelineId() != null) {
                excluded.add(Map.of("ref", ref, "reason", "DUPLICATE"));
                continue;
            }
            if (!Boolean.TRUE.equals(t.getIsLive())) {
                excluded.add(Map.of("ref", ref, "reason", "NOT_LIVE"));
                continue;
            }
            if (BureauProductCategory.UNKNOWN.name().equals(t.getProductCategory())) {
                excluded.add(Map.of("ref", ref, "reason", "UNKNOWN_PRODUCT"));
                unknownCount++;
                continue;
            }
            if (t.getSecured() == null) {
                excluded.add(Map.of("ref", ref, "reason", "UNKNOWN_SECURITY"));
                unknownCount++;
                continue;
            }
            if (Boolean.TRUE.equals(t.getSecured())) {
                excluded.add(Map.of("ref", ref, "reason", "SECURED"));
                continue;
            }
            // secured == false
            included.add(Map.of("ref", ref, "reason", "LIVE_UNSECURED"));
            count++;
        }

        evidence.put("count", count);
        evidence.put("liveDefinition", LiveAccountDefinition.BUREAU_LIVE_ACCOUNT_DEFINITION_V1);
        evidence.put("taxonomyVersion", BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1);
        return result(report, LIVE_UNSECURED, BureauMetricOutcome.PASS.name(),
                valueOf(count), "OK", included, excluded, unknownCount, evidence);
    }

    private CiMetricResult computeExposure(
            CiBureauReport report, List<CiBureauTradeline> tradelines, String code, Boolean securedFilter) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        for (CiBureauTradeline t : safe(tradelines)) {
            String ref = refOf(t);
            if (t.getDuplicateOfTradelineId() != null || !Boolean.TRUE.equals(t.getIsLive())) {
                excluded.add(Map.of("ref", ref, "reason", "NOT_SELECTED"));
                continue;
            }
            if (securedFilter != null) {
                if (t.getSecured() == null || !securedFilter.equals(t.getSecured())) {
                    excluded.add(Map.of("ref", ref, "reason", "SECURITY_FILTER"));
                    continue;
                }
            }
            BigDecimal bal = t.getCurrentBalance() != null ? t.getCurrentBalance() : BigDecimal.ZERO;
            sum = sum.add(bal);
            included.add(Map.of("ref", ref, "balance", bal.toPlainString()));
        }
        return result(report, code, BureauMetricOutcome.PASS.name(),
                valueOf(sum), "OK", included, excluded, 0, baseEvidence(report));
    }

    private CiMetricResult computeMonthlyObligation(CiBureauReport report, List<CiBureauTradeline> tradelines) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, TOTAL_MONTHLY_OBLIGATION, "TRADELINES_NOT_AVAILABLE");
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        boolean partial = false;
        int missingEmi = 0;
        for (CiBureauTradeline t : safe(tradelines)) {
            String ref = refOf(t);
            if (t.getDuplicateOfTradelineId() != null || !Boolean.TRUE.equals(t.getIsLive())) {
                excluded.add(Map.of("ref", ref, "reason", "NOT_SELECTED"));
                continue;
            }
            if (t.getEmiAmount() == null) {
                missingEmi++;
                partial = true;
                excluded.add(Map.of("ref", ref, "reason", "EMI_MISSING"));
                continue;
            }
            sum = sum.add(t.getEmiAmount());
            included.add(Map.of("ref", ref, "emi", t.getEmiAmount().toPlainString()));
        }
        Map<String, Object> evidence = baseEvidence(report);
        evidence.put("missingEmiCount", missingEmi);
        evidence.put("emiInvented", false);
        String quality = partial ? "PARTIAL" : "OK";
        return result(report, TOTAL_MONTHLY_OBLIGATION, BureauMetricOutcome.PASS.name(),
                valueOf(sum), quality, included, excluded, 0, evidence);
    }

    private CiMetricResult computeMaxDpd(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            int months,
            String code,
            Map<String, Object> reportData,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        LocalDate asOf = asOfRes.asOf();
        List<PaymentHistoryMonthInput> rows = flattenHistory(tradelines, history);
        boolean anyPhRow = !rows.isEmpty();

        MaxDpdEvaluation eval = evaluateMaxDpd(rows, asOf, months);
        Map<String, Object> evidence = baseEvidence(report);
        evidence.putAll(eval.evidence());
        evidence.put("canonicalParameterId", code);
        evidence.put("windowMonths", months);
        evidence.put("asOfSource", asOfRes.source());

        if (!anyPhRow || BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())) {
            // Soft aggregate signal in evidence only — metric DATA_INSUFFICIENT, not zero
            Map<String, Object> soft = new LinkedHashMap<>();
            if (reportData != null) {
                soft.put("dpd30Plus", reportData.get("dpd30Plus"));
                soft.put("dpd60Plus", reportData.get("dpd60Plus"));
                soft.put("dpd90Plus", reportData.get("dpd90Plus"));
            }
            if (report.getMetadata() != null) {
                soft.putAll(report.getMetadata());
            }
            evidence.put("aggregateDpdFlags", soft);
            evidence.put("signal", "EXTRACTED_SOFT_ONLY");
            return result(report, code, BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, "DATA_INSUFFICIENT", eval.included(), eval.excluded(), 0, evidence);
        }
        return result(report, code, BureauMetricOutcome.PASS.name(),
                valueOf(eval.maxDpd() != null ? eval.maxDpd() : 0),
                eval.quality(),
                eval.included(),
                eval.excluded(),
                0,
                evidence);
    }

    private CiMetricResult computeInquiries90d(CiBureauReport report, Map<String, Object> reportData) {
        // Prefer explicit inquiry list count within 90d; else use enquiryAge30Days / recent as partial
        Integer count = null;
        Map<String, Object> evidence = baseEvidence(report);
        if (reportData != null && reportData.get("inquiries") instanceof List<?> list) {
            BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                    BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, true);
            LocalDate asOf = asOfRes.asOf();
            evidence.put("asOf", asOf.toString());
            evidence.put("asOfSource", asOfRes.source());
            LocalDate cutoff = asOf.minusDays(90);
            int c = 0;
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object d = m.get("inquiryDate");
                    LocalDate id = EquifaxBureauAccountExtractorSafe.parse(d);
                    if (id != null && !id.isBefore(cutoff)) {
                        c++;
                    }
                }
            }
            count = c;
            evidence.put("source", "INQUIRY_LIST");
        } else if (reportData != null && reportData.get("enquiryAge30Days") != null) {
            // Only 30d aggregate available — still report as metric with PARTIAL quality using 30d as lower bound signal
            // Spec asks recent_inquiries_90d — without 90d data → DATA_INSUFFICIENT rather than invent
            evidence.put("enquiryAge30Days", reportData.get("enquiryAge30Days"));
            evidence.put("reason", "NO_90D_INQUIRY_DETAIL");
            return result(report, RECENT_INQUIRIES_90D, BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                    null, "PARTIAL", List.of(), List.of(), 0, evidence);
        } else {
            return insufficient(report, RECENT_INQUIRIES_90D, "NO_INQUIRY_DATA");
        }
        return result(report, RECENT_INQUIRIES_90D, BureauMetricOutcome.PASS.name(),
                valueOf(count), "OK", List.of(), List.of(), 0, evidence);
    }

    private CiMetricResult computeStatusCount(
            CiBureauReport report, List<CiBureauTradeline> tradelines, String code,
            boolean settled, boolean writtenOff) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        int count = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : safe(tradelines)) {
            if (t.getDuplicateOfTradelineId() != null) {
                continue;
            }
            if (settled && t.isSettled()) {
                count++;
                included.add(refOf(t));
            }
            if (writtenOff && t.isWrittenOff()) {
                count++;
                included.add(refOf(t));
            }
        }
        return result(report, code, BureauMetricOutcome.PASS.name(),
                valueOf(count), "OK", included, List.of(), 0, baseEvidence(report));
    }

    private CiMetricResult computeInquiriesCurrentMonth(CiBureauReport report, Map<String, Object> reportData) {
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, true);
        List<LocalDate> dates = BureauDerivedMetricCalculator.inquiryDates(reportData);
        ScalarEvaluation eval = dates == null
                ? insufficientInquiryEval("NO_INQUIRY_DATA")
                : evaluateInquiriesCurrentMonth(dates, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, INQUIRIES_CURRENT_MONTH, eval);
    }

    private CiMetricResult computeInquiriesLast3Months(CiBureauReport report, Map<String, Object> reportData) {
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, true);
        List<LocalDate> dates = BureauDerivedMetricCalculator.inquiryDates(reportData);
        ScalarEvaluation eval = dates == null
                ? insufficientInquiryEval("NO_INQUIRY_DATA")
                : evaluateInquiriesLast3Months(dates, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, INQUIRIES_LAST_3M, eval);
    }

    private CiMetricResult computeDpdPlusCount(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            int threshold,
            String code) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), null, false);
        List<PaymentHistoryMonthInput> rows = flattenHistory(tradelines, history);
        ScalarEvaluation eval = evaluateDpdPlusCount(rows, asOfRes.asOf(), 6, threshold);
        stampAsOf(eval, asOfRes);
        return toMetric(report, code, eval);
    }

    private CiMetricResult computeMonthsSinceLastDelinquency(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            Map<String, Object> reportData) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, MONTHS_SINCE_LAST_DELINQUENCY, "TRADELINES_NOT_AVAILABLE");
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        ScalarEvaluation eval = evaluateMonthsSinceLastDelinquency(
                flattenHistory(tradelines, history), asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, MONTHS_SINCE_LAST_DELINQUENCY, eval);
    }

    private CiMetricResult computeVintage(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<String, Object> reportData,
            boolean oldest,
            String code) {
        CiMetricResult gated = tradelineGate(report, tradelines, code);
        if (gated != null) {
            return gated;
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        ScalarEvaluation eval = BureauDerivedMetricCalculator.vintageOldestAndAverage(tls, asOfRes.asOf(), oldest);
        stampAsOf(eval, asOfRes);
        return toMetric(report, code, eval);
    }

    private CiMetricResult computeCcOverdueAmount(CiBureauReport report, List<CiBureauTradeline> tradelines) {
        CiMetricResult gated = tradelineGate(report, tradelines, CC_OVERDUE_AMOUNT);
        if (gated != null) {
            return gated;
        }
        List<CcOverdueInput> inputs = new ArrayList<>();
        for (CiBureauTradeline t : emptyExtraction(report) ? List.<CiBureauTradeline>of() : safe(tradelines)) {
            inputs.add(new CcOverdueInput(
                    BureauDerivedMetricCalculator.isCreditCard(t),
                    t.getOverdueAmount(),
                    t.getDuplicateOfTradelineId() != null));
        }
        return toMetric(report, CC_OVERDUE_AMOUNT, evaluateCcOverdueAmount(inputs));
    }

    private CiMetricResult computeCcUtilisation(CiBureauReport report, List<CiBureauTradeline> tradelines) {
        CiMetricResult gated = tradelineGate(report, tradelines, CC_UTILISATION);
        if (gated != null) {
            return gated;
        }
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        return toMetric(report, CC_UTILISATION, evaluateCcUtilisation(tls));
    }

    private CiMetricResult computeOverdueAmount(CiBureauReport report, List<CiBureauTradeline> tradelines) {
        CiMetricResult gated = tradelineGate(report, tradelines, OVERDUE_AMOUNT);
        if (gated != null) {
            return gated;
        }
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        return toMetric(report, OVERDUE_AMOUNT, BureauDerivedMetricCalculator.overdueAmountNonCc(tls));
    }

    private CiMetricResult computeOverdueAgeMonths(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            Map<String, Object> reportData) {
        CiMetricResult gated = tradelineGate(report, tradelines, OVERDUE_AGE_MONTHS);
        if (gated != null) {
            return gated;
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        ScalarEvaluation eval = BureauDerivedMetricCalculator.overdueAgeMonths(tls, history, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, OVERDUE_AGE_MONTHS, eval);
    }

    private CiMetricResult computeCreditAfterOverdueExists(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            Map<String, Object> reportData) {
        CiMetricResult gated = tradelineGate(report, tradelines, CREDIT_AFTER_OVERDUE_EXISTS);
        if (gated != null) {
            return gated;
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        ScalarEvaluation eval = BureauDerivedMetricCalculator.creditAfterOverdueExists(tls, history, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, CREDIT_AFTER_OVERDUE_EXISTS, eval);
    }

    private CiMetricResult computeCreditAfterOverdueCleanHistory(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            Map<String, Object> reportData) {
        CiMetricResult gated = tradelineGate(report, tradelines, CREDIT_AFTER_OVERDUE_CLEAN_HISTORY_MONTHS);
        if (gated != null) {
            return gated;
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        ScalarEvaluation eval = BureauDerivedMetricCalculator.creditAfterOverdueCleanHistoryMonths(
                tls, history, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, CREDIT_AFTER_OVERDUE_CLEAN_HISTORY_MONTHS, eval);
    }

    private CiMetricResult computeNonCcOverdueExceptionViolations(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            Map<String, Object> reportData) {
        CiMetricResult gated = tradelineGate(report, tradelines, NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT);
        if (gated != null) {
            return gated;
        }
        BureauDerivedMetricCalculator.AsOfResolution asOfRes =
                BureauDerivedMetricCalculator.resolveAsOf(report.getReportDate(), reportData, false);
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        ScalarEvaluation eval = BureauDerivedMetricCalculator.nonCcOverdueExceptionViolationCount(
                tls, history, asOfRes.asOf());
        stampAsOf(eval, asOfRes);
        return toMetric(report, NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT, eval);
    }

    private CiMetricResult computeSuitFiledAccountCount(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        CiMetricResult gated = tradelineGate(report, tradelines, SUIT_FILED_ACCOUNT_COUNT);
        if (gated != null) {
            return gated;
        }
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        return toMetric(report, SUIT_FILED_ACCOUNT_COUNT,
                BureauDerivedMetricCalculator.suitFiledAccountCount(tls, history));
    }

    private CiMetricResult computeEquifaxAdverseAccountCount(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            EquifaxRetailPaymentStatusVocabulary.Family family) {
        String code = EquifaxRetailPaymentStatusVocabulary.canonicalParameterId(family);
        CiMetricResult gated = tradelineGate(report, tradelines, code);
        if (gated != null) {
            return gated;
        }
        List<CiBureauTradeline> tls = emptyExtraction(report) ? List.of() : safe(tradelines);
        return toMetric(report, code,
                BureauDerivedMetricCalculator.equifaxAdverseAccountCount(tls, history, family));
    }

    private CiMetricResult computePanDistinctCount(CiBureauReport report, Map<String, Object> reportData) {
        return toMetric(report, PAN_DISTINCT_COUNT, evaluatePanDistinctCount(reportData));
    }

    private Map<UUID, List<PaymentHistoryMonthInput>> loadHistoryInputs(List<CiBureauTradeline> tradelines) {
        Map<UUID, List<PaymentHistoryMonthInput>> out = new LinkedHashMap<>();
        if (paymentHistoryRepository == null) {
            return out;
        }
        for (CiBureauTradeline t : safe(tradelines)) {
            if (t.getId() == null) {
                continue;
            }
            List<CiBureauPaymentHistory> ph = paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(t.getId());
            out.put(t.getId(), BureauDerivedMetricCalculator.toInputs(t, ph));
        }
        return out;
    }

    private static List<PaymentHistoryMonthInput> flattenHistory(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        List<PaymentHistoryMonthInput> rows = new ArrayList<>();
        if (history == null) {
            return rows;
        }
        for (CiBureauTradeline t : safe(tradelines)) {
            if (t.getId() == null) {
                continue;
            }
            List<PaymentHistoryMonthInput> ph = history.get(t.getId());
            if (ph != null) {
                rows.addAll(ph);
            }
        }
        return rows;
    }

    private static CiMetricResult tradelineGate(
            CiBureauReport report, List<CiBureauTradeline> tradelines, String code) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        if (emptyExtraction(report)) {
            return null;
        }
        if (tradelines == null || tradelines.isEmpty()) {
            return insufficient(report, code, "TRADELINE_LIST_EMPTY_WITHOUT_EMPTY_STATUS");
        }
        return null;
    }

    private static boolean emptyExtraction(CiBureauReport report) {
        return "EMPTY".equalsIgnoreCase(nullToEmpty(report.getTradelineExtractionStatus()));
    }

    private static ScalarEvaluation insufficientInquiryEval(String reason) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reason", reason);
        return new ScalarEvaluation(
                BureauMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                evidence, List.of(), List.of(), null);
    }

    private static void stampAsOf(ScalarEvaluation eval, BureauDerivedMetricCalculator.AsOfResolution asOfRes) {
        if (eval != null && eval.evidence() != null && asOfRes != null) {
            eval.evidence().putIfAbsent("asOf", asOfRes.asOf() != null ? asOfRes.asOf().toString() : null);
            eval.evidence().put("asOfSource", asOfRes.source());
        }
    }

    private static CiMetricResult toMetric(CiBureauReport report, String code, ScalarEvaluation eval) {
        if (eval == null) {
            return insufficient(report, code, "EVALUATION_NULL");
        }
        Map<String, Object> evidence = baseEvidence(report);
        if (eval.evidence() != null) {
            evidence.putAll(eval.evidence());
        }
        Map<String, Object> value;
        if (eval.valueMap() != null) {
            value = eval.valueMap();
        } else if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(eval.outcome())) {
            value = null;
        } else {
            value = valueOf(eval.value());
        }
        return result(
                report,
                code,
                eval.outcome(),
                value,
                eval.quality() != null ? eval.quality() : "OK",
                eval.included() != null ? eval.included() : List.of(),
                eval.excluded() != null ? eval.excluded() : List.of(),
                0,
                evidence);
    }

    private CiMetricResult persist(CiMetricResult r) {
        return metricResultRepository.save(r);
    }

    private static boolean isExtractionInsufficient(CiBureauReport report) {
        String status = report.getTradelineExtractionStatus() != null
                ? report.getTradelineExtractionStatus().toUpperCase(Locale.ROOT) : "";
        return "MISSING".equals(status) || "FAILED".equals(status) || "ABSENT".equals(status);
    }

    private static CiMetricResult insufficient(CiBureauReport report, String code, String reason) {
        Map<String, Object> evidence = baseEvidence(report);
        evidence.put("reason", reason);
        return result(report, code, BureauMetricOutcome.DATA_INSUFFICIENT.name(),
                null, "DATA_INSUFFICIENT", List.of(), List.of(), 0, evidence);
    }

    private static CiMetricResult result(
            CiBureauReport report,
            String code,
            String outcome,
            Map<String, Object> value,
            String quality,
            List<Object> included,
            List<Object> excluded,
            int unknownCount,
            Map<String, Object> evidence) {
        return CiMetricResult.builder()
                .tenantId(report.getTenantId())
                .applicationId(report.getApplicationId())
                .bureauReportId(report.getId())
                .metricCode(code)
                .metricVersion(METRIC_VERSION)
                .outcome(outcome)
                .value(value)
                .dataQualityStatus(quality)
                .includedReferences(included)
                .excludedReferences(excluded)
                .unknownCount(unknownCount)
                .sourceRecordIds(report.getSourceRecordId() != null
                        ? List.of(report.getSourceRecordId().toString())
                        : List.of())
                .evidence(evidence)
                .metadata(Map.of(
                        "liveDefinition", LiveAccountDefinition.BUREAU_LIVE_ACCOUNT_DEFINITION_V1,
                        "taxonomyVersion", BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1))
                .build();
    }

    private static Map<String, Object> baseEvidence(CiBureauReport report) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("reportId", report.getId() != null ? report.getId().toString() : null);
        e.put("parserVersion", report.getParserVersion());
        e.put("normalizerVersion", report.getNormalizerVersion());
        e.put("tradelineExtractionStatus", report.getTradelineExtractionStatus());
        return e;
    }

    private static Map<String, Object> valueOf(Object v) {
        if (v == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (v instanceof BigDecimal bd) {
            m.put("v", bd.toPlainString());
        } else {
            m.put("v", v);
        }
        return m;
    }

    private static String refOf(CiBureauTradeline t) {
        if (t.getId() != null) {
            return t.getId().toString();
        }
        return t.getProviderTradelineRef() != null ? t.getProviderTradelineRef() : t.getSourceReference();
    }

    private static List<CiBureauTradeline> safe(List<CiBureauTradeline> tradelines) {
        return tradelines != null ? tradelines : List.of();
    }

    /** Local helper to avoid circular import noise in inquiry date parsing. */
    private static final class EquifaxBureauAccountExtractorSafe {
        static LocalDate parse(Object d) {
            if (d == null) {
                return null;
            }
            return com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor
                    .parseFlexibleDate(String.valueOf(d));
        }
    }
}
