package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import com.los.core.creditintelligence.tax.domain.ItrForm;
import com.los.core.creditintelligence.tax.repository.CiItrPresumptiveIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.repository.CiItrTaxSummaryRepository;
import com.los.core.creditintelligence.tax.util.ItrFormNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves left/right operands from CiMetricResult, underwriting facts, or tax summaries.
 */
@Component
@RequiredArgsConstructor
public class OperandResolver {

    private final CiMetricResultRepository metricResultRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final CiItrReturnRepository itrReturnRepository;
    private final CiItrPresumptiveIncomeRepository presumptiveRepository;
    private final CiItrTaxSummaryRepository taxSummaryRepository;

    public record ResolvedOperand(
            boolean present,
            BigDecimal value,
            String metricCode,
            String metricVersion,
            String factPath,
            LocalDate periodFrom,
            LocalDate periodTo,
            BigDecimal completeness,
            BigDecimal confidence,
            List<Object> sourceRefs,
            Map<String, Object> evidence,
            boolean presumptiveItr,
            String dataQualityStatus) {
    }

    public ResolvedOperand resolve(
            UUID applicationId,
            UUID factSnapshotId,
            Map<String, Object> operandDef,
            LocalDate asOf) {

        if (operandDef == null || operandDef.isEmpty()) {
            return missing(null);
        }

        // Synthesis / special roles handled by callers
        if (operandDef.containsKey("synthesis") || operandDef.containsKey("inputs")) {
            return missing(null);
        }

        String role = stringVal(operandDef.get("role"));
        if ("LEFT_FROM_EVIDENCE".equals(role) || "RIGHT_FROM_EVIDENCE".equals(role)) {
            return resolveFromLegacyVarianceEvidence(applicationId, operandDef, role);
        }

        String metricCode = stringVal(operandDef.get("metricCode"));
        if (metricCode != null) {
            ResolvedOperand fromMetric = resolveMetric(applicationId, metricCode, asOf);
            if (fromMetric.present()) {
                return enrichPresumptive(applicationId, fromMetric);
            }
            Object fallback = operandDef.get("fallback");
            if (fallback instanceof List<?> fb) {
                for (Object o : fb) {
                    if (o != null) {
                        ResolvedOperand alt = resolveMetric(applicationId, String.valueOf(o), asOf);
                        if (alt.present()) {
                            return enrichPresumptive(applicationId, alt);
                        }
                    }
                }
            }
            // Special: itr.tax.tds from tax summary when metric absent
            if ("itr.tax.tds".equals(metricCode)) {
                ResolvedOperand tds = resolveItrTds(applicationId);
                if (tds.present()) {
                    return tds;
                }
            }
            return missing(metricCode);
        }

        String factPath = stringVal(operandDef.get("factPath"));
        if (factPath != null) {
            ResolvedOperand fromFact = resolveFact(applicationId, factSnapshotId, factPath);
            if (fromFact.present()) {
                return fromFact;
            }
            Object fallbacks = operandDef.get("fallbackFactPaths");
            if (fallbacks instanceof List<?> fb) {
                for (Object o : fb) {
                    if (o != null) {
                        ResolvedOperand alt = resolveFact(applicationId, factSnapshotId, String.valueOf(o));
                        if (alt.present()) {
                            return alt;
                        }
                    }
                }
            }
            return missingFact(factPath);
        }

        return missing(null);
    }

    private ResolvedOperand resolveMetric(UUID applicationId, String metricCode, LocalDate asOf) {
        Optional<CiMetricResult> opt =
                metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, metricCode);
        if (opt.isEmpty()) {
            return missing(metricCode);
        }
        CiMetricResult m = opt.get();
        BigDecimal value = MetricValueExtractor.extract(m.getValue());
        // For variance metrics, primary value is %, not amount — still "present"
        boolean di = m.getOutcome() != null && m.getOutcome().contains("DATA_INSUFFICIENT");
        if (value == null && di) {
            return missingMetric(metricCode, m);
        }
        PeriodAlignmentService.PeriodWindow window = inferPeriod(m, metricCode, asOf);
        BigDecimal completeness = completenessFrom(m);
        BigDecimal confidence = confidenceFrom(m);
        List<Object> refs = new ArrayList<>();
        if (m.getSourceRecordIds() != null) {
            refs.addAll(m.getSourceRecordIds());
        }
        Map<String, Object> evidence = m.getEvidence() != null ? new LinkedHashMap<>(m.getEvidence()) : Map.of();
        return new ResolvedOperand(
                value != null || !di,
                value,
                m.getMetricCode(),
                m.getMetricVersion(),
                null,
                window != null ? window.from() : null,
                window != null ? window.to() : null,
                completeness,
                confidence,
                refs,
                evidence,
                false,
                m.getDataQualityStatus());
    }

    private ResolvedOperand resolveFromLegacyVarianceEvidence(
            UUID applicationId, Map<String, Object> operandDef, String role) {
        String metricCode = stringVal(operandDef.get("metricCode"));
        Optional<CiMetricResult> opt =
                metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, metricCode);
        if (opt.isEmpty()) {
            return missing(metricCode);
        }
        CiMetricResult m = opt.get();
        Map<String, Object> value = m.getValue() != null ? m.getValue() : Map.of();
        Map<String, Object> evidence = m.getEvidence() != null ? m.getEvidence() : Map.of();
        String key = "LEFT_FROM_EVIDENCE".equals(role) ? "gstr1Sum" : "gstr3bSum";
        BigDecimal amount = MetricValueExtractor.extractOrNull(value.get(key));
        if (amount == null) {
            amount = MetricValueExtractor.extractOrNull(evidence.get(key));
        }
        // C4 variance uses left/right in evidence
        if (amount == null) {
            String lr = "LEFT_FROM_EVIDENCE".equals(role) ? "left" : "right";
            amount = MetricValueExtractor.extractOrNull(evidence.get(lr));
        }
        PeriodAlignmentService.PeriodWindow window = inferPeriod(m, metricCode, LocalDate.now());
        return new ResolvedOperand(
                amount != null,
                amount,
                m.getMetricCode(),
                m.getMetricVersion(),
                null,
                window != null ? window.from() : null,
                window != null ? window.to() : null,
                completenessFrom(m),
                confidenceFrom(m),
                m.getSourceRecordIds() != null ? new ArrayList<>(m.getSourceRecordIds()) : List.of(),
                evidence,
                false,
                m.getDataQualityStatus());
    }

    private ResolvedOperand resolveFact(UUID applicationId, UUID factSnapshotId, String factPath) {
        if (factSnapshotId == null) {
            return missingFact(factPath);
        }
        CiUnderwritingFact fact = factRepository.findBySnapshotId(factSnapshotId).stream()
                .filter(f -> factPath.equals(f.getCanonicalPath()))
                .findFirst()
                .orElse(null);
        if (fact == null) {
            return missingFact(factPath);
        }
        BigDecimal value = MetricValueExtractor.extractOrNull(fact.getValue());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("classification", fact.getClassification());
        return new ResolvedOperand(
                value != null,
                value,
                null,
                null,
                factPath,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.valueOf(0.8),
                fact.getSourceRecordIds() != null
                        ? fact.getSourceRecordIds().stream().map(u -> (Object) u).toList()
                        : List.of(),
                meta,
                false,
                fact.getQualityStatus());
    }

    /** Resolve from an in-memory fact map (tests / scorecard compat). */
    public ResolvedOperand resolveFromValueMap(
            String codeOrPath, BigDecimal value, LocalDate from, LocalDate to,
            BigDecimal completeness, BigDecimal confidence) {
        return new ResolvedOperand(
                value != null, value, codeOrPath, "V1", codeOrPath, from, to,
                completeness != null ? completeness : BigDecimal.ONE,
                confidence != null ? confidence : BigDecimal.ONE,
                List.of(), Map.of(), false, "OK");
    }

    private ResolvedOperand resolveItrTds(UUID applicationId) {
        List<CiItrReturn> returns =
                itrReturnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
        for (CiItrReturn r : returns) {
            Optional<CiItrTaxSummary> tax = taxSummaryRepository.findByItrReturnId(r.getId());
            if (tax.isPresent() && tax.get().getTds() != null) {
                PeriodAlignmentService.PeriodWindow fy = fyWindow(r);
                return new ResolvedOperand(
                        true, tax.get().getTds(), "itr.tax.tds", "V1", null,
                        fy != null ? fy.from() : null, fy != null ? fy.to() : null,
                        BigDecimal.ONE, BigDecimal.valueOf(0.85),
                        List.of(r.getId()), Map.of("itrReturnId", r.getId().toString()),
                        isPresumptive(r), "OK");
            }
        }
        return missing("itr.tax.tds");
    }

    private ResolvedOperand enrichPresumptive(UUID applicationId, ResolvedOperand op) {
        if (op.metricCode() == null || !op.metricCode().startsWith("itr.")) {
            return op;
        }
        List<CiItrReturn> returns =
                itrReturnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId);
        if (returns.isEmpty()) {
            return op;
        }
        CiItrReturn latest = returns.get(0);
        boolean presumptive = isPresumptive(latest);
        if (!presumptive) {
            return op;
        }
        return new ResolvedOperand(
                op.present(), op.value(), op.metricCode(), op.metricVersion(), op.factPath(),
                op.periodFrom(), op.periodTo(), op.completeness(), op.confidence(),
                op.sourceRefs(), op.evidence(), true, op.dataQualityStatus());
    }

    private boolean isPresumptive(CiItrReturn r) {
        if (r == null) {
            return false;
        }
        ItrForm form = ItrFormNormalizer.normalize(r.getItrForm());
        if (ItrFormNormalizer.isPresumptiveForm(form)) {
            return true;
        }
        List<CiItrPresumptiveIncome> rows = presumptiveRepository.findByItrReturnId(r.getId());
        return rows != null && !rows.isEmpty();
    }

    private static PeriodAlignmentService.PeriodWindow inferPeriod(
            CiMetricResult m, String metricCode, LocalDate asOf) {
        Map<String, Object> evidence = m.getEvidence() != null ? m.getEvidence() : Map.of();
        LocalDate from = parseDate(evidence.get("periodFrom"));
        LocalDate to = parseDate(evidence.get("periodTo"));
        if (from != null && to != null) {
            return new PeriodAlignmentService.PeriodWindow(from, to);
        }
        if (metricCode != null) {
            if (metricCode.contains("trailing_12m") || metricCode.contains("credits_12m")) {
                return PeriodAlignmentService.trailing12(asOf);
            }
            if (metricCode.contains("credits_6m")) {
                LocalDate end = asOf != null ? asOf : LocalDate.now();
                return new PeriodAlignmentService.PeriodWindow(end.minusMonths(5).withDayOfMonth(1), end);
            }
            if (metricCode.contains("latest_fy") || metricCode.contains("itr.")) {
                int year = asOf != null ? PeriodAlignmentService.fiscalYearEndYear(asOf) : LocalDate.now().getYear();
                return PeriodAlignmentService.financialYearEnding(year);
            }
            if (metricCode.contains("obligation") || metricCode.contains("monthly")) {
                LocalDate d = asOf != null ? asOf : LocalDate.now();
                return new PeriodAlignmentService.PeriodWindow(d.withDayOfMonth(1), d);
            }
        }
        return PeriodAlignmentService.trailing12(asOf);
    }

    private static PeriodAlignmentService.PeriodWindow fyWindow(CiItrReturn r) {
        if (r.getFinancialYear() != null && r.getFinancialYear().contains("-")) {
            try {
                String[] parts = r.getFinancialYear().split("-");
                int start = Integer.parseInt(parts[0].trim());
                return PeriodAlignmentService.financialYearEnding(start + 1);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static BigDecimal completenessFrom(CiMetricResult m) {
        Map<String, Object> evidence = m.getEvidence() != null ? m.getEvidence() : Map.of();
        Object c = evidence.get("completeness");
        if (c == null) {
            c = evidence.get("periodCompleteness");
        }
        BigDecimal bd = MetricValueExtractor.extractOrNull(c);
        if (bd != null) {
            return bd;
        }
        if (m.getOutcome() != null && m.getOutcome().contains("DATA_INSUFFICIENT")) {
            return BigDecimal.valueOf(0.2);
        }
        return BigDecimal.valueOf(0.9);
    }

    private static BigDecimal confidenceFrom(CiMetricResult m) {
        Map<String, Object> evidence = m.getEvidence() != null ? m.getEvidence() : Map.of();
        Object c = evidence.get("confidence");
        BigDecimal bd = MetricValueExtractor.extractOrNull(c);
        if (bd != null) {
            return bd;
        }
        return BigDecimal.valueOf(0.85);
    }

    private static LocalDate parseDate(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static ResolvedOperand missing(String metricCode) {
        return new ResolvedOperand(
                false, null, metricCode, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), Map.of(), false, "MISSING");
    }

    private static ResolvedOperand missingFact(String factPath) {
        return new ResolvedOperand(
                false, null, null, null, factPath, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), Map.of(), false, "MISSING");
    }

    private static ResolvedOperand missingMetric(String metricCode, CiMetricResult m) {
        return new ResolvedOperand(
                false, null, metricCode, m.getMetricVersion(), null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO,
                m.getSourceRecordIds() != null ? new ArrayList<>(m.getSourceRecordIds()) : List.of(),
                m.getEvidence() != null ? m.getEvidence() : Map.of(),
                false, m.getDataQualityStatus());
    }
}
