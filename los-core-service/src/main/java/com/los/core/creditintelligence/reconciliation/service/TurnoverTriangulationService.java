package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.DiscrepancySeverity;
import com.los.core.creditintelligence.reconciliation.domain.ExplanationCode;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationDataStatus;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.SubjectMatchStatus;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import com.los.core.creditintelligence.reconciliation.domain.VarianceMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TURNOVER_TRIANGULATION_V1 — pairwise synthesis, not an average.
 */
@Component
@RequiredArgsConstructor
public class TurnoverTriangulationService {

    private final VarianceCalculator varianceCalculator;
    private final ReconciliationExplanationBuilder explanationBuilder;
    private final CreditIntelligenceProperties properties;

    public record TriangulationInput(
            BigDecimal gst,
            BigDecimal itr,
            BigDecimal bank,
            BigDecimal gstConfidence,
            BigDecimal itrConfidence,
            BigDecimal bankConfidence) {
    }

    public record TriangulationResult(
            TriangulationStatus status,
            BigDecimal confidence,
            BigDecimal gstItrPct,
            BigDecimal gstBankPct,
            BigDecimal itrBankPct,
            Map<String, Object> detail) {
    }

    public TriangulationResult synthesize(TriangulationInput input) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", ReconciliationConstants.TURNOVER_TRIANGULATION_V1);
        if (input == null) {
            detail.put("reason", "NULL_INPUT");
            return new TriangulationResult(
                    TriangulationStatus.DATA_INSUFFICIENT, BigDecimal.ZERO, null, null, null, detail);
        }
        detail.put("gst", input.gst());
        detail.put("itr", input.itr());
        detail.put("bank", input.bank());

        int available = 0;
        if (input.gst() != null) {
            available++;
        }
        if (input.itr() != null) {
            available++;
        }
        if (input.bank() != null) {
            available++;
        }
        if (available < 2) {
            detail.put("reason", "FEWER_THAN_TWO_SOURCES");
            return new TriangulationResult(
                    TriangulationStatus.DATA_INSUFFICIENT, BigDecimal.ZERO, null, null, null, detail);
        }

        var cfg = properties.getReconciliation().getTurnover();
        BigDecimal gstItr = pairwise(input.gst(), input.itr());
        BigDecimal gstBank = pairwise(input.gst(), input.bank());
        BigDecimal itrBank = pairwise(input.itr(), input.bank());
        detail.put("gstItrVariancePct", gstItr);
        detail.put("gstBankVariancePct", gstBank);
        detail.put("itrBankVariancePct", itrBank);

        List<BigDecimal> pcts = new ArrayList<>();
        if (gstItr != null) {
            pcts.add(gstItr);
        }
        if (gstBank != null) {
            pcts.add(gstBank);
        }
        if (itrBank != null) {
            pcts.add(itrBank);
        }

        long matchLike = pcts.stream().filter(p -> p.doubleValue() <= cfg.getWarningPct()).count();
        long materialOrWorse = pcts.stream().filter(p -> p.doubleValue() > cfg.getMaterialPct()).count();
        long materialOnly = pcts.stream()
                .filter(p -> p.doubleValue() > cfg.getWarningPct() && p.doubleValue() <= cfg.getMaterialPct())
                .count();

        TriangulationStatus status;
        if (materialOrWorse >= 2 || (materialOrWorse >= 1 && available == 2 && matchLike == 0)) {
            status = TriangulationStatus.MATERIAL_CONFLICT;
        } else if (materialOrWorse == 1 || materialOnly >= 2) {
            status = TriangulationStatus.REVIEW_REQUIRED;
        } else if (matchLike == pcts.size() && pcts.stream().allMatch(p -> p.doubleValue() <= cfg.getMatchPct())) {
            status = TriangulationStatus.STRONG_ALIGNMENT;
        } else if (matchLike == pcts.size()) {
            status = TriangulationStatus.REASONABLE_ALIGNMENT;
        } else {
            status = TriangulationStatus.REVIEW_REQUIRED;
        }

        BigDecimal conf = BigDecimal.ONE;
        if (input.gstConfidence() != null) {
            conf = conf.min(input.gstConfidence());
        }
        if (input.itrConfidence() != null) {
            conf = conf.min(input.itrConfidence());
        }
        if (input.bankConfidence() != null) {
            conf = conf.min(input.bankConfidence());
        }
        if (available < 3) {
            conf = conf.min(BigDecimal.valueOf(0.7));
        }
        detail.put("status", status.name());
        detail.put("availableSources", available);
        return new TriangulationResult(status, conf, gstItr, gstBank, itrBank, detail);
    }

    public CiReconciliationResult toResult(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            CiReconciliationDefinition def,
            TriangulationInput input) {

        TriangulationResult syn = synthesize(input);
        Instant now = Instant.now();
        ReconciliationOutcome outcome = switch (syn.status()) {
            case STRONG_ALIGNMENT -> ReconciliationOutcome.MATCH;
            case REASONABLE_ALIGNMENT -> ReconciliationOutcome.ACCEPTABLE_VARIANCE;
            case REVIEW_REQUIRED -> ReconciliationOutcome.MATERIAL_VARIANCE;
            case MATERIAL_CONFLICT -> ReconciliationOutcome.CONFLICT;
            case DATA_INSUFFICIENT -> ReconciliationOutcome.DATA_INSUFFICIENT;
        };

        List<ExplanationCode> codes = new ArrayList<>();
        if (syn.status() == TriangulationStatus.DATA_INSUFFICIENT) {
            codes.add(ExplanationCode.MISSING_LEFT_OPERAND);
        } else if (syn.status() == TriangulationStatus.STRONG_ALIGNMENT
                || syn.status() == TriangulationStatus.REASONABLE_ALIGNMENT) {
            codes.add(ExplanationCode.VALUES_ALIGNED);
        } else {
            codes.add(ExplanationCode.MATERIAL_DIFFERENCE);
        }

        BigDecimal maxPct = maxOf(syn.gstItrPct(), syn.gstBankPct(), syn.itrBankPct());
        var explanation = explanationBuilder.build(
                ReconciliationConstants.TURNOVER_TRIANGULATION, outcome,
                input != null ? input.gst() : null,
                input != null ? input.itr() : null,
                maxPct, codes);

        Map<String, Object> meta = new LinkedHashMap<>(syn.detail());
        meta.put("bank", input != null ? input.bank() : null);

        return CiReconciliationResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .evaluationId(evaluationId)
                .reconciliationDefinitionId(def != null ? def.getId() : null)
                .reconciliationCode(ReconciliationConstants.TURNOVER_TRIANGULATION)
                .definitionVersion(def != null ? def.getVersion() : "V1")
                .leftMetricCode("gst.turnover.trailing_12m")
                .leftValue(input != null ? input.gst() : null)
                .rightMetricCode("itr.business.turnover.latest_fy")
                .rightValue(input != null ? input.itr() : null)
                .normalizedLeftValue(input != null ? input.gst() : null)
                .normalizedRightValue(input != null ? input.itr() : null)
                .percentageVariance(maxPct)
                .absoluteVariance(null)
                .varianceDenominatorMethod(VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE.name())
                .outcome(outcome.name())
                .severity(outcome == ReconciliationOutcome.CONFLICT
                        ? DiscrepancySeverity.HIGH.name()
                        : DiscrepancySeverity.MEDIUM.name())
                .explanation(explanation.freeText())
                .explanationCodes(new ArrayList<>(explanation.explanationCodes()))
                .probableCauses(new ArrayList<>(explanation.probableCauses()))
                .evidenceRefs(List.of())
                .evidenceGroupIds(List.of())
                .humanReviewRequired(syn.status() == TriangulationStatus.REVIEW_REQUIRED
                        || syn.status() == TriangulationStatus.MATERIAL_CONFLICT)
                .dataStatus(syn.status() == TriangulationStatus.DATA_INSUFFICIENT
                        ? ReconciliationDataStatus.MISSING_BOTH.name()
                        : ReconciliationDataStatus.COMPLETE.name())
                .subjectMatchStatus(SubjectMatchStatus.UNKNOWN.name())
                .confidence(syn.confidence())
                .toleranceVersion(properties.getReconciliation().getTurnover().getToleranceVersion())
                .executedAt(now)
                .executionDurationMs(0L)
                .trace(syn.detail())
                .metadata(meta)
                .leftSourceRefs(List.of())
                .rightSourceRefs(List.of())
                .build();
    }

    private BigDecimal pairwise(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        return varianceCalculator.calculate(a, b, VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE)
                .percentageVariance();
    }

    private static BigDecimal maxOf(BigDecimal... values) {
        BigDecimal max = null;
        for (BigDecimal v : values) {
            if (v == null) {
                continue;
            }
            if (max == null || v.compareTo(max) > 0) {
                max = v;
            }
        }
        return max;
    }
}
