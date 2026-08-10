package com.los.core.creditintelligence.tax.api;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.tax.domain.CiAisSummary;
import com.los.core.creditintelligence.tax.domain.CiForm26AsSummary;
import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import com.los.core.creditintelligence.tax.repository.CiAisInformationRepository;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsEntryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrBusinessFinancialsRepository;
import com.los.core.creditintelligence.tax.repository.CiItrIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrPresumptiveIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.repository.CiItrTaxSummaryRepository;
import com.los.core.creditintelligence.tax.service.CanonicalTaxRuleEvaluator;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal admin APIs for Phase C4 ITR / AIS / Form 26AS canonicalization.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/tax")
@RequiredArgsConstructor
public class TaxCanonicalAdminController {

    private final CiItrReturnRepository returnRepository;
    private final CiItrIncomeRepository incomeRepository;
    private final CiItrBusinessFinancialsRepository businessRepository;
    private final CiItrPresumptiveIncomeRepository presumptiveRepository;
    private final CiItrTaxSummaryRepository taxSummaryRepository;
    private final CiAisSummaryRepository aisSummaryRepository;
    private final CiAisInformationRepository aisInformationRepository;
    private final CiForm26AsSummaryRepository form26AsSummaryRepository;
    private final CiForm26AsEntryRepository form26AsEntryRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CreditIntelligenceProperties properties;
    private final CanonicalTaxRuleEvaluator ruleEvaluator;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/returns")
    public List<Map<String, Object>> getReturns(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return returnRepository.findByApplicationIdOrderByAssessmentYearDescCreatedAtDesc(applicationId).stream()
                .filter(r -> tenantMatches(tenantHeader, r.getTenantId()))
                .filter(r -> !"PULL".equals(r.getAssessmentYear()))
                .map(this::returnSummary)
                .toList();
    }

    @GetMapping("/returns/{returnId}/income")
    public Map<String, Object> getIncome(
            @PathVariable UUID returnId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiItrIncome income = incomeRepository.findByItrReturnId(returnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found"));
        return incomeSummary(income);
    }

    @GetMapping("/returns/{returnId}/business-financials")
    public Map<String, Object> getBusinessFinancials(
            @PathVariable UUID returnId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiItrBusinessFinancials fin = businessRepository.findByItrReturnId(returnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business financials not found"));
        return businessSummary(fin);
    }

    @GetMapping("/returns/{returnId}/presumptive")
    public List<Map<String, Object>> getPresumptive(
            @PathVariable UUID returnId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return presumptiveRepository.findByItrReturnId(returnId).stream()
                .map(this::presumptiveSummary)
                .toList();
    }

    @GetMapping("/returns/{returnId}/tax-summary")
    public Map<String, Object> getTaxSummary(
            @PathVariable UUID returnId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiItrTaxSummary tax = taxSummaryRepository.findByItrReturnId(returnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax summary not found"));
        return taxSummary(tax);
    }

    @GetMapping("/applications/{applicationId}/ais")
    public List<Map<String, Object>> getAis(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return aisSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).stream()
                .map(s -> {
                    Map<String, Object> m = aisSummary(s);
                    m.put("information", aisInformationRepository.findByAisSummaryId(s.getId()).stream()
                            .map(i -> Map.<String, Object>of(
                                    "id", i.getId().toString(),
                                    "category", i.getCategory(),
                                    "amount", i.getAmount() != null ? i.getAmount().toPlainString() : ""))
                            .toList());
                    return m;
                })
                .toList();
    }

    @GetMapping("/applications/{applicationId}/form26as")
    public List<Map<String, Object>> getForm26As(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return form26AsSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).stream()
                .map(s -> {
                    Map<String, Object> m = form26Summary(s);
                    m.put("entries", form26AsEntryRepository.findByForm26asSummaryId(s.getId()).size());
                    return m;
                })
                .toList();
    }

    @GetMapping("/applications/{applicationId}/metrics")
    public List<Map<String, Object>> getMetrics(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null
                        && (m.getMetricCode().startsWith("itr.") || m.getMetricCode().startsWith("xsrc.itr_")))
                .map(this::metricSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/reconciliation")
    public Map<String, Object> reconciliation(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        Optional<CiMetricResult> tds = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, TaxMetricService.XSRC_26AS_TDS);
        Optional<CiMetricResult> ais = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, TaxMetricService.XSRC_AIS_INCOME);
        out.put("itr26asTdsVariance", tds.map(this::metricSummary).orElse(null));
        out.put("itrAisIncomeVariance", ais.map(this::metricSummary).orElse(null));
        tds.ifPresent(m -> out.put("tdsEvidence", SourceRegistryService.sanitizeMetadata(m.getEvidence())));
        ais.ifPresent(m -> out.put("aisEvidence", SourceRegistryService.sanitizeMetadata(m.getEvidence())));
        return out;
    }

    @GetMapping("/applications/{applicationId}/legacy-vs-canonical")
    public Map<String, Object> legacyVsCanonical(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        List<CiItrReturn> returns =
                returnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
        out.put("canonicalReturnsAvailable", !returns.isEmpty());
        out.put("returns", returns.stream().map(this::returnSummary).toList());

        Optional<CiMetricResult> turnover = metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, TaxMetricService.TURNOVER_LATEST);
        turnover.ifPresent(m -> out.put("canonicalTurnoverLatestFy", metricSummary(m)));

        Map<String, CiMetricResult> byCode = new LinkedHashMap<>();
        for (CiMetricResult m : metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            if (m.getMetricCode() != null && (m.getMetricCode().startsWith("itr.")
                    || m.getMetricCode().startsWith("xsrc.itr_"))) {
                byCode.putIfAbsent(m.getMetricCode(), m);
            }
        }
        var cfg = properties.getCanonicalization().getTax();
        out.put("shadowRules", ruleEvaluator.evaluateAll(
                returns, byCode,
                cfg.getMinIncomeThreshold(),
                cfg.getMinTurnoverThreshold(),
                cfg.isMinPatPositive(),
                null,
                LocalDate.now()).stream()
                .map(r -> Map.of(
                        "ruleId", r.ruleId(),
                        "outcome", r.outcome(),
                        "value", r.value() != null ? r.value() : "",
                        "threshold", r.threshold() != null ? r.threshold() : ""))
                .toList());

        Optional<CiCreditEvaluation> shadow = evaluationRepository
                .findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(e -> EvaluationType.SHADOW.name().equals(e.getEvaluationType()))
                .findFirst();
        if (shadow.isPresent() && shadow.get().getMetadata() != null) {
            out.put("shadowTaxComparison", shadow.get().getMetadata().get("taxComparison"));
            out.put("shadowEvaluationId", shadow.get().getId().toString());
        }
        out.put("note", "Production CreditControl / SCF_GAP_ITR_INCOME unchanged");
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token blank — allowing tax admin request (local)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }

    private boolean tenantMatches(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank() || tenantId == null) {
            return true;
        }
        return tenantId.toString().equalsIgnoreCase(tenantHeader.trim());
    }

    private Map<String, Object> returnSummary(CiItrReturn r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("assessmentYear", r.getAssessmentYear());
        m.put("financialYear", r.getFinancialYear());
        m.put("itrForm", r.getItrForm());
        m.put("panLast4", r.getPanLast4());
        m.put("filingDate", r.getFilingDate() != null ? r.getFilingDate().toString() : null);
        m.put("returnVersionType", r.getReturnVersionType());
        m.put("effective", r.isEffective());
        m.put("filingStatus", r.getFilingStatus());
        return m;
    }

    private Map<String, Object> incomeSummary(CiItrIncome i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId().toString());
        m.put("itrReturnId", i.getItrReturnId().toString());
        m.put("businessProfessionIncome", bd(i.getBusinessProfessionIncome()));
        m.put("grossTotalIncome", bd(i.getGrossTotalIncome()));
        m.put("totalIncome", bd(i.getTotalIncome()));
        m.put("salaryIncome", bd(i.getSalaryIncome()));
        return m;
    }

    private Map<String, Object> businessSummary(CiItrBusinessFinancials f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId().toString());
        m.put("salesTurnover", bd(f.getSalesTurnover()));
        m.put("ebitda", bd(f.getEbitda()));
        m.put("profitAfterTax", bd(f.getProfitAfterTax()));
        m.put("financeCost", bd(f.getFinanceCost()));
        m.put("totalLiabilities", bd(f.getTotalLiabilities()));
        m.put("netWorth", bd(f.getNetWorth()));
        return m;
    }

    private Map<String, Object> presumptiveSummary(CiItrPresumptiveIncome p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("applicableSection", p.getApplicableSection());
        m.put("grossReceipts", bd(p.getGrossReceipts()));
        m.put("declaredPresumptiveIncome", bd(p.getDeclaredPresumptiveIncome()));
        m.put("declaredMargin", bd(p.getDeclaredMargin()));
        return m;
    }

    private Map<String, Object> taxSummary(CiItrTaxSummary t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("taxLiability", bd(t.getTaxLiability()));
        m.put("taxPaid", bd(t.getTaxPaid()));
        m.put("tds", bd(t.getTds()));
        m.put("outstandingDemand", bd(t.getOutstandingDemand()));
        return m;
    }

    private Map<String, Object> aisSummary(CiAisSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("financialYear", s.getFinancialYear());
        m.put("totalReportedValue", bd(s.getTotalReportedValue()));
        m.put("informationSourceCount", s.getInformationSourceCount());
        return m;
    }

    private Map<String, Object> form26Summary(CiForm26AsSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("financialYear", s.getFinancialYear());
        m.put("totalTds", bd(s.getTotalTds()));
        m.put("totalTcs", bd(s.getTotalTcs()));
        return m;
    }

    private Map<String, Object> metricSummary(CiMetricResult m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId() != null ? m.getId().toString() : null);
        out.put("metricCode", m.getMetricCode());
        out.put("outcome", m.getOutcome());
        out.put("value", m.getValue());
        out.put("dataQualityStatus", m.getDataQualityStatus());
        return out;
    }

    private static String bd(java.math.BigDecimal v) {
        return v != null ? v.toPlainString() : null;
    }
}
