package com.los.core.creditintelligence;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.CiEvaluationStage;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.ComparisonStatus;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.repository.CiEvaluationStageRepository;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.repository.CiStandardRuleResultRepository;
import com.los.core.creditintelligence.service.LegacyUnderwritingContextAdapter;
import com.los.core.creditintelligence.service.ShadowCreditEvaluationService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import com.los.core.service.underwriting.UnderwritingRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShadowCreditEvaluationServiceTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiPolicyVersionRepository policyVersionRepository;
    @Mock
    private CiCreditEvaluationRepository evaluationRepository;
    @Mock
    private CiEvaluationStageRepository stageRepository;
    @Mock
    private CiStandardRuleResultRepository ruleResultRepository;
    @Mock
    private LegacyUnderwritingContextAdapter adapter;
    @Mock
    private UnderwritingRuleEngine underwritingRuleEngine;
    @Mock
    private ScorecardPolicyEngine scorecardPolicyEngine;
    @Mock
    private AuditService auditService;
    @Mock
    private com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator canonicalBureauRuleEvaluator;
    @Mock
    private com.los.core.creditintelligence.core.repository.CiMetricResultRepository metricResultRepository;
    @Mock
    private com.los.core.creditintelligence.gst.service.CanonicalGstRuleEvaluator canonicalGstRuleEvaluator;
    @Mock
    private com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository gstRegistrationRepository;
    @Mock
    private com.los.core.creditintelligence.banking.service.CanonicalBankingRuleEvaluator canonicalBankingRuleEvaluator;
    @Mock
    private com.los.core.creditintelligence.banking.repository.CiBankAccountRepository bankAccountRepository;
    @Mock
    private com.los.core.creditintelligence.tax.service.CanonicalTaxRuleEvaluator canonicalTaxRuleEvaluator;
    @Mock
    private com.los.core.creditintelligence.tax.repository.CiItrReturnRepository itrReturnRepository;
    @Mock
    private com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService reconciliationIngestionService;
    @Mock
    private com.los.core.creditintelligence.reconciliation.service.CanonicalReconciliationRuleEvaluator canonicalReconciliationRuleEvaluator;
    @Mock
    private com.los.core.creditintelligence.evaluation.ConfigFreezeService configFreezeService;
    @Mock
    private com.los.core.creditintelligence.evaluation.MetricResultSetService metricResultSetService;
    @Mock
    private com.los.core.creditintelligence.evaluation.ReconciliationResultSetService reconciliationResultSetService;
    @Mock
    private com.los.core.creditintelligence.evaluation.EvaluationContextFactory evaluationContextFactory;
    @Mock
    private com.los.core.creditintelligence.evaluation.FrozenPolicyExecutionAdapter frozenPolicyExecutionAdapter;
    @Mock
    private com.los.core.creditintelligence.evaluation.PureCanonicalEvaluationService pureCanonicalEvaluationService;
    @Mock
    private com.los.core.creditintelligence.evaluation.repository.CiEvaluationContextRepository evaluationContextRepository;

    private ShadowCreditEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ShadowCreditEvaluationService(
                snapshotRepository, policyVersionRepository, evaluationRepository,
                stageRepository, ruleResultRepository, adapter,
                underwritingRuleEngine, scorecardPolicyEngine,
                new CreditIntelligenceProperties(), auditService,
                canonicalBureauRuleEvaluator, metricResultRepository,
                canonicalGstRuleEvaluator, gstRegistrationRepository,
                canonicalBankingRuleEvaluator, bankAccountRepository,
                canonicalTaxRuleEvaluator, itrReturnRepository,
                reconciliationIngestionService, canonicalReconciliationRuleEvaluator,
                configFreezeService, metricResultSetService, reconciliationResultSetService,
                evaluationContextFactory, frozenPolicyExecutionAdapter,
                pureCanonicalEvaluationService, evaluationContextRepository);
        org.mockito.Mockito.lenient().when(gstRegistrationRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(bankAccountRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(itrReturnRepository
                        .findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(canonicalGstRuleEvaluator.evaluateAll(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(canonicalBankingRuleEvaluator.evaluateAll(
                        any(), any(), any(), any(), anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(canonicalTaxRuleEvaluator.evaluateAll(
                        any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(reconciliationIngestionService.runForShadow(any(), any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(reconciliationIngestionService.isEnabledFor(any(UUID.class)))
                .thenReturn(false);
    }

    @Test
    void recordsMatch_andDoesNotDependOnApplicationOrCamPersistence() {
        // Shadow service has no LoanApplicationRepository / CreditAppraisalService deps —
        // confirming architectural isolation (no save/CAM path available).
        UUID snapshotId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID prodEvalId = UUID.randomUUID();

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(
                CiFactSnapshot.builder().id(snapshotId).status(SnapshotStatus.FROZEN.name()).build()));
        when(policyVersionRepository.findById(policyId)).thenReturn(Optional.of(
                CiPolicyVersion.builder().id(policyId).version(1).build()));
        when(evaluationRepository.save(any())).thenAnswer(inv -> {
            CiCreditEvaluation e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(stageRepository.save(any())).thenAnswer(inv -> {
            CiEvaluationStage s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(ruleResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(canonicalBureauRuleEvaluator.evaluate(any(), anyInt()))
                .thenReturn(new com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator.RuleEvalResult(
                        "HARD_LIVE_UNSECURED", "HARD_LIVE_UNSECURED_V1", "DATA_INSUFFICIENT",
                        null, 6, Map.of(), Map.of("reason", "METRIC_MISSING")));

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                720, true, new BigDecimal("80000"), new BigDecimal("10000"),
                "MH", "Mumbai", "PROVIDER", "PROVIDER", "PROVIDER", Map.of("BUREAU_SCORE", new BigDecimal("720")));
        LoanApplication stub = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("500000"))
                .tenureMonths(36)
                .build();
        LegacyUnderwritingContextAdapter.AdapterResult adapted =
                new LegacyUnderwritingContextAdapter.AdapterResult(
                        ctx, Set.of(), Map.of(), Map.of(
                        "borrowerType", "INDIVIDUAL",
                        "loanProduct", "PERSONAL_LOAN",
                        "requestedAmount", "500000",
                        "tenureMonths", 36), "PASS");
        when(adapter.adapt(any(), any())).thenReturn(adapted);
        when(adapter.toLoanApplicationStub(appId, adapted)).thenReturn(stub);

        MultiRuleEvalResult multi = new MultiRuleEvalResult(
                List.of(new MultiRuleEvalResult.PerRuleEval(
                        UUID.randomUUID().toString(), "R1", "APPROVE", "APPROVED", 80,
                        List.of("ok"), "RULE", Map.of("BUREAU_SCORE", 720), Map.of("BUREAU_SCORE", 720))),
                "APPROVE", "APPROVED", 80, List.of("ok"));
        when(underwritingRuleEngine.evaluateAll(stub, ctx, "PASS")).thenReturn(multi);
        when(scorecardPolicyEngine.evaluate(stub, ctx, "PASS")).thenReturn(Optional.empty());

        UnderwritingEvaluation prod = UnderwritingEvaluation.builder()
                .id(prodEvalId)
                .applicationId(appId)
                .aggregateDecision("APPROVED")
                .build();

        Optional<ShadowCreditEvaluationService.ShadowResult> result =
                service.evaluate(snapshotId, policyId, appId, prod, "APPROVED");

        assertTrue(result.isPresent());
        assertEquals(ComparisonStatus.MATCH.name(), result.get().shadow().getComparisonStatus());
        assertEquals("APPROVED", result.get().shadow().getOverallOutcome());
        assertFalse(result.get().shadow().isAuthoritative());
    }

    @Test
    void recordsMismatch() {
        UUID snapshotId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(
                CiFactSnapshot.builder().id(snapshotId).status(SnapshotStatus.FROZEN.name()).build()));
        when(policyVersionRepository.findById(policyId)).thenReturn(Optional.of(
                CiPolicyVersion.builder().id(policyId).version(1).build()));
        when(evaluationRepository.save(any())).thenAnswer(inv -> {
            CiCreditEvaluation e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(stageRepository.save(any())).thenAnswer(inv -> {
            CiEvaluationStage s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(ruleResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(canonicalBureauRuleEvaluator.evaluate(any(), anyInt()))
                .thenReturn(new com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator.RuleEvalResult(
                        "HARD_LIVE_UNSECURED", "HARD_LIVE_UNSECURED_V1", "DATA_INSUFFICIENT",
                        null, 6, Map.of(), Map.of()));

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                500, true, null, null, null, null, "PROVIDER", "PROVIDER", "PROVIDER", Map.of());
        LoanApplication stub = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .build();
        var adapted = new LegacyUnderwritingContextAdapter.AdapterResult(
                ctx, Set.of(), Map.of(), Map.of("borrowerType", "INDIVIDUAL", "loanProduct", "PERSONAL_LOAN"), "PASS");
        when(adapter.adapt(any(), any())).thenReturn(adapted);
        when(adapter.toLoanApplicationStub(appId, adapted)).thenReturn(stub);

        MultiRuleEvalResult multi = new MultiRuleEvalResult(
                List.of(), "REJECT", "REJECTED", 20, List.of("fail"));
        when(underwritingRuleEngine.evaluateAll(stub, ctx, "PASS")).thenReturn(multi);
        when(scorecardPolicyEngine.evaluate(stub, ctx, "PASS")).thenReturn(Optional.empty());

        UnderwritingEvaluation prod = UnderwritingEvaluation.builder()
                .id(UUID.randomUUID())
                .aggregateDecision("APPROVED")
                .build();

        Optional<ShadowCreditEvaluationService.ShadowResult> result =
                service.evaluate(snapshotId, policyId, appId, prod, "APPROVED");

        assertTrue(result.isPresent());
        assertEquals(ComparisonStatus.MISMATCH.name(), result.get().shadow().getComparisonStatus());
    }
}
