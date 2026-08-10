package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow rules for XSRC_* + XSRC_TURNOVER_TRIANGULATION.
 * MATCH→PASS, ACCEPTABLE→PASS/WARN, MATERIAL→REFER, CONFLICT→REFER, DI→DI.
 */
@Component
public class CanonicalReconciliationRuleEvaluator {

    public static final String XSRC_TURNOVER_TRIANGULATION = "XSRC_TURNOVER_TRIANGULATION";

    public record RuleEvalResult(
            String ruleId,
            String ruleVersion,
            String outcome,
            Object value,
            Object threshold,
            Map<String, Object> versions,
            Map<String, Object> evidence) {
    }

    public List<RuleEvalResult> evaluateAll(List<CiReconciliationResult> results) {
        List<RuleEvalResult> out = new ArrayList<>();
        Map<String, CiReconciliationResult> byCode = new LinkedHashMap<>();
        if (results != null) {
            for (CiReconciliationResult r : results) {
                byCode.putIfAbsent(r.getReconciliationCode(), r);
            }
        }
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_GST_ITR_TURNOVER));
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_GST_BANK_TURNOVER));
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_ITR_BANK_TURNOVER));
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION));
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_DECLARED_BUREAU_OBLIGATION));
        out.add(evaluatePair(byCode, ReconciliationConstants.XSRC_DECLARED_BANK_OBLIGATION));
        out.add(evaluateTriangulation(byCode));
        return out;
    }

    public RuleEvalResult evaluatePair(Map<String, CiReconciliationResult> byCode, String code) {
        Map<String, Object> versions = frozenVersions();
        CiReconciliationResult r = byCode != null ? byCode.get(code) : null;
        if (r == null) {
            return new RuleEvalResult(code, code + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "NO_RECONCILIATION_RESULT"));
        }
        String ruleOutcome = mapToRuleOutcome(r.getOutcome(), false);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reconciliationOutcome", r.getOutcome());
        evidence.put("variancePct", r.getPercentageVariance());
        evidence.put("severity", r.getSeverity());
        evidence.put("humanReviewRequired", r.isHumanReviewRequired());
        evidence.put("dataStatus", r.getDataStatus());
        return new RuleEvalResult(code, code + "_V1", ruleOutcome,
                r.getPercentageVariance(), r.getToleranceVersion(), versions, evidence);
    }

    public RuleEvalResult evaluateTriangulation(Map<String, CiReconciliationResult> byCode) {
        Map<String, Object> versions = frozenVersions();
        CiReconciliationResult r = byCode != null
                ? byCode.get(ReconciliationConstants.TURNOVER_TRIANGULATION) : null;
        if (r == null) {
            return new RuleEvalResult(XSRC_TURNOVER_TRIANGULATION, XSRC_TURNOVER_TRIANGULATION + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "NO_TRIANGULATION"));
        }
        Object status = r.getMetadata() != null ? r.getMetadata().get("status") : null;
        String ruleOutcome;
        if (status != null) {
            try {
                TriangulationStatus ts = TriangulationStatus.valueOf(String.valueOf(status));
                ruleOutcome = switch (ts) {
                    case STRONG_ALIGNMENT, REASONABLE_ALIGNMENT -> RuleOutcome.PASS.name();
                    case REVIEW_REQUIRED -> RuleOutcome.REFER.name();
                    case MATERIAL_CONFLICT -> RuleOutcome.REFER.name();
                    case DATA_INSUFFICIENT -> RuleOutcome.DATA_INSUFFICIENT.name();
                };
            } catch (Exception e) {
                ruleOutcome = mapToRuleOutcome(r.getOutcome(), true);
            }
        } else {
            ruleOutcome = mapToRuleOutcome(r.getOutcome(), true);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("status", status);
        evidence.put("confidence", r.getConfidence());
        evidence.put("reconciliationOutcome", r.getOutcome());
        return new RuleEvalResult(XSRC_TURNOVER_TRIANGULATION, XSRC_TURNOVER_TRIANGULATION + "_V1",
                ruleOutcome, status, null, versions, evidence);
    }

    /**
     * @param warnOnAcceptable when true, ACCEPTABLE_VARIANCE → WARN; else PASS
     */
    public static String mapToRuleOutcome(String reconciliationOutcome, boolean warnOnAcceptable) {
        if (reconciliationOutcome == null) {
            return RuleOutcome.DATA_INSUFFICIENT.name();
        }
        try {
            ReconciliationOutcome o = ReconciliationOutcome.valueOf(reconciliationOutcome);
            return switch (o) {
                case MATCH -> RuleOutcome.PASS.name();
                case ACCEPTABLE_VARIANCE -> warnOnAcceptable ? "WARN" : RuleOutcome.PASS.name();
                case MATERIAL_VARIANCE, CONFLICT -> RuleOutcome.REFER.name();
                case DATA_INSUFFICIENT, NOT_APPLICABLE, ERROR -> RuleOutcome.DATA_INSUFFICIENT.name();
            };
        } catch (Exception e) {
            return RuleOutcome.DATA_INSUFFICIENT.name();
        }
    }

    public static Map<String, Object> frozenVersions() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("engine", "CanonicalReconciliationRuleEvaluator");
        v.put("periodAlignment", ReconciliationConstants.PERIOD_ALIGNMENT_V1);
        v.put("explanation", ReconciliationConstants.RECON_EXPLANATION_V1);
        v.put("triangulation", ReconciliationConstants.TURNOVER_TRIANGULATION_V1);
        v.put("evidenceStrength", ReconciliationConstants.EVIDENCE_STRENGTH_V1);
        return v;
    }
}
