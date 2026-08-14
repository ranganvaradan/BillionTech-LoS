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

    public static final String WRITEOFF_CALCULATOR = "BureauMetricService.computeWriteoffCounts";

    private final CiMetricResultRepository metricResultRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final BureauStatusNormalizer statusNormalizer = new BureauStatusNormalizer();

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<String, Object> reportData) {
        List<CiMetricResult> results = new ArrayList<>();
        results.add(persist(computeStatusNtc(report, reportData)));
        results.add(persist(computeLiveUnsecured(report, tradelines)));
        results.add(persist(computeExposure(report, tradelines, TOTAL_LIVE_EXPOSURE, null)));
        results.add(persist(computeExposure(report, tradelines, SECURED_LIVE_EXPOSURE, true)));
        results.add(persist(computeExposure(report, tradelines, UNSECURED_LIVE_EXPOSURE, false)));
        results.add(persist(computeMonthlyObligation(report, tradelines)));
        results.add(persist(computeMaxDpd(report, tradelines, 6, MAX_DPD_6M, reportData)));
        results.add(persist(computeMaxDpd(report, tradelines, 12, MAX_DPD_12M, reportData)));
        results.add(persist(computeMaxDpd(report, tradelines, 24, MAX_DPD_24M, reportData)));
        results.add(persist(computeInquiries90d(report, reportData)));
        results.add(persist(computeStatusCount(report, tradelines, SETTLED_ACCOUNT_COUNT, true, false)));
        results.add(persist(computeStatusCount(report, tradelines, WRITTEN_OFF_ACCOUNT_COUNT, false, true)));
        results.addAll(persistWriteoffPair(report, computeWriteoffCounts(report, tradelines, reportData)));
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
            Map<String, Object> reportData) {
        if (!report.isTradelinesPresent() || isExtractionInsufficient(report)) {
            return insufficient(report, code, "TRADELINES_NOT_AVAILABLE");
        }
        LocalDate asOf = report.getReportDate() != null ? report.getReportDate() : LocalDate.now();
        LocalDate cutoff = asOf.minusMonths(months);
        Integer max = null;
        boolean anyPh = false;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : safe(tradelines)) {
            if (t.getId() == null) {
                continue;
            }
            List<CiBureauPaymentHistory> ph = paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(t.getId());
            for (CiBureauPaymentHistory row : ph) {
                anyPh = true;
                if (row.getMonth() != null && !row.getMonth().isBefore(cutoff) && row.getDpd() != null) {
                    if (max == null || row.getDpd() > max) {
                        max = row.getDpd();
                    }
                    included.add(Map.of(
                            "tradelineId", t.getId().toString(),
                            "month", row.getMonth().toString(),
                            "dpd", row.getDpd()));
                }
            }
        }
        Map<String, Object> evidence = baseEvidence(report);
        if (!anyPh) {
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
                    null, "DATA_INSUFFICIENT", included, List.of(), 0, evidence);
        }
        return result(report, code, BureauMetricOutcome.PASS.name(),
                valueOf(max != null ? max : 0), "OK", included, List.of(), 0, evidence);
    }

    private CiMetricResult computeInquiries90d(CiBureauReport report, Map<String, Object> reportData) {
        // Prefer explicit inquiry list count within 90d; else use enquiryAge30Days / recent as partial
        Integer count = null;
        Map<String, Object> evidence = baseEvidence(report);
        if (reportData != null && reportData.get("inquiries") instanceof List<?> list) {
            LocalDate asOf = report.getReportDate() != null ? report.getReportDate() : LocalDate.now();
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
                .sourceRecordIds(List.of(report.getSourceRecordId().toString()))
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
