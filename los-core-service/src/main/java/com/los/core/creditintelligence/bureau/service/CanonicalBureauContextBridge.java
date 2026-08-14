package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Compatibility adapter: maps GACAT bureau metrics to legacy scorecard keys for production underwriting.
 * <p>
 * Must not calculate metrics, apply defaults, or interpret provider payloads — upstream calculators only.
 */
@Service
@RequiredArgsConstructor
public class CanonicalBureauContextBridge {

    private final CiBureauReportRepository reportRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CreditIntelligenceProperties properties;

    public record OverlayResult(
            Integer bureauScore,
            String bureauSource,
            Map<String, BigDecimal> scorecard,
            Map<String, String> provenance,
            Set<String> canonicalKeys) {
    }

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.Canonicalization.Bureau cfg =
                properties.getCanonicalization().getBureau();
        if (cfg == null || !cfg.isEnabled() || !cfg.isUseForProductionUnderwriting()) {
            return false;
        }
        if (app == null || app.getId() == null) {
            return false;
        }
        UUID tenantId = properties.getDefaultTenantId();
        List<String> tenantIds = cfg.getTenantIds();
        if (tenantIds != null && !tenantIds.isEmpty()) {
            String tid = tenantId != null ? tenantId.toString() : "";
            boolean match = tenantIds.stream().anyMatch(t -> t != null && t.equalsIgnoreCase(tid));
            if (!match) {
                return false;
            }
        }
        List<String> productCodes = cfg.getProductCodes();
        if (productCodes != null && !productCodes.isEmpty()) {
            String product = app.getLoanProduct() != null ? app.getLoanProduct() : "";
            boolean match = productCodes.stream()
                    .anyMatch(p -> p != null && p.equalsIgnoreCase(product));
            if (!match) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<OverlayResult> overlay(LoanApplication app) {
        if (!isEnabledFor(app)) {
            return Optional.empty();
        }
        Optional<CiBureauReport> reportOpt =
                reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId());
        if (reportOpt.isEmpty()) {
            return Optional.empty();
        }
        CiBureauReport report = reportOpt.get();
        if (isExtractionBad(report)) {
            return Optional.empty();
        }

        Map<String, BigDecimal> scorecard = new LinkedHashMap<>();
        Map<String, String> provenance = new LinkedHashMap<>();
        Set<String> canonicalKeys = new LinkedHashSet<>();
        Integer bureauScore = null;

        // bureau.score: allow positive scores and Equifax sentinel -1 only (not other negatives).
        if (report.getScore() != null && isAllowedBureauScore(report.getScore())) {
            bureauScore = report.getScore();
            put(scorecard, provenance, canonicalKeys, "BUREAU_SCORE", BigDecimal.valueOf(bureauScore));
        }

        // bureau.status_ntc → legacy NTC_FLAG (compatibility alias only; no decision logic here).
        applyMetric(app.getId(), report.getId(), BureauMetricService.STATUS_NTC,
                "NTC_FLAG", scorecard, provenance, canonicalKeys);

        applyMetric(app.getId(), report.getId(), BureauMetricService.LIVE_UNSECURED,
                "LIVE_UNSECURED_LOAN_COUNT", scorecard, provenance, canonicalKeys);
        applyMetric(app.getId(), report.getId(), BureauMetricService.MAX_DPD_6M,
                "MAX_DPD_6M", scorecard, provenance, canonicalKeys);
        applyMetric(app.getId(), report.getId(), BureauMetricService.MAX_DPD_12M,
                "MAX_DPD_12M", scorecard, provenance, canonicalKeys);
        applyMetric(app.getId(), report.getId(), BureauMetricService.RECENT_INQUIRIES_90D,
                "BUREAU_ENQUIRIES_3M", scorecard, provenance, canonicalKeys);
        applyMetric(app.getId(), report.getId(), BureauMetricService.TOTAL_MONTHLY_OBLIGATION,
                "MONTHLY_OBLIGATION", scorecard, provenance, canonicalKeys);
        applyMetric(app.getId(), report.getId(), BureauMetricService.TOTAL_MONTHLY_OBLIGATION,
                "EMI_OBLIGATION", scorecard, provenance, canonicalKeys);
        // Canonical write-off non-CC → legacy scorecard key (compatibility only; no calculation here).
        applyMetric(app.getId(), report.getId(), BureauMetricService.WRITEOFF_NON_CC,
                "WRITEOFF_NON_CC", scorecard, provenance, canonicalKeys);

        if (scorecard.isEmpty() && bureauScore == null) {
            return Optional.empty();
        }

        String bureauSource = ScorecardValueProvenance.CANONICAL;
        return Optional.of(new OverlayResult(bureauScore, bureauSource, scorecard, provenance, canonicalKeys));
    }

    private void applyMetric(
            UUID applicationId,
            UUID reportId,
            String metricCode,
            String scorecardKey,
            Map<String, BigDecimal> scorecard,
            Map<String, String> provenance,
            Set<String> canonicalKeys) {
        Optional<CiMetricResult> metricOpt = metricResultRepository
                .findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(reportId, metricCode);
        if (metricOpt.isEmpty()) {
            metricOpt = metricResultRepository
                    .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, metricCode);
        }
        if (metricOpt.isEmpty()) {
            return;
        }
        CiMetricResult metric = metricOpt.get();
        if (!BureauMetricOutcome.PASS.name().equals(metric.getOutcome())) {
            return;
        }
        BigDecimal value = decimalFrom(metric);
        if (value == null) {
            return;
        }
        put(scorecard, provenance, canonicalKeys, scorecardKey, value);
    }

    private static void put(
            Map<String, BigDecimal> scorecard,
            Map<String, String> provenance,
            Set<String> canonicalKeys,
            String key,
            BigDecimal value) {
        scorecard.put(key, value);
        provenance.put(key, ScorecardValueProvenance.CANONICAL);
        canonicalKeys.add(key);
    }

    private static BigDecimal decimalFrom(CiMetricResult metric) {
        if (metric.getValue() == null || metric.getValue().get("v") == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(metric.getValue().get("v")));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isExtractionBad(CiBureauReport report) {
        String status = report.getTradelineExtractionStatus() != null
                ? report.getTradelineExtractionStatus().toUpperCase(Locale.ROOT) : "";
        return "MISSING".equals(status) || "FAILED".equals(status) || "ABSENT".equals(status);
    }

    /** Positive bureau scores, or Equifax no-hit sentinel -1 only. */
    static boolean isAllowedBureauScore(int score) {
        return score > 0 || score == -1;
    }
}
