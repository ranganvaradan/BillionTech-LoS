package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-application bureau ingest evidence. Does not decide platform executability —
 * feed into {@link BureauApplicationExecutabilityService} with {@code ParameterExecutabilitySupport}.
 */
@Service
@RequiredArgsConstructor
public class BureauDataReadinessProbe {

    private static final Map<String, String> PATH_TO_METRIC = Map.ofEntries(
            Map.entry("bureau.score", "__REPORT_SCORE__"),
            Map.entry("bureau.live_unsecured_loan_count", BureauMetricService.LIVE_UNSECURED),
            Map.entry("bureau.live_unsecured_count", BureauMetricService.LIVE_UNSECURED),
            Map.entry("bureau.max_dpd_6m", BureauMetricService.MAX_DPD_6M),
            Map.entry("bureau.max_dpd_12m", BureauMetricService.MAX_DPD_12M),
            Map.entry("bureau.max_dpd_24m", BureauMetricService.MAX_DPD_24M),
            Map.entry("bureau.recent_inquiries_90d", BureauMetricService.RECENT_INQUIRIES_90D),
            Map.entry("bureau.inquiries.current_month", BureauMetricService.RECENT_INQUIRIES_90D),
            Map.entry("bureau.total_monthly_obligation", BureauMetricService.TOTAL_MONTHLY_OBLIGATION),
            Map.entry("bureau.emi.monthly", BureauMetricService.TOTAL_MONTHLY_OBLIGATION),
            Map.entry("bureau.written_off_account_count", BureauMetricService.WRITTEN_OFF_ACCOUNT_COUNT),
            Map.entry("bureau.settled_account_count", BureauMetricService.SETTLED_ACCOUNT_COUNT),
            Map.entry("bureau.status_ntc", BureauMetricService.STATUS_NTC)
    );

    private final CiBureauReportRepository reportRepository;
    private final CiMetricResultRepository metricResultRepository;

    public boolean isBureauPath(String path) {
        return path != null && path.startsWith("bureau.");
    }

    @Transactional(readOnly = true)
    public BureauReadiness readinessForPath(UUID applicationId, String path) {
        if (applicationId == null || path == null || !isBureauPath(path)) {
            return BureauReadiness.NOT_APPLICABLE;
        }
        Optional<CiBureauReport> reportOpt =
                reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (reportOpt.isEmpty()) {
            return BureauReadiness.NO_BUREAU_PULL;
        }
        CiBureauReport report = reportOpt.get();
        if (isExtractionBad(report)) {
            return BureauReadiness.INGEST_INCOMPLETE;
        }
        String metricCode = PATH_TO_METRIC.get(path);
        if ("__REPORT_SCORE__".equals(metricCode)) {
            // Positive scores and Equifax sentinel -1 are ready; 0/null are incomplete (not NTC).
            Integer score = report.getScore();
            return score != null && (score > 0 || score == -1)
                    ? BureauReadiness.READY
                    : BureauReadiness.INGEST_INCOMPLETE;
        }
        if (metricCode == null) {
            return BureauReadiness.READY;
        }
        Optional<CiMetricResult> metricOpt = metricResultRepository
                .findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(report.getId(), metricCode);
        if (metricOpt.isEmpty()) {
            metricOpt = metricResultRepository
                    .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, metricCode);
        }
        if (metricOpt.isEmpty()) {
            return BureauReadiness.INGEST_INCOMPLETE;
        }
        CiMetricResult metric = metricOpt.get();
        if (BureauMetricOutcome.PASS.name().equals(metric.getOutcome())
                && metric.getValue() != null && metric.getValue().get("v") != null) {
            return BureauReadiness.READY;
        }
        return BureauReadiness.INGEST_INCOMPLETE;
    }

    @Transactional(readOnly = true)
    public boolean hasAnyBureauIngest(UUID applicationId) {
        return reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).isPresent();
    }

    private static boolean isExtractionBad(CiBureauReport report) {
        String status = report.getTradelineExtractionStatus() != null
                ? report.getTradelineExtractionStatus().toUpperCase(Locale.ROOT) : "";
        return "MISSING".equals(status) || "FAILED".equals(status) || "ABSENT".equals(status);
    }

    public enum BureauReadiness {
        NOT_APPLICABLE,
        NO_BUREAU_PULL,
        INGEST_INCOMPLETE,
        READY
    }
}
