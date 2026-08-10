package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.EvidenceStrengthGrade;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import com.los.core.creditintelligence.reconciliation.repository.CiCreditEvidenceSummaryRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationDefinitionRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationEvidenceRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.creditintelligence.reconciliation.service.CreditEvidenceSummaryBuilder;
import com.los.core.creditintelligence.reconciliation.service.DiscrepancySeverityClassifier;
import com.los.core.creditintelligence.reconciliation.service.EvidenceStrengthScorer;
import com.los.core.creditintelligence.reconciliation.service.LegacyReconciliationBridge;
import com.los.core.creditintelligence.reconciliation.service.OperandResolver;
import com.los.core.creditintelligence.reconciliation.service.PeriodAlignmentService;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationConfidenceCalculator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationEvaluator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationExplanationBuilder;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationOrchestrator;
import com.los.core.creditintelligence.reconciliation.service.TurnoverTriangulationService;
import com.los.core.creditintelligence.reconciliation.service.VarianceCalculator;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrPresumptiveIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.repository.CiItrTaxSummaryRepository;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Multi-source E2E using SYNTHETIC fixture application (GST + Bank + ITR + Bureau metrics).
 * Data origin: SYNTHETIC — not live provider / stored payload validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiSourceReconciliationE2ETest {

    static final String DATA_ORIGIN = "SYNTHETIC";

    @Mock CiReconciliationDefinitionRepository definitionRepository;
    @Mock CiReconciliationResultRepository resultRepository;
    @Mock CiReconciliationEvidenceRepository evidenceRepository;
    @Mock CiCreditEvidenceSummaryRepository evidenceSummaryRepository;
    @Mock CiMetricResultRepository metricResultRepository;
    @Mock CiUnderwritingFactRepository factRepository;
    @Mock CiItrReturnRepository itrReturnRepository;
    @Mock CiItrPresumptiveIncomeRepository presumptiveRepository;
    @Mock CiItrTaxSummaryRepository taxSummaryRepository;
    @Mock CiAisSummaryRepository aisSummaryRepository;
    @Mock CiForm26AsSummaryRepository form26AsSummaryRepository;

    private ReconciliationOrchestrator orchestrator;
    private final UUID tenant = UUID.randomUUID();
    private final UUID app = UUID.randomUUID();
    private final List<CiReconciliationResult> savedResults = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        OperandResolver operandResolver = new OperandResolver(
                metricResultRepository, factRepository, itrReturnRepository,
                presumptiveRepository, taxSummaryRepository);
        ReconciliationEvaluator evaluator = new ReconciliationEvaluator(
                new PeriodAlignmentService(), new VarianceCalculator(),
                new ReconciliationConfidenceCalculator(), new ReconciliationExplanationBuilder(),
                new DiscrepancySeverityClassifier(), props);
        LegacyReconciliationBridge legacyBridge = new LegacyReconciliationBridge(
                metricResultRepository, new ReconciliationExplanationBuilder(),
                new DiscrepancySeverityClassifier());
        TurnoverTriangulationService triangulation = new TurnoverTriangulationService(
                new VarianceCalculator(), new ReconciliationExplanationBuilder(), props);
        CreditEvidenceSummaryBuilder summaryBuilder = new CreditEvidenceSummaryBuilder(new EvidenceStrengthScorer());

        orchestrator = new ReconciliationOrchestrator(
                definitionRepository, resultRepository, evidenceRepository, evidenceSummaryRepository,
                operandResolver, evaluator, legacyBridge, triangulation, summaryBuilder,
                metricResultRepository, itrReturnRepository, aisSummaryRepository, form26AsSummaryRepository);

        when(definitionRepository.findByStatusOrderByReconciliationCodeAsc(anyString())).thenReturn(List.of());
        when(resultRepository.save(any())).thenAnswer(inv -> {
            CiReconciliationResult r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            savedResults.add(r);
            return r;
        });
        when(evidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceSummaryRepository.save(any())).thenAnswer(inv -> {
            CiCreditEvidenceSummary s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(itrReturnRepository.findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(app))
                .thenReturn(List.of());
        when(aisSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(app)).thenReturn(List.of());
        when(form26AsSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(app)).thenReturn(List.of());
        when(factRepository.findBySnapshotId(any())).thenReturn(List.of());
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(eq(app), anyString()))
                .thenReturn(Optional.empty());

        // SYNTHETIC: GST 8.4 Cr, ITR 8.1 Cr, Bank 7.7 Cr, obligations near-aligned
        stubAmount(GstMetricService.TRAILING_12M, "84000000");
        stubAmount(TaxMetricService.TURNOVER_LATEST, "81000000");
        stubAmount(BankingMetricService.ADJ_12M, "77000000");
        stubAmount(BureauMetricService.TOTAL_MONTHLY_OBLIGATION, "182000");
        stubAmount(BankingMetricService.MONTHLY_OBL, "176000");
        stubVariance(GstMetricService.VARIANCE, "MATCH", "84000000", "84000000", "0");
        stubVariance(TaxMetricService.XSRC_AIS_INCOME, "ACCEPTABLE_VARIANCE", "1000000", "950000", "5");
        stubVariance(TaxMetricService.XSRC_26AS_TDS, "MATCH", "200000", "200000", "0");
    }

    @Test
    void syntheticMultiSourceRunsOrchestratorTriangulationAndEvidenceStrength() {
        assertThat(DATA_ORIGIN).as("fixture origin must be documented as SYNTHETIC").isEqualTo("SYNTHETIC");

        var orch = orchestrator.run(tenant, app, null, null, Set.of(), LocalDate.of(2025, 8, 1));

        assertThat(orch.results()).isNotEmpty();
        assertThat(orch.results().stream().map(CiReconciliationResult::getReconciliationCode))
                .contains(
                        ReconciliationConstants.XSRC_GST_ITR_TURNOVER,
                        ReconciliationConstants.XSRC_GST_BANK_TURNOVER,
                        ReconciliationConstants.XSRC_ITR_BANK_TURNOVER,
                        ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION,
                        ReconciliationConstants.TURNOVER_TRIANGULATION);

        assertThat(orch.triangulation()).isNotNull();
        assertThat(orch.triangulation().status()).isIn(
                TriangulationStatus.STRONG_ALIGNMENT,
                TriangulationStatus.REASONABLE_ALIGNMENT,
                TriangulationStatus.REVIEW_REQUIRED);

        assertThat(orch.evidenceSummary()).isNotNull();
        assertThat(orch.evidenceSummary().getEvidenceStrengthScore()).isNotNull();
        assertThat(orch.evidenceSummary().getEvidenceStrengthGrade())
                .isIn(EvidenceStrengthGrade.STRONG.name(), EvidenceStrengthGrade.ADEQUATE.name(),
                        EvidenceStrengthGrade.WEAK.name());
        assertThat(orch.evidenceSummary().getMetadata().get("notCreditRiskScore")).isEqualTo(true);

        Map<String, Object> turnover = orch.evidenceSummary().getTurnoverEvidence();
        assertThat(turnover.get("gst")).isNotNull();
        assertThat(turnover.get("itr")).isNotNull();
        assertThat(turnover.get("bank")).isNotNull();
    }

    private void stubAmount(String code, String amount) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("v", new BigDecimal(amount));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("periodFrom", "2024-08-01");
        evidence.put("periodTo", "2025-07-31");
        evidence.put("completeness", "1.0");
        evidence.put("confidence", "0.9");
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(eq(app), eq(code)))
                .thenReturn(Optional.of(metric(code, "PASS", value, evidence)));
    }

    private void stubVariance(String code, String outcome, String left, String right, String pct) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("v", new BigDecimal(pct));
        value.put("gstr1Sum", left);
        value.put("gstr3bSum", right);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("gstr1Sum", left);
        evidence.put("gstr3bSum", right);
        evidence.put("left", left);
        evidence.put("right", right);
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(eq(app), eq(code)))
                .thenReturn(Optional.of(metric(code, outcome, value, evidence)));
    }

    private CiMetricResult metric(String code, String outcome, Map<String, Object> value, Map<String, Object> evidence) {
        return CiMetricResult.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant)
                .applicationId(app)
                .metricCode(code)
                .metricVersion("V1")
                .outcome(outcome)
                .value(value)
                .evidence(evidence)
                .sourceRecordIds(List.of())
                .includedReferences(List.of())
                .excludedReferences(List.of())
                .metadata(Map.of("origin", DATA_ORIGIN))
                .build();
    }
}
