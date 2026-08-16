package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityParityService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerType;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.requirement.acquisition.ExistingSourceAcquisitionAdapters;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.CanonicalScorecardValueResolver;
import com.los.core.service.underwriting.UnderwritingEvaluationContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W6-EXECUTION-SPINE-CLOSURE-1 — Workflow post-acquisition uses the same spine as
 * Policy Test / Scorecard / Underwriting. Source acquired ≠ parameter available.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class W6ExecutionSpineClosureAcceptanceTest {

    static final UUID VIKASAM_POLICY = UUID.fromString("4543e643-c3a0-4a57-a92c-370dff8b2fa9");

    static final List<String> VIKASAM_13 = List.of(
            "bureau.score",
            "bureau.recent_inquiries_90d",
            "bureau.settled_account_count",
            "bureau.written_off_account_count",
            "bureau.accounts.cc_writeoff",
            "bureau.accounts.writeoff_non_cc",
            "bureau.tradeline.suit_filed",
            "bureau.credit_after_overdue.clean_history_months",
            "bureau.dpd_30_plus_count_6m",
            "bureau.cc_overdue_amount",
            "bureau.overdue.amount",
            "bureau.overdue.age_months",
            "bureau.max_dpd_6m"
    );

    static final List<String> UNSUPPORTED = List.of(
            "bureau.dpd_30_plus_count_6m",
            "bureau.cc_overdue_amount",
            "bureau.overdue.amount",
            "bureau.overdue.age_months"
    );

    @Mock CiFactSnapshotRepository snapshotRepository;
    @Mock CiUnderwritingFactRepository factRepository;
    @Mock com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository bureauReportRepository;
    @Mock com.los.core.creditintelligence.bureau.repository.CiBureauTradelineRepository tradelineRepository;
    @Mock com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository paymentHistoryRepository;
    @Mock com.los.core.creditintelligence.bureau.repository.CiBureauInquiryRepository inquiryRepository;
    @Mock RequirementItemRepository itemRepository;
    @Mock RequirementStateTransitionRepository transitionRepository;

    private CanonicalParameterExecutionService spine;
    private DerivedCalculationDefinitionService definitions;
    private W6EvaluationContextFactory evalFactory;
    private W6CanonicalParameterExecutor executor;
    private CanonicalFactReadinessReconciler reconciler;
    private RequirementItemTransitionService transitionService;
    private DataCompletenessGate gate;

    @BeforeEach
    void setUp() {
        definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());

        Map<String, Object> cleanExpr = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GT",
                "matchValue", 0,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        when(definitions.latestFor(eq("bureau.credit_after_overdue.clean_history_months"), any()))
                .thenReturn(Optional.of(CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000601"))
                        .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(cleanExpr)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

        Map<String, Object> badDpd = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GTE",
                "matchValue", 30,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        when(definitions.latestFor(eq("bureau.dpd_30_plus_count_6m"), any()))
                .thenReturn(Optional.of(CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000602"))
                        .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(badDpd)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);

        when(snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(any()))
                .thenReturn(Optional.empty());
        when(transitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByIdAndPlanId(any(), any())).thenAnswer(inv -> Optional.empty());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        evalFactory = new W6EvaluationContextFactory(
                snapshotRepository, factRepository,
                bureauReportRepository, tradelineRepository,
                paymentHistoryRepository, inquiryRepository);
        executor = new W6CanonicalParameterExecutor();
        transitionService = new RequirementItemTransitionService(itemRepository, transitionRepository);
        reconciler = new CanonicalFactReadinessReconciler(
                new CanonicalFactLookupService(snapshotRepository, factRepository),
                transitionService, evalFactory, executor);
        gate = new DataCompletenessGate(new RequirementCompletenessEvaluator());
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    private EvaluationContext representativeFacts(EvaluationMode mode) {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of("month", "2026-01", "dpd", 45));
        history.add(Map.of("month", "2026-02", "dpd", 0));
        history.add(Map.of("month", "2026-03", "dpd", 0));
        history.add(Map.of("month", "2026-04", "dpd", 0));
        history.add(Map.of("month", "2026-05", "dpd", 0));
        history.add(Map.of("month", "2026-06", "dpd", 0));
        history.add(Map.of("month", "2026-07", "dpd", 0));
        history.add(Map.of("month", "2026-08", "dpd", 0));
        return EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 720)
                .fact("bureau.recent_inquiries_90d", 2)
                .fact("bureau.settled_account_count", 1)
                .fact("bureau.written_off_account_count", 0)
                .fact("bureau.accounts.cc_writeoff", 0)
                .fact("bureau.accounts.writeoff_non_cc", 0)
                .fact("bureau.tradeline.suit_filed", false)
                .fact("bureau.max_dpd_6m", 45)
                .fact("bureau.tradeline.payment_history", history)
                .build();
    }

    private RequirementPlanEntity planWithItem(RequirementItemEntity item) {
        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .policyDocumentId(VIKASAM_POLICY)
                .planVersion(1)
                .status(RequirementPlanStatus.ACTIVE)
                .metadata(new LinkedHashMap<>())
                .items(new ArrayList<>())
                .build();
        item.setId(UUID.randomUUID());
        item.setPlan(plan);
        plan.getItems().add(item);
        when(itemRepository.findByIdAndPlanId(item.getId(), plan.getId())).thenReturn(Optional.of(item));
        return plan;
    }

    private RequirementItemEntity gacatItem(String canonicalId, Map<String, Object> hints) {
        return RequirementItemEntity.builder()
                .itemKey(canonicalId)
                .canonicalParameterId(canonicalId)
                .businessName(canonicalId)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(RequirementClass.AUTO_SOURCE)
                .phase(RequirementPhase.PRE_UNDERWRITING_DATA)
                .required(true)
                .customerFulfilmentState(CustomerFulfilmentState.NOT_APPLICABLE)
                .dataReadinessState(DataReadinessState.NOT_AVAILABLE)
                .sourceAcquisitionState(SourceAcquisitionState.NOT_STARTED)
                .allowedFulfilmentModes(new ArrayList<>(List.of(FulfilmentMode.AUTOMATIC_SOURCE)))
                .sourceHints(hints != null ? new LinkedHashMap<>(hints) : new LinkedHashMap<>())
                .provenance(new LinkedHashMap<>())
                .policyRuleRefs(new ArrayList<>(List.of("vikasam-rule")))
                .sortOrder(0)
                .build();
    }

    @Test
    void w4RequiredIds_resolveViaGacatMapping() {
        assertThat(W6EvaluationContextFactory.resolveGacatId("bureau.score")).contains("bureau.score");
        assertThat(W6EvaluationContextFactory.resolveGacatId("BUREAU_SCORE")).contains("bureau.score");
        // Fixture keys with no EXACT/SAFE_ALIAS GACAT binding stay on legacy readiness path
        assertThat(W6EvaluationContextFactory.resolveGacatId("AUTO_1")).isEmpty();
        assertThat(W6EvaluationContextFactory.resolveGacatId("EBITDA_MARGIN")).isEmpty();
    }

    @Test
    void sourceAcquisitionAlone_doesNotSatisfyParameter() {
        RequirementItemEntity item = gacatItem("bureau.max_dpd_6m",
                Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU",
                Map.of("bureau.max_dpd_6m", true),
                Map.of("creditScore", 750, "scorePresent", true, "providerHttpSuccess", true));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getSourceAcquisitionState()).isNotEqualTo(SourceAcquisitionState.FAILED_TERMINAL);
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.DATA_INSUFFICIENT);
        assertThat(after.getProvenance().get("acquisitionVsParameter"))
                .isEqualTo("SOURCE_ACQUIRED_BUT_PARAMETER_NOT_RESOLVED");
        assertThat(after.getProvenance().get("spineExecutionStatus"))
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE.name());
        assertThat(Boolean.TRUE.equals(after.getProvenance().get("requirementSatisfied"))).isFalse();
    }

    @Test
    void rawParameter_resolvesThroughSpineAfterAcquisition() {
        RequirementItemEntity item = gacatItem("bureau.score", Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU",
                Map.of("bureau.score", false),
                Map.of("creditScore", 720, "scorePresent", true));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.READY_FOR_POLICY);
        assertThat(after.getProvenance().get("spineExecutionStatus"))
                .isEqualTo(ExecutionStatus.VALUE_AVAILABLE.name());
        assertThat(after.getProvenance().get("spineProducerType")).isEqualTo(ProducerType.RAW.name());
        assertThat(after.getProvenance().get("spineValue")).isEqualTo(720);
        assertThat(after.getProvenance().get("executionAuthority"))
                .isEqualTo("CanonicalParameterExecutionService");
    }

    @Test
    void builtIn_resolvesThroughSpineWhenFactPresent() {
        // bureau.max_dpd_6m is RAW/BUILT_IN claimed — supply exact fact via outcome metrics
        RequirementItemEntity item = gacatItem("bureau.max_dpd_6m", Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU", Map.of(),
                Map.of("canonicalMetrics", Map.of("bureau.max_dpd_6m", 45), "scorePresent", true));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.READY_FOR_POLICY);
        assertThat(after.getProvenance().get("spineValue")).isEqualTo(45);
        assertThat(after.getProvenance().get("spineProducerType")).isEqualTo(ProducerType.BUILT_IN.name());
    }

    @Test
    void authoredDerived_resolvesRecursivelyThroughSpine() {
        RequirementItemEntity item = gacatItem(
                "bureau.credit_after_overdue.clean_history_months",
                Map.of("preferredSourceKey", "DERIVATION", "preferredMode", "DERIVATION",
                        "inputs", Map.of("bureau.tradeline.payment_history", List.of(
                                Map.of("month", "2026-01", "dpd", 45),
                                Map.of("month", "2026-08", "dpd", 0)))));
        item.setRequirementClass(RequirementClass.DERIVABLE);
        item.setAllowedFulfilmentModes(new ArrayList<>(List.of(FulfilmentMode.DERIVATION)));
        RequirementPlanEntity plan = planWithItem(item);

        ExistingSourceAcquisitionAdapters.DerivationAcquisitionAdapter adapter =
                new ExistingSourceAcquisitionAdapters.DerivationAcquisitionAdapter(evalFactory, executor);
        AcquisitionDtos.ExecutorOutcome outcome = adapter.execute(
                new AcquisitionExecutorPort.ExecutionContext(
                        plan.getApplicationId(), plan.getId(), 1, item, "DERIVATION", "test", false));

        assertThat(outcome.resultSummary().get("executionAuthority"))
                .isEqualTo("CanonicalParameterExecutionService");
        assertThat(outcome.resultSummary().get("spineExecution")).isInstanceOf(Map.class);

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.READY_FOR_POLICY);
        assertThat(after.getProvenance().get("spineProducerType"))
                .isEqualTo(ProducerType.AUTHORED_DERIVED.name());
        assertThat(after.getProvenance().get("spineProvenance")).isNotNull();
    }

    @Test
    void missingProducer_notExecutable_noEndlessAcquisitionRetry() {
        RequirementItemEntity item = gacatItem("bureau.cc_overdue_amount",
                Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU", Map.of(), Map.of("creditScore", 720, "scorePresent", true));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.DATA_INSUFFICIENT);
        assertThat(after.getProvenance().get("spineExecutionStatus"))
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE.name());
        assertThat(Boolean.TRUE.equals(after.getProvenance().get("requirementSatisfied"))).isFalse();
    }

    @Test
    void missingDependency_dependencyNotAvailable() {
        RequirementItemEntity item = gacatItem(
                "bureau.credit_after_overdue.clean_history_months",
                Map.of("preferredSourceKey", "DERIVATION"));
        item.setRequirementClass(RequirementClass.DERIVABLE);
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "DERIVATION", Map.of(), Map.of("mode", "DERIVATION"));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isEqualTo(DataReadinessState.DATA_INSUFFICIENT);
        assertThat(after.getProvenance().get("spineExecutionStatus"))
                .isIn(ExecutionStatus.DEPENDENCY_NOT_AVAILABLE.name(),
                        ExecutionStatus.DATA_NOT_AVAILABLE.name());
    }

    @Test
    void providerFailure_remainsAcquisitionFailure() {
        RequirementItemEntity item = gacatItem("bureau.score", Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        item.setSourceAcquisitionState(SourceAcquisitionState.FAILED_RETRYABLE);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.retryable(
                "BUREAU", "Equifax timeout", Map.of());

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isNotEqualTo(DataReadinessState.READY_FOR_POLICY);
        assertThat(after.getProvenance().get("spineExecutionStatus")).isNull();
    }

    @Test
    void exactCanonicalId_enforced_noProxy() {
        RequirementItemEntity item = gacatItem("bureau.cc_overdue_amount",
                Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        // Poison: related metric present — must not satisfy cc_overdue_amount
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU", Map.of(),
                Map.of("canonicalMetrics", Map.of(
                        "bureau.accounts.credit_card_overdue_max", 5000,
                        "bureau.overdue.amount", 5000),
                        "creditScore", 720));

        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getDataReadinessState()).isNotEqualTo(DataReadinessState.READY_FOR_POLICY);
        assertThat(after.getProvenance().get("spineExecutionStatus"))
                .isEqualTo(ExecutionStatus.NOT_EXECUTABLE.name());
    }

    @Test
    void completenessRequiresSuccessfulCanonicalExecution() {
        RequirementItemEntity acquired = gacatItem("bureau.cc_overdue_amount", Map.of());
        acquired.setId(UUID.randomUUID());
        acquired.setSourceAcquisitionState(SourceAcquisitionState.SUCCEEDED);
        acquired.setDataReadinessState(DataReadinessState.DATA_INSUFFICIENT);
        acquired.getProvenance().put("acquisitionVsParameter", "SOURCE_ACQUIRED_BUT_PARAMETER_NOT_RESOLVED");
        acquired.getProvenance().put("spineExecutionStatus", ExecutionStatus.NOT_EXECUTABLE.name());

        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .policyDocumentId(VIKASAM_POLICY)
                .items(new ArrayList<>(List.of(acquired)))
                .build();

        AcquisitionDtos.GateResult g = gate.evaluate(plan);
        assertThat(g.status()).isNotEqualTo(DataCompletenessGateStatus.READY_FOR_POLICY);
        assertThat(g.reasons().stream().anyMatch(r -> r.contains("SOURCE_ACQUIRED≠PARAMETER_RESOLVED")
                || r.contains("DATA_INSUFFICIENT"))).isTrue();
    }

    @Test
    void crossPath_w6PolicyTestScorecardUnderwriting_valueParity() {
        EvaluationContext w6Ctx = representativeFacts(EvaluationMode.W6_ACQUISITION);
        EvaluationContext ptCtx = representativeFacts(EvaluationMode.POLICY_TEST);
        EvaluationContext scCtx = representativeFacts(EvaluationMode.UNDERWRITING);

        LoanApplication app = new LoanApplication();
        app.setBureauScore(720);
        Map<String, BigDecimal> scMap = new LinkedHashMap<>();
        scMap.put("bureau.score", BigDecimal.valueOf(720));
        scMap.put("bureau.recent_inquiries_90d", BigDecimal.valueOf(2));
        scMap.put("bureau.settled_account_count", BigDecimal.ONE);
        scMap.put("bureau.written_off_account_count", BigDecimal.ZERO);
        scMap.put("bureau.accounts.cc_writeoff", BigDecimal.ZERO);
        scMap.put("bureau.accounts.writeoff_non_cc", BigDecimal.ZERO);
        scMap.put("bureau.max_dpd_6m", BigDecimal.valueOf(45));
        EffectiveUnderwritingContext uwCtxData = new EffectiveUnderwritingContext(
                720, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC", scMap);
        EvaluationContext uwBuilt = UnderwritingEvaluationContextFactory.forUnderwriting(app, uwCtxData);
        EvaluationContext uwCtx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .facts(w6Ctx.facts())
                .build();

        int mismatchPt = 0;
        int mismatchSc = 0;
        int mismatchUw = 0;
        int unsupportedFalseExec = 0;
        List<Map<String, Object>> table = new ArrayList<>();

        for (String id : VIKASAM_13) {
            ExecutionResult w6 = spine.resolveAndExecute(id, w6Ctx);
            ExecutionResult pt = spine.resolveAndExecute(id, ptCtx);
            CanonicalScorecardValueResolver.ResolveOutcome sc =
                    CanonicalScorecardValueResolver.resolveCanonical(id, scCtx);
            ExecutionResult uwExec = spine.resolveAndExecute(id, uwCtx);

            boolean executable = w6.valueAvailable();
            if (UNSUPPORTED.contains(id) && executable) {
                unsupportedFalseExec++;
            }

            if (executable) {
                if (!sameValue(w6.value(), pt.value()) || w6.producerId() == null
                        || !w6.producerId().equals(pt.producerId())) {
                    mismatchPt++;
                }
                if (!sc.valueAvailable()
                        || !sameValue(w6.value(), sc.rawValue() != null ? sc.rawValue() : sc.numericValue())
                        || (sc.producerId() != null && !sc.producerId().equals(w6.producerId()))) {
                    mismatchSc++;
                }
                if (!sameValue(w6.value(), uwExec.value()) || !w6.producerId().equals(uwExec.producerId())) {
                    mismatchUw++;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalId", id);
            row.put("vikasamPolicyId", VIKASAM_POLICY.toString());
            row.put("requiredByW4", "YES");
            row.put("w6Status", w6.status().name());
            row.put("w6Value", w6.value());
            row.put("w6Producer", w6.producerId());
            row.put("ptValue", pt.value());
            row.put("scValue", sc.numericValue());
            row.put("uwValue", uwExec.value());
            row.put("requirementSatisfied", executable);
            row.put("provenance", w6.provenance());
            row.put("underwritingSameValue", executable && sameValue(w6.value(), uwExec.value()));
            table.add(row);
        }

        assertThat(mismatchPt).as("W6_POLICY_TEST_VALUE_MISMATCH_COUNT").isZero();
        assertThat(mismatchSc).as("W6_SCORECARD_VALUE_MISMATCH_COUNT").isZero();
        assertThat(mismatchUw).as("W6_UNDERWRITING_VALUE_MISMATCH_COUNT").isZero();
        assertThat(unsupportedFalseExec).as("UNSUPPORTED_PARAMETER_FALSE_EXECUTION_COUNT").isZero();
        assertThat(table).hasSize(13);

        ExecutionResult clean = spine.resolveAndExecute(
                "bureau.credit_after_overdue.clean_history_months", w6Ctx);
        assertThat(clean.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(clean.producerType()).isEqualTo(ProducerType.AUTHORED_DERIVED);
        assertThat(uwBuilt).isNotNull();
    }

    @Test
    void provenanceSurvivesAcquisitionToExecution() {
        RequirementItemEntity item = gacatItem("bureau.score", Map.of("preferredSourceKey", "BUREAU"));
        RequirementPlanEntity plan = planWithItem(item);
        AcquisitionDtos.ExecutorOutcome outcome = AcquisitionDtos.ExecutorOutcome.succeeded(
                "BUREAU", Map.of(), Map.of("creditScore", 701, "scorePresent", true));
        RequirementItemEntity after = reconciler.reconcile(plan.getId(), item, outcome, "test");
        assertThat(after.getProvenance().get("spineProvenance")).isInstanceOf(Map.class);
        assertThat(after.getProvenance().get("canonicalParameterId")).isEqualTo("bureau.score");
        assertThat(after.getProvenance().get("spineExactProducerPath")).isNotNull();
    }

    @Test
    void vikasamUnsupportedFour_remainUnsupported() {
        EvaluationContext ctx = representativeFacts(EvaluationMode.W6_ACQUISITION);
        for (String id : UNSUPPORTED) {
            ExecutionResult r = spine.resolveAndExecute(id, ctx);
            assertThat(r.valueAvailable()).as(id).isFalse();
            assertThat(r.status()).as(id).isNotEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        }
    }

    @Test
    void surfaceParity169_zeroMismatch() {
        Map<String, Object> report = new CanonicalParameterCapabilityParityService(spine).runParityCheck();
        assertThat(report.get("catalogueCount")).isEqualTo(169);
        assertThat(report.get("dataParametersVsPolicyStudioDisagreementCount")).isEqualTo(0);
        assertThat(report.get("surfaceVsSpineDisagreementCount")).isEqualTo(0);
    }

    @Test
    void acquisitionClaim_neverMarksGacatReadyFromSourceAlone() {
        assertThat(W6EvaluationContextFactory.acquisitionClaim("bureau.score", true)
                .get("bureau.score")).isFalse();
        assertThat(W6EvaluationContextFactory.acquisitionClaim("AUTO_1", true)
                .get("AUTO_1")).isTrue();
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString())) == 0;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }
}
