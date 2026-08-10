package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationEvidence;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationDefinitionStatus;
import com.los.core.creditintelligence.reconciliation.domain.SubjectMatchStatus;
import com.los.core.creditintelligence.reconciliation.repository.CiCreditEvidenceSummaryRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationDefinitionRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationEvidenceRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs ACTIVE reconciliation catalogue for an application; selective re-run by dependency_metric_codes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationOrchestrator {

    private final CiReconciliationDefinitionRepository definitionRepository;
    private final CiReconciliationResultRepository resultRepository;
    private final CiReconciliationEvidenceRepository evidenceRepository;
    private final CiCreditEvidenceSummaryRepository evidenceSummaryRepository;
    private final OperandResolver operandResolver;
    private final ReconciliationEvaluator evaluator;
    private final LegacyReconciliationBridge legacyBridge;
    private final TurnoverTriangulationService triangulationService;
    private final CreditEvidenceSummaryBuilder evidenceSummaryBuilder;
    private final CiMetricResultRepository metricResultRepository;
    private final CiItrReturnRepository itrReturnRepository;
    private final CiAisSummaryRepository aisSummaryRepository;
    private final CiForm26AsSummaryRepository form26AsSummaryRepository;

    public record OrchestrationResult(
            List<CiReconciliationResult> results,
            CiCreditEvidenceSummary evidenceSummary,
            TurnoverTriangulationService.TriangulationResult triangulation) {
    }

    @Transactional
    public OrchestrationResult run(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            Set<String> changedMetricCodes,
            LocalDate asOf) {

        LocalDate date = asOf != null ? asOf : LocalDate.now();
        List<CiReconciliationDefinition> defs =
                definitionRepository.findByStatusOrderByReconciliationCodeAsc(
                        ReconciliationDefinitionStatus.ACTIVE.name());

        // In-memory catalogue fallback for unit tests / pre-migration
        if (defs == null || defs.isEmpty()) {
            defs = CatalogueDefaults.activeDefinitions();
        }

        List<CiReconciliationDefinition> selected = new ArrayList<>();
        for (CiReconciliationDefinition def : defs) {
            if (ReconciliationConstants.TURNOVER_TRIANGULATION.equals(def.getReconciliationCode())) {
                continue; // run after pairwise
            }
            if (changedMetricCodes != null && !changedMetricCodes.isEmpty()) {
                if (!dependsOn(def, changedMetricCodes)) {
                    continue;
                }
            }
            selected.add(def);
        }

        List<CiReconciliationResult> results = new ArrayList<>();
        Map<String, BigDecimal> turnoverValues = new LinkedHashMap<>();
        Map<String, BigDecimal> turnoverConfidence = new LinkedHashMap<>();

        for (CiReconciliationDefinition def : selected) {
            try {
                CiReconciliationResult result;
                if (LegacyReconciliationBridge.isLegacyCode(def.getReconciliationCode())) {
                    result = legacyBridge.wrapIfLegacy(
                                    tenantId, applicationId, factSnapshotId, evaluationId, def)
                            .orElseGet(() -> evaluator.evaluate(
                                    tenantId, applicationId, factSnapshotId, evaluationId, def,
                                    operandResolver.resolve(applicationId, factSnapshotId,
                                            def.getLeftOperandDefinition(), date),
                                    operandResolver.resolve(applicationId, factSnapshotId,
                                            def.getRightOperandDefinition(), date),
                                    SubjectMatchStatus.UNKNOWN, date));
                } else {
                    OperandResolver.ResolvedOperand left = operandResolver.resolve(
                            applicationId, factSnapshotId, def.getLeftOperandDefinition(), date);
                    OperandResolver.ResolvedOperand right = operandResolver.resolve(
                            applicationId, factSnapshotId, def.getRightOperandDefinition(), date);
                    result = evaluator.evaluate(
                            tenantId, applicationId, factSnapshotId, evaluationId, def,
                            left, right, SubjectMatchStatus.UNKNOWN, date);
                }
                result = resultRepository.save(result);
                persistEvidence(result);
                results.add(result);
                captureTurnover(result, turnoverValues, turnoverConfidence);
            } catch (Exception e) {
                log.warn("Reconciliation {} failed for {}: {}",
                        def.getReconciliationCode(), applicationId, e.getMessage());
            }
        }

        // Fill turnover from metrics if pairwise missed
        fillTurnoverFromMetrics(applicationId, turnoverValues, turnoverConfidence);

        CiReconciliationDefinition triDef = defs.stream()
                .filter(d -> ReconciliationConstants.TURNOVER_TRIANGULATION.equals(d.getReconciliationCode()))
                .findFirst()
                .orElse(CatalogueDefaults.triangulationDefinition());

        boolean runTri = changedMetricCodes == null || changedMetricCodes.isEmpty()
                || dependsOn(triDef, changedMetricCodes)
                || selected.stream().anyMatch(d -> d.getCategory() != null
                && "TURNOVER".equalsIgnoreCase(d.getCategory()));

        TurnoverTriangulationService.TriangulationResult triangulation = null;
        if (runTri) {
            TurnoverTriangulationService.TriangulationInput input =
                    new TurnoverTriangulationService.TriangulationInput(
                            turnoverValues.get("gst"),
                            turnoverValues.get("itr"),
                            turnoverValues.get("bank"),
                            turnoverConfidence.getOrDefault("gst", BigDecimal.valueOf(0.85)),
                            turnoverConfidence.getOrDefault("itr", BigDecimal.valueOf(0.85)),
                            turnoverConfidence.getOrDefault("bank", BigDecimal.valueOf(0.85)));
            triangulation = triangulationService.synthesize(input);
            CiReconciliationResult triResult = triangulationService.toResult(
                    tenantId, applicationId, factSnapshotId, evaluationId, triDef, input);
            triResult = resultRepository.save(triResult);
            persistEvidence(triResult);
            results.add(triResult);
        }

        boolean bureau = hasMetric(applicationId, BureauMetricService.TOTAL_MONTHLY_OBLIGATION);
        boolean gst = hasMetric(applicationId, GstMetricService.TRAILING_12M);
        boolean bank = hasMetric(applicationId, BankingMetricService.ADJ_12M)
                || hasMetric(applicationId, BankingMetricService.ADJ_6M);
        boolean itr = hasMetric(applicationId, TaxMetricService.TURNOVER_LATEST);
        boolean ais = !aisSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).isEmpty();
        boolean form26 = !form26AsSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(applicationId).isEmpty();
        if (!itr) {
            itr = !itrReturnRepository
                    .findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(applicationId).isEmpty();
        }

        CiCreditEvidenceSummary summary = evidenceSummaryBuilder.build(
                tenantId, applicationId, factSnapshotId, evaluationId,
                results, triangulation,
                bureau, gst, bank, itr, ais, form26);
        summary = evidenceSummaryRepository.save(summary);

        return new OrchestrationResult(results, summary, triangulation);
    }

    private void persistEvidence(CiReconciliationResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("outcome", result.getOutcome());
        summary.put("percentageVariance", result.getPercentageVariance());
        summary.put("left", result.getLeftValue());
        summary.put("right", result.getRightValue());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("explanationCodes", result.getExplanationCodes());
        detail.put("trace", result.getTrace());
        evidenceRepository.save(CiReconciliationEvidence.builder()
                .tenantId(result.getTenantId())
                .applicationId(result.getApplicationId())
                .reconciliationResultId(result.getId())
                .evidenceKind("RECONCILIATION_SUMMARY")
                .summary(summary)
                .detail(detail)
                .build());
    }

    private static boolean dependsOn(CiReconciliationDefinition def, Set<String> changed) {
        if (def.getDependencyMetricCodes() == null || def.getDependencyMetricCodes().isEmpty()) {
            return true;
        }
        for (Object o : def.getDependencyMetricCodes()) {
            if (o != null && changed.contains(String.valueOf(o))) {
                return true;
            }
        }
        return false;
    }

    private void captureTurnover(
            CiReconciliationResult result,
            Map<String, BigDecimal> values,
            Map<String, BigDecimal> confidence) {
        String code = result.getReconciliationCode();
        if (ReconciliationConstants.XSRC_GST_ITR_TURNOVER.equals(code)) {
            if (result.getLeftValue() != null) {
                values.putIfAbsent("gst", result.getLeftValue());
            }
            if (result.getRightValue() != null) {
                values.putIfAbsent("itr", result.getRightValue());
            }
        } else if (ReconciliationConstants.XSRC_GST_BANK_TURNOVER.equals(code)) {
            if (result.getLeftValue() != null) {
                values.putIfAbsent("gst", result.getLeftValue());
            }
            if (result.getRightValue() != null) {
                values.putIfAbsent("bank", result.getRightValue());
            }
        } else if (ReconciliationConstants.XSRC_ITR_BANK_TURNOVER.equals(code)) {
            if (result.getLeftValue() != null) {
                values.putIfAbsent("itr", result.getLeftValue());
            }
            if (result.getRightValue() != null) {
                values.putIfAbsent("bank", result.getRightValue());
            }
        }
        if (result.getLeftConfidence() != null && result.getLeftMetricCode() != null) {
            if (result.getLeftMetricCode().startsWith("gst.")) {
                confidence.putIfAbsent("gst", result.getLeftConfidence());
            }
            if (result.getLeftMetricCode().startsWith("itr.")) {
                confidence.putIfAbsent("itr", result.getLeftConfidence());
            }
        }
        if (result.getRightConfidence() != null && result.getRightMetricCode() != null) {
            if (result.getRightMetricCode().startsWith("banking.")) {
                confidence.putIfAbsent("bank", result.getRightConfidence());
            }
            if (result.getRightMetricCode().startsWith("itr.")) {
                confidence.putIfAbsent("itr", result.getRightConfidence());
            }
        }
    }

    private void fillTurnoverFromMetrics(
            UUID applicationId,
            Map<String, BigDecimal> values,
            Map<String, BigDecimal> confidence) {
        putMetric(applicationId, GstMetricService.TRAILING_12M, "gst", values, confidence);
        putMetric(applicationId, TaxMetricService.TURNOVER_LATEST, "itr", values, confidence);
        if (!values.containsKey("bank")) {
            putMetric(applicationId, BankingMetricService.ADJ_12M, "bank", values, confidence);
            if (!values.containsKey("bank")) {
                putMetric(applicationId, BankingMetricService.ADJ_6M, "bank", values, confidence);
            }
        }
    }

    private void putMetric(
            UUID applicationId, String metricCode, String key,
            Map<String, BigDecimal> values, Map<String, BigDecimal> confidence) {
        if (values.containsKey(key)) {
            return;
        }
        metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, metricCode)
                .ifPresent(m -> {
                    BigDecimal v = MetricValueExtractor.extract(m.getValue());
                    if (v != null) {
                        values.put(key, v);
                        confidence.putIfAbsent(key, BigDecimal.valueOf(0.85));
                    }
                });
    }

    private boolean hasMetric(UUID applicationId, String code) {
        return metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, code)
                .isPresent();
    }

    /** In-memory ACTIVE definitions when DB catalogue not loaded (tests). */
    public static final class CatalogueDefaults {
        private CatalogueDefaults() {
        }

        public static List<CiReconciliationDefinition> activeDefinitions() {
            List<CiReconciliationDefinition> list = new ArrayList<>();
            list.add(def(ReconciliationConstants.XSRC_GST_ITR_TURNOVER, "TURNOVER",
                    Map.of("metricCode", "gst.turnover.trailing_12m"),
                    Map.of("metricCode", "itr.business.turnover.latest_fy"),
                    "COMMON_OVERLAP", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("gst.turnover.trailing_12m", "itr.business.turnover.latest_fy"),
                    "turnover", 5, 15));
            list.add(def(ReconciliationConstants.XSRC_GST_BANK_TURNOVER, "TURNOVER",
                    Map.of("metricCode", "gst.turnover.trailing_12m"),
                    Map.of("metricCode", "banking.adjusted_business_credits_12m",
                            "fallback", List.of("banking.adjusted_business_credits_6m")),
                    "COMMON_OVERLAP", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("gst.turnover.trailing_12m", "banking.adjusted_business_credits_12m"),
                    "turnover", 5, 15));
            list.add(def(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER, "TURNOVER",
                    Map.of("metricCode", "itr.business.turnover.latest_fy"),
                    Map.of("metricCode", "banking.adjusted_business_credits_12m",
                            "fallback", List.of("banking.adjusted_business_credits_6m")),
                    "FINANCIAL_YEAR", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("itr.business.turnover.latest_fy", "banking.adjusted_business_credits_12m"),
                    "turnover", 5, 15));
            list.add(def(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION, "OBLIGATION",
                    Map.of("metricCode", "bureau.total_monthly_obligation"),
                    Map.of("metricCode", "banking.monthly_obligation"),
                    "EXACT_PERIOD", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("bureau.total_monthly_obligation", "banking.monthly_obligation"),
                    "obligation", 5, 20));
            list.add(def(ReconciliationConstants.XSRC_DECLARED_BUREAU_OBLIGATION, "OBLIGATION",
                    Map.of("factPath", "compat.EMI_OBLIGATION",
                            "fallbackFactPaths", List.of("compat.MONTHLY_OBLIGATION")),
                    Map.of("metricCode", "bureau.total_monthly_obligation"),
                    "EXACT_PERIOD", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("bureau.total_monthly_obligation"),
                    "obligation", 5, 20));
            list.add(def(ReconciliationConstants.XSRC_DECLARED_BANK_OBLIGATION, "OBLIGATION",
                    Map.of("factPath", "compat.EMI_OBLIGATION",
                            "fallbackFactPaths", List.of("compat.MONTHLY_OBLIGATION")),
                    Map.of("metricCode", "banking.monthly_obligation"),
                    "EXACT_PERIOD", "SYMMETRIC_PERCENT_DIFFERENCE",
                    List.of("banking.monthly_obligation"),
                    "obligation", 5, 20));
            list.add(legacyDef(ReconciliationConstants.XSRC_ITR_AIS_INCOME, "INCOME",
                    TaxMetricService.XSRC_AIS_INCOME, "C4", "tax", 10, 25));
            list.add(legacyDef(ReconciliationConstants.XSRC_ITR_26AS_TDS, "TAX",
                    TaxMetricService.XSRC_26AS_TDS, "C4", "tax", 10, 25));
            list.add(legacyDef(ReconciliationConstants.XSRC_GSTR1_GSTR3B_TURNOVER, "TURNOVER",
                    GstMetricService.VARIANCE, "C2", "turnover", 5, 15));
            list.add(triangulationDefinition());
            return list;
        }

        public static CiReconciliationDefinition triangulationDefinition() {
            return CiReconciliationDefinition.builder()
                    .reconciliationCode(ReconciliationConstants.TURNOVER_TRIANGULATION)
                    .version("V1")
                    .name("Three-way GST/ITR/Bank turnover synthesis")
                    .category("TURNOVER")
                    .leftOperandDefinition(Map.of("inputs", List.of(
                            "gst.turnover.trailing_12m",
                            "itr.business.turnover.latest_fy",
                            "banking.adjusted_business_credits_12m")))
                    .rightOperandDefinition(Map.of("synthesis", ReconciliationConstants.TURNOVER_TRIANGULATION_V1))
                    .periodAlignmentStrategy("COMMON_OVERLAP")
                    .normalizationStrategy("ANNUALIZE_IF_NEEDED")
                    .varianceMethod("SYMMETRIC_PERCENT_DIFFERENCE")
                    .warningTolerance(BigDecimal.valueOf(5))
                    .materialTolerance(BigDecimal.valueOf(15))
                    .dependencyMetricCodes(List.of(
                            "gst.turnover.trailing_12m",
                            "itr.business.turnover.latest_fy",
                            "banking.adjusted_business_credits_12m"))
                    .status(ReconciliationDefinitionStatus.ACTIVE.name())
                    .metadata(Map.of("method", ReconciliationConstants.TURNOVER_TRIANGULATION_V1))
                    .missingDataPolicy("DATA_INSUFFICIENT")
                    .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                    .allowedDataStatuses(List.of())
                    .build();
        }

        private static CiReconciliationDefinition def(
                String code, String category,
                Map<String, Object> left, Map<String, Object> right,
                String alignment, String variance, List<Object> deps,
                String profile, double warn, double material) {
            return CiReconciliationDefinition.builder()
                    .reconciliationCode(code)
                    .version("V1")
                    .name(code)
                    .category(category)
                    .leftOperandDefinition(left)
                    .rightOperandDefinition(right)
                    .periodAlignmentStrategy(alignment)
                    .normalizationStrategy("NONE")
                    .varianceMethod(variance)
                    .warningTolerance(BigDecimal.valueOf(warn))
                    .materialTolerance(BigDecimal.valueOf(material))
                    .dependencyMetricCodes(new ArrayList<>(deps))
                    .status(ReconciliationDefinitionStatus.ACTIVE.name())
                    .metadata(Map.of("toleranceProfile", profile))
                    .missingDataPolicy("DATA_INSUFFICIENT")
                    .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                    .allowedDataStatuses(List.of())
                    .minimumCompleteness(BigDecimal.valueOf(0.5))
                    .minimumConfidence(BigDecimal.valueOf(0.4))
                    .build();
        }

        private static CiReconciliationDefinition legacyDef(
                String code, String category, String legacyMetric, String from,
                String profile, double warn, double material) {
            CiReconciliationDefinition d = def(code, category,
                    Map.of("metricCode", legacyMetric, "role", "LEFT_FROM_EVIDENCE"),
                    Map.of("metricCode", legacyMetric, "role", "RIGHT_FROM_EVIDENCE"),
                    "COMMON_OVERLAP",
                    "PERCENT_OF_RIGHT",
                    List.of(legacyMetric),
                    profile, warn, material);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("toleranceProfile", profile);
            meta.put("migratedFrom", from);
            meta.put("legacyMetricCode", legacyMetric);
            d.setMetadata(meta);
            return d;
        }
    }
}
