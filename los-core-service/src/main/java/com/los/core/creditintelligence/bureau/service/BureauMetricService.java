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
    public static final String MAX_DPD_12M = "bureau.max_dpd_12m";
    public static final String MAX_DPD_24M = "bureau.max_dpd_24m";
    public static final String RECENT_INQUIRIES_90D = "bureau.recent_inquiries_90d";
    public static final String SETTLED_ACCOUNT_COUNT = "bureau.settled_account_count";
    public static final String WRITTEN_OFF_ACCOUNT_COUNT = "bureau.written_off_account_count";

    private final CiMetricResultRepository metricResultRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            Map<String, Object> reportData) {
        List<CiMetricResult> results = new ArrayList<>();
        results.add(persist(computeLiveUnsecured(report, tradelines)));
        results.add(persist(computeExposure(report, tradelines, TOTAL_LIVE_EXPOSURE, null)));
        results.add(persist(computeExposure(report, tradelines, SECURED_LIVE_EXPOSURE, true)));
        results.add(persist(computeExposure(report, tradelines, UNSECURED_LIVE_EXPOSURE, false)));
        results.add(persist(computeMonthlyObligation(report, tradelines)));
        results.add(persist(computeMaxDpd(report, tradelines, 12, MAX_DPD_12M, reportData)));
        results.add(persist(computeMaxDpd(report, tradelines, 24, MAX_DPD_24M, reportData)));
        results.add(persist(computeInquiries90d(report, reportData)));
        results.add(persist(computeStatusCount(report, tradelines, SETTLED_ACCOUNT_COUNT, true, false)));
        results.add(persist(computeStatusCount(report, tradelines, WRITTEN_OFF_ACCOUNT_COUNT, false, true)));
        return results;
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
