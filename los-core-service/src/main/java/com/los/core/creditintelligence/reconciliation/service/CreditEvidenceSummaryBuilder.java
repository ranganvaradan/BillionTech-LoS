package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreditEvidenceSummaryBuilder {

    private final EvidenceStrengthScorer evidenceStrengthScorer;

    public CiCreditEvidenceSummary build(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            List<CiReconciliationResult> results,
            TurnoverTriangulationService.TriangulationResult triangulation,
            boolean bureauAvailable,
            boolean gstAvailable,
            boolean bankAvailable,
            boolean itrAvailable,
            boolean aisAvailable,
            boolean form26AsAvailable) {

        Map<String, CiReconciliationResult> byCode = indexLatest(results);

        Map<String, Object> turnover = new LinkedHashMap<>();
        CiReconciliationResult gstItr = byCode.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER);
        CiReconciliationResult gstBank = byCode.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER);
        CiReconciliationResult itrBank = byCode.get(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER);
        CiReconciliationResult tri = byCode.get(ReconciliationConstants.TURNOVER_TRIANGULATION);

        BigDecimal gst = firstNonNull(
                gstItr != null ? gstItr.getLeftValue() : null,
                gstBank != null ? gstBank.getLeftValue() : null);
        BigDecimal itr = firstNonNull(
                gstItr != null ? gstItr.getRightValue() : null,
                itrBank != null ? itrBank.getLeftValue() : null);
        BigDecimal bank = firstNonNull(
                gstBank != null ? gstBank.getRightValue() : null,
                itrBank != null ? itrBank.getRightValue() : null);

        turnover.put("gst", gst);
        turnover.put("itr", itr);
        turnover.put("bank", bank);
        if (triangulation != null) {
            turnover.put("alignment", triangulation.status().name());
            turnover.put("confidence", triangulation.confidence());
            turnover.put("gstItrVariancePct", triangulation.gstItrPct());
            turnover.put("gstBankVariancePct", triangulation.gstBankPct());
            turnover.put("itrBankVariancePct", triangulation.itrBankPct());
        } else if (tri != null && tri.getMetadata() != null) {
            turnover.put("alignment", tri.getMetadata().get("status"));
            turnover.put("confidence", tri.getConfidence());
        }

        Map<String, Object> obligation = new LinkedHashMap<>();
        putObligation(obligation, "bureauBank", byCode.get(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION));
        putObligation(obligation, "declaredBureau", byCode.get(ReconciliationConstants.XSRC_DECLARED_BUREAU_OBLIGATION));
        putObligation(obligation, "declaredBank", byCode.get(ReconciliationConstants.XSRC_DECLARED_BANK_OBLIGATION));

        Map<String, Object> income = new LinkedHashMap<>();
        CiReconciliationResult ais = byCode.get(ReconciliationConstants.XSRC_ITR_AIS_INCOME);
        if (ais != null) {
            income.put("outcome", ais.getOutcome());
            income.put("variancePct", ais.getPercentageVariance());
            income.put("left", ais.getLeftValue());
            income.put("right", ais.getRightValue());
        }

        Map<String, Object> tax = new LinkedHashMap<>();
        CiReconciliationResult tds = byCode.get(ReconciliationConstants.XSRC_ITR_26AS_TDS);
        if (tds != null) {
            tax.put("outcome", tds.getOutcome());
            tax.put("variancePct", tds.getPercentageVariance());
        }

        Map<String, Object> sourceQuality = new LinkedHashMap<>();
        sourceQuality.put("bureau", bureauAvailable);
        sourceQuality.put("gst", gstAvailable);
        sourceQuality.put("bank", bankAvailable);
        sourceQuality.put("itr", itrAvailable);
        sourceQuality.put("ais", aisAvailable);
        sourceQuality.put("form26as", form26AsAvailable);

        List<Object> conflicts = new ArrayList<>();
        List<Object> gaps = new ArrayList<>();
        int conflictCount = 0;
        if (results != null) {
            for (CiReconciliationResult r : results) {
                if (ReconciliationOutcome.CONFLICT.name().equals(r.getOutcome())
                        || ReconciliationOutcome.MATERIAL_VARIANCE.name().equals(r.getOutcome())) {
                    conflicts.add(Map.of(
                            "code", r.getReconciliationCode(),
                            "outcome", r.getOutcome(),
                            "variancePct", r.getPercentageVariance() != null ? r.getPercentageVariance() : ""));
                    conflictCount++;
                }
                if (ReconciliationOutcome.DATA_INSUFFICIENT.name().equals(r.getOutcome())) {
                    gaps.add(Map.of("code", r.getReconciliationCode(), "dataStatus", r.getDataStatus()));
                }
            }
        }
        if (!aisAvailable) {
            gaps.add("AIS unavailable");
        }
        if (!form26AsAvailable) {
            gaps.add("Form 26AS unavailable");
        }

        TriangulationStatus triStatus = triangulation != null
                ? triangulation.status()
                : TriangulationStatus.DATA_INSUFFICIENT;

        EvidenceStrengthScorer.StrengthResult strength = evidenceStrengthScorer.scoreFromResults(
                bureauAvailable, gstAvailable, bankAvailable, itrAvailable, aisAvailable, form26AsAvailable,
                results, triStatus);

        Map<String, Object> reconQuality = new LinkedHashMap<>();
        reconQuality.put("resultCount", results != null ? results.size() : 0);
        reconQuality.put("materialConflictCount", conflictCount);
        reconQuality.put("evidenceStrengthComponents", strength.components());

        return CiCreditEvidenceSummary.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .evaluationId(evaluationId)
                .turnoverEvidence(turnover)
                .obligationEvidence(obligation)
                .incomeEvidence(income)
                .taxEvidence(tax)
                .sourceQuality(sourceQuality)
                .reconciliationQuality(reconQuality)
                .materialConflicts(conflicts)
                .dataGaps(gaps)
                .evidenceStrengthScore(strength.score())
                .evidenceStrengthGrade(strength.grade().name())
                .methodVersion(ReconciliationConstants.CREDIT_EVIDENCE_SUMMARY_V1)
                .metadata(Map.of(
                        "notCreditRiskScore", true,
                        "evidenceStrengthMethod", strength.methodVersion()))
                .build();
    }

    private static void putObligation(Map<String, Object> map, String key, CiReconciliationResult r) {
        if (r == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("left", r.getLeftValue());
        row.put("right", r.getRightValue());
        row.put("alignment", r.getOutcome());
        row.put("variancePct", r.getPercentageVariance());
        map.put(key, row);
        if ("bureauBank".equals(key)) {
            map.put("bureauMonthly", r.getLeftValue());
            map.put("bankDetectedMonthly", r.getRightValue());
            map.put("alignment", r.getOutcome());
        }
    }

    private static Map<String, CiReconciliationResult> indexLatest(List<CiReconciliationResult> results) {
        Map<String, CiReconciliationResult> map = new LinkedHashMap<>();
        if (results == null) {
            return map;
        }
        for (CiReconciliationResult r : results) {
            map.putIfAbsent(r.getReconciliationCode(), r);
        }
        return map;
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}
