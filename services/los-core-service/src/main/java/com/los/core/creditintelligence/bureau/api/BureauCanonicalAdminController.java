package com.los.core.creditintelligence.bureau.api;

import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauTradelineRepository;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.service.SourceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal admin APIs for Phase C1 bureau canonicalization.
 * Same X-Internal-Token security as Phase F CreditIntelligenceAdminController.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/bureau")
@RequiredArgsConstructor
public class BureauCanonicalAdminController {

    private final CiBureauReportRepository reportRepository;
    private final CiBureauTradelineRepository tradelineRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CreditIntelligenceProperties properties;
    private final CanonicalBureauRuleEvaluator ruleEvaluator;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/report")
    public Map<String, Object> getReport(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiBureauReport report = reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bureau report not found"));
        assertTenant(tenantHeader, report.getTenantId());
        return reportSummary(report);
    }

    @GetMapping("/reports/{reportId}/tradelines")
    public List<Map<String, Object>> getTradelines(
            @PathVariable UUID reportId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiBureauReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        assertTenant(tenantHeader, report.getTenantId());
        return tradelineRepository.findByBureauReportId(reportId).stream()
                .map(this::tradelineSummary)
                .toList();
    }

    @GetMapping("/tradelines/{tradelineId}/payment-history")
    public List<Map<String, Object>> getPaymentHistory(
            @PathVariable UUID tradelineId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiBureauTradeline tl = tradelineRepository.findById(tradelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tradeline not found"));
        return paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(tl.getId()).stream()
                .map(this::phSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/metrics")
    public List<Map<String, Object>> getMetrics(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> tenantMatches(tenantHeader, m.getTenantId()))
                .map(this::metricSummary)
                .toList();
    }

    @GetMapping("/metrics/{metricId}/evidence")
    public Map<String, Object> getMetricEvidence(
            @PathVariable UUID metricId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiMetricResult m = metricResultRepository.findById(metricId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metric not found"));
        assertTenant(tenantHeader, m.getTenantId());
        Map<String, Object> out = metricSummary(m);
        out.put("includedReferences", m.getIncludedReferences());
        out.put("excludedReferences", m.getExcludedReferences());
        out.put("evidence", SourceRegistryService.sanitizeMetadata(m.getEvidence()));
        out.put("metadata", SourceRegistryService.sanitizeMetadata(m.getMetadata()));
        return out;
    }

    @GetMapping("/applications/{applicationId}/legacy-vs-canonical")
    public Map<String, Object> legacyVsCanonical(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());

        Optional<CiBureauReport> report =
                reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
        out.put("canonicalReportAvailable", report.isPresent());
        report.ifPresent(r -> out.put("report", reportSummary(r)));

        Optional<CiMetricResult> live = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, BureauMetricService.LIVE_UNSECURED);
        if (live.isPresent()) {
            out.put("canonicalLiveUnsecured", metricSummary(live.get()));
            int threshold = properties.getCanonicalization().getBureau().getLiveUnsecuredThreshold();
            var eval = ruleEvaluator.evaluate(live.get(), threshold);
            out.put("canonicalRule", Map.of(
                    "ruleId", eval.ruleId(),
                    "outcome", eval.outcome(),
                    "value", eval.value() != null ? eval.value() : "",
                    "threshold", threshold,
                    "versions", eval.versions()));
        } else {
            out.put("canonicalLiveUnsecured", null);
        }

        Optional<CiCreditEvaluation> shadow = evaluationRepository
                .findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(e -> EvaluationType.SHADOW.name().equals(e.getEvaluationType()))
                .findFirst();
        if (shadow.isPresent() && shadow.get().getMetadata() != null) {
            Object bureauComparison = shadow.get().getMetadata().get("bureauComparison");
            out.put("shadowBureauComparison", bureauComparison);
            out.put("shadowEvaluationId", shadow.get().getId().toString());
        }
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing bureau CI API without token (local only)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }

    private void assertTenant(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return;
        }
        if (!tenantMatches(tenantHeader, tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
        }
    }

    private static boolean tenantMatches(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return true;
        }
        try {
            return UUID.fromString(tenantHeader).equals(tenantId);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> reportSummary(CiBureauReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("applicationId", r.getApplicationId().toString());
        m.put("tenantId", r.getTenantId().toString());
        m.put("providerCode", r.getProviderCode());
        m.put("subjectType", r.getSubjectType());
        m.put("score", r.getScore());
        m.put("reportDate", r.getReportDate() != null ? r.getReportDate().toString() : null);
        m.put("parserVersion", r.getParserVersion());
        m.put("normalizerVersion", r.getNormalizerVersion());
        m.put("tradelinesPresent", r.isTradelinesPresent());
        m.put("tradelineExtractionStatus", r.getTradelineExtractionStatus());
        m.put("qualityStatus", r.getQualityStatus());
        m.put("metadata", SourceRegistryService.sanitizeMetadata(r.getMetadata()));
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> tradelineSummary(CiBureauTradeline t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("lenderName", t.getLenderName());
        m.put("accountTypeRaw", t.getAccountTypeRaw());
        m.put("productCategory", t.getProductCategory());
        m.put("secured", t.getSecured());
        m.put("revolving", t.getRevolving());
        m.put("currentBalance", t.getCurrentBalance());
        m.put("emiAmount", t.getEmiAmount());
        m.put("accountStatus", t.getAccountStatus());
        m.put("isLive", t.getIsLive());
        m.put("liveDefinitionVersion", t.getLiveDefinitionVersion());
        m.put("duplicateOfTradelineId",
                t.getDuplicateOfTradelineId() != null ? t.getDuplicateOfTradelineId().toString() : null);
        m.put("dataQualityStatus", t.getDataQualityStatus());
        m.put("openedDate", t.getOpenedDate() != null ? t.getOpenedDate().toString() : null);
        m.put("lastReportedDate", t.getLastReportedDate() != null ? t.getLastReportedDate().toString() : null);
        // Never expose raw account numbers — metadata may contain last4 only
        m.put("metadata", SourceRegistryService.sanitizeMetadata(t.getMetadata()));
        return m;
    }

    private Map<String, Object> phSummary(CiBureauPaymentHistory p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("month", p.getMonth() != null ? p.getMonth().toString() : null);
        m.put("dpd", p.getDpd());
        m.put("status", p.getStatus());
        m.put("providerRawStatus", p.getProviderRawStatus());
        m.put("estimated", p.isEstimated());
        return m;
    }

    private Map<String, Object> metricSummary(CiMetricResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("metricCode", r.getMetricCode());
        m.put("metricVersion", r.getMetricVersion());
        m.put("outcome", r.getOutcome());
        m.put("value", r.getValue());
        m.put("dataQualityStatus", r.getDataQualityStatus());
        m.put("unknownCount", r.getUnknownCount());
        m.put("bureauReportId", r.getBureauReportId() != null ? r.getBureauReportId().toString() : null);
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }
}
