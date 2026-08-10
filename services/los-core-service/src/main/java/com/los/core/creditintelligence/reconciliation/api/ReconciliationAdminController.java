package com.los.core.creditintelligence.reconciliation.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationEvidence;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.repository.CiCreditEvidenceSummaryRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationEvidenceRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.creditintelligence.reconciliation.service.CanonicalReconciliationRuleEvaluator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal admin APIs for Phase C5 cross-source reconciliation.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/reconciliation")
@RequiredArgsConstructor
public class ReconciliationAdminController {

    private final CiReconciliationResultRepository resultRepository;
    private final CiReconciliationEvidenceRepository evidenceRepository;
    private final CiCreditEvidenceSummaryRepository evidenceSummaryRepository;
    private final ReconciliationIngestionService ingestionService;
    private final CanonicalReconciliationRuleEvaluator ruleEvaluator;
    private final CreditIntelligenceProperties properties;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/reconciliations")
    public List<Map<String, Object>> listReconciliations(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        ingestionService.ensureRan(applicationId, null, null, null);
        return latestByCode(applicationId).stream()
                .filter(r -> tenantMatches(tenantHeader, r.getTenantId()))
                .map(this::resultSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/reconciliations/{code}")
    public Map<String, Object> getReconciliation(
            @PathVariable UUID applicationId,
            @PathVariable String code,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiReconciliationResult r = resultRepository
                .findFirstByApplicationIdAndReconciliationCodeOrderByExecutedAtDesc(applicationId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation not found"));
        Map<String, Object> out = explainability(r);
        List<CiReconciliationEvidence> evidence = evidenceRepository.findByReconciliationResultId(r.getId());
        out.put("evidence", evidence.stream().map(this::evidenceSummary).toList());
        return out;
    }

    @GetMapping("/applications/{applicationId}/credit-evidence-summary")
    public Map<String, Object> creditEvidenceSummary(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        ingestionService.ensureRan(applicationId, null, null, null);
        CiCreditEvidenceSummary s = evidenceSummaryRepository
                .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence summary not found"));
        return summaryMap(s);
    }

    @GetMapping("/applications/{applicationId}/evidence-strength")
    public Map<String, Object> evidenceStrength(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiCreditEvidenceSummary s = evidenceSummaryRepository
                .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence summary not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", s.getEvidenceStrengthScore());
        out.put("grade", s.getEvidenceStrengthGrade());
        out.put("methodVersion", s.getMethodVersion());
        out.put("note", "NOT_A_CREDIT_OR_RISK_SCORE");
        out.put("components", s.getReconciliationQuality() != null
                ? s.getReconciliationQuality().get("evidenceStrengthComponents") : null);
        return out;
    }

    @GetMapping("/applications/{applicationId}/cross-source-comparison")
    public Map<String, Object> crossSourceComparison(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        List<CiReconciliationResult> latest = latestByCode(applicationId);
        Map<String, CiReconciliationResult> byCode = new LinkedHashMap<>();
        for (CiReconciliationResult r : latest) {
            byCode.put(r.getReconciliationCode(), r);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());

        // Underwriter-friendly turnover block
        CiReconciliationResult gstItr = byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER);
        CiReconciliationResult gstBank = byCode.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER);
        CiReconciliationResult itrBank = byCode.get(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER);
        CiReconciliationResult tri = byCode.get(ReconciliationConstants.TURNOVER_TRIANGULATION);

        Map<String, Object> turnover = new LinkedHashMap<>();
        turnover.put("gstTurnover", gstItr != null ? gstItr.getLeftValue()
                : (gstBank != null ? gstBank.getLeftValue() : null));
        turnover.put("itrTurnover", gstItr != null ? gstItr.getRightValue()
                : (itrBank != null ? itrBank.getLeftValue() : null));
        turnover.put("bankAdjustedCredits", gstBank != null ? gstBank.getRightValue()
                : (itrBank != null ? itrBank.getRightValue() : null));
        turnover.put("gstItr", pairView(gstItr));
        turnover.put("gstBank", pairView(gstBank));
        turnover.put("itrBank", pairView(itrBank));
        if (tri != null) {
            turnover.put("triangulationStatus", tri.getMetadata() != null ? tri.getMetadata().get("status") : null);
            turnover.put("triangulationConfidence", tri.getConfidence());
            turnover.put("reasons", tri.getExplanationCodes());
            turnover.put("explanation", tri.getExplanation());
        }
        out.put("turnover", turnover);

        Map<String, Object> obligation = new LinkedHashMap<>();
        obligation.put("bureauBank", pairView(byCode.get(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION)));
        obligation.put("declaredBureau", pairView(byCode.get(ReconciliationConstants.XSRC_DECLARED_BUREAU_OBLIGATION)));
        obligation.put("declaredBank", pairView(byCode.get(ReconciliationConstants.XSRC_DECLARED_BANK_OBLIGATION)));
        out.put("obligation", obligation);

        List<CanonicalReconciliationRuleEvaluator.RuleEvalResult> rules = ruleEvaluator.evaluateAll(latest);
        out.put("shadowRules", rules.stream().map(r -> Map.of(
                "ruleId", r.ruleId(),
                "outcome", r.outcome(),
                "value", r.value() != null ? r.value() : "",
                "threshold", r.threshold() != null ? r.threshold() : "")).toList());

        evidenceSummaryRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .ifPresent(s -> {
                    out.put("evidenceStrengthScore", s.getEvidenceStrengthScore());
                    out.put("evidenceStrengthGrade", s.getEvidenceStrengthGrade());
                    out.put("materialConflicts", s.getMaterialConflicts());
                    out.put("dataGaps", s.getDataGaps());
                });

        out.put("note", "Shadow-only; production underwriting unchanged");
        return out;
    }

    private List<CiReconciliationResult> latestByCode(UUID applicationId) {
        List<CiReconciliationResult> all =
                resultRepository.findByApplicationIdOrderByExecutedAtDesc(applicationId);
        Map<String, CiReconciliationResult> latest = new LinkedHashMap<>();
        for (CiReconciliationResult r : all) {
            latest.putIfAbsent(r.getReconciliationCode(), r);
        }
        return new ArrayList<>(latest.values());
    }

    private Map<String, Object> resultSummary(CiReconciliationResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reconciliationCode", r.getReconciliationCode());
        m.put("definitionVersion", r.getDefinitionVersion());
        m.put("outcome", r.getOutcome());
        m.put("severity", r.getSeverity());
        m.put("percentageVariance", r.getPercentageVariance());
        m.put("absoluteVariance", r.getAbsoluteVariance());
        m.put("leftValue", r.getLeftValue());
        m.put("rightValue", r.getRightValue());
        m.put("dataStatus", r.getDataStatus());
        m.put("confidence", r.getConfidence());
        m.put("humanReviewRequired", r.isHumanReviewRequired());
        m.put("executedAt", r.getExecutedAt());
        return m;
    }

    private Map<String, Object> explainability(CiReconciliationResult r) {
        Map<String, Object> m = resultSummary(r);
        m.put("leftMetricCode", r.getLeftMetricCode());
        m.put("rightMetricCode", r.getRightMetricCode());
        m.put("leftPeriodFrom", r.getLeftPeriodFrom());
        m.put("leftPeriodTo", r.getLeftPeriodTo());
        m.put("rightPeriodFrom", r.getRightPeriodFrom());
        m.put("rightPeriodTo", r.getRightPeriodTo());
        m.put("explanation", r.getExplanation());
        m.put("explanationCodes", r.getExplanationCodes());
        m.put("probableCauses", r.getProbableCauses());
        m.put("toleranceVersion", r.getToleranceVersion());
        m.put("subjectMatchStatus", r.getSubjectMatchStatus());
        m.put("trace", r.getTrace());
        m.put("metadata", r.getMetadata());
        return m;
    }

    private Map<String, Object> pairView(CiReconciliationResult r) {
        if (r == null) {
            return Map.of("available", false);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", true);
        m.put("left", r.getLeftValue());
        m.put("right", r.getRightValue());
        m.put("variancePct", r.getPercentageVariance());
        m.put("outcome", r.getOutcome());
        m.put("confidence", r.getConfidence());
        m.put("reasons", r.getExplanationCodes());
        m.put("explanation", r.getExplanation());
        return m;
    }

    private Map<String, Object> summaryMap(CiCreditEvidenceSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("turnoverEvidence", s.getTurnoverEvidence());
        m.put("obligationEvidence", s.getObligationEvidence());
        m.put("incomeEvidence", s.getIncomeEvidence());
        m.put("taxEvidence", s.getTaxEvidence());
        m.put("sourceQuality", s.getSourceQuality());
        m.put("reconciliationQuality", s.getReconciliationQuality());
        m.put("materialConflicts", s.getMaterialConflicts());
        m.put("dataGaps", s.getDataGaps());
        m.put("evidenceStrengthScore", s.getEvidenceStrengthScore());
        m.put("evidenceStrengthGrade", s.getEvidenceStrengthGrade());
        m.put("methodVersion", s.getMethodVersion());
        m.put("note", "NOT_A_CREDIT_OR_RISK_SCORE");
        return m;
    }

    private Map<String, Object> evidenceSummary(CiReconciliationEvidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("evidenceKind", e.getEvidenceKind());
        m.put("summary", e.getSummary());
        m.put("detail", e.getDetail());
        return m;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token blank — allowing request");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }
    }

    private boolean tenantMatches(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return true;
        }
        return tenantId != null && tenantHeader.equalsIgnoreCase(tenantId.toString());
    }
}
