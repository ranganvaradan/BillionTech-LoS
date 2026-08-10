package com.los.core.creditintelligence.gst.api;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.gst.domain.CiGstPeriodFinancials;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.CiGstReturnPeriod;
import com.los.core.creditintelligence.gst.repository.CiGstPeriodFinancialsRepository;
import com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository;
import com.los.core.creditintelligence.gst.repository.CiGstReturnPeriodRepository;
import com.los.core.creditintelligence.gst.service.CanonicalGstRuleEvaluator;
import com.los.core.creditintelligence.gst.service.GstMetricService;
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
 * Internal admin APIs for Phase C2 GST canonicalization.
 * Same X-Internal-Token security as Phase F / C1.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/gst")
@RequiredArgsConstructor
public class GstCanonicalAdminController {

    private final CiGstRegistrationRepository registrationRepository;
    private final CiGstReturnPeriodRepository periodRepository;
    private final CiGstPeriodFinancialsRepository financialsRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CreditIntelligenceProperties properties;
    private final CanonicalGstRuleEvaluator ruleEvaluator;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/registrations")
    public List<Map<String, Object>> getRegistrations(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return registrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(r -> tenantMatches(tenantHeader, r.getTenantId()))
                .map(this::registrationSummary)
                .toList();
    }

    @GetMapping("/registrations/{registrationId}/periods")
    public List<Map<String, Object>> getPeriods(
            @PathVariable UUID registrationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiGstRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
        assertTenant(tenantHeader, reg.getTenantId());
        return periodRepository.findByGstRegistrationId(registrationId).stream()
                .map(this::periodSummary)
                .toList();
    }

    @GetMapping("/periods/{periodId}/financials")
    public Map<String, Object> getFinancials(
            @PathVariable UUID periodId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiGstReturnPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found"));
        CiGstPeriodFinancials fin = financialsRepository.findByReturnPeriodId(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financials not found"));
        Map<String, Object> out = financialsSummary(fin);
        out.put("periodId", period.getId().toString());
        out.put("periodYyyyMm", period.getPeriodYyyyMm());
        out.put("returnType", period.getReturnType());
        return out;
    }

    @GetMapping("/applications/{applicationId}/metrics")
    public List<Map<String, Object>> getMetrics(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("gst."))
                .filter(m -> tenantMatches(tenantHeader, m.getTenantId()))
                .map(this::metricSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/gstr1-gstr3b-reconciliation")
    public Map<String, Object> gstr1Gstr3bReconciliation(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        Optional<CiMetricResult> variance = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, GstMetricService.VARIANCE);
        if (variance.isPresent()) {
            out.put("metric", metricSummary(variance.get()));
            out.put("evidence", SourceRegistryService.sanitizeMetadata(variance.get().getEvidence()));
            out.put("includedReferences", variance.get().getIncludedReferences());
        } else {
            out.put("metric", null);
        }
        return out;
    }

    @GetMapping("/applications/{applicationId}/legacy-vs-canonical")
    public Map<String, Object> legacyVsCanonical(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());

        List<CiGstRegistration> regs =
                registrationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        out.put("canonicalRegistrationsAvailable", !regs.isEmpty());
        out.put("registrations", regs.stream().map(this::registrationSummary).toList());

        Optional<CiMetricResult> t12 = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, GstMetricService.TRAILING_12M);
        if (t12.isPresent()) {
            out.put("canonicalTrailing12m", metricSummary(t12.get()));
            var eval = ruleEvaluator.evaluateTurnoverEligibility(
                    Map.of(GstMetricService.TRAILING_12M, t12.get()),
                    properties.getCanonicalization().getGst().getTurnoverEligibilityThreshold());
            out.put("canonicalTurnoverEligibility", Map.of(
                    "ruleId", eval.ruleId(),
                    "outcome", eval.outcome(),
                    "value", eval.value() != null ? eval.value() : "",
                    "threshold", eval.threshold() != null ? eval.threshold() : "",
                    "versions", eval.versions()));
        } else {
            out.put("canonicalTrailing12m", null);
        }

        Optional<CiCreditEvaluation> shadow = evaluationRepository
                .findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(e -> EvaluationType.SHADOW.name().equals(e.getEvaluationType()))
                .findFirst();
        if (shadow.isPresent() && shadow.get().getMetadata() != null) {
            out.put("shadowGstComparison", shadow.get().getMetadata().get("gstComparison"));
            out.put("shadowEvaluationId", shadow.get().getId().toString());
        }
        out.put("note", "Production CreditControl / SCF gap default unchanged");
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing GST CI API without token (local only)");
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

    private Map<String, Object> registrationSummary(CiGstRegistration r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("applicationId", r.getApplicationId().toString());
        m.put("tenantId", r.getTenantId().toString());
        m.put("gstin", r.getGstin());
        m.put("legalName", r.getLegalName());
        m.put("tradeName", r.getTradeName());
        m.put("registrationStatus", r.getRegistrationStatus());
        m.put("registrationDate", r.getRegistrationDate() != null ? r.getRegistrationDate().toString() : null);
        m.put("parserVersion", r.getParserVersion());
        m.put("normalizerVersion", r.getNormalizerVersion());
        m.put("qualityStatus", r.getQualityStatus());
        m.put("metadata", SourceRegistryService.sanitizeMetadata(r.getMetadata()));
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> periodSummary(CiGstReturnPeriod p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("returnType", p.getReturnType());
        m.put("financialYear", p.getFinancialYear());
        m.put("periodYyyyMm", p.getPeriodYyyyMm());
        m.put("filingStatus", p.getFilingStatus());
        m.put("filingDelayDays", p.getFilingDelayDays());
        m.put("effective", p.isEffective());
        m.put("dueDate", p.getDueDate() != null ? p.getDueDate().toString() : null);
        m.put("filedDate", p.getFiledDate() != null ? p.getFiledDate().toString() : null);
        return m;
    }

    private Map<String, Object> financialsSummary(CiGstPeriodFinancials f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId().toString());
        m.put("returnType", f.getReturnType());
        m.put("taxableTurnover", f.getTaxableTurnover());
        m.put("taxLiability", f.getTaxLiability());
        m.put("extractionQuality", f.getExtractionQuality());
        m.put("metadata", SourceRegistryService.sanitizeMetadata(f.getMetadata()));
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
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }
}
