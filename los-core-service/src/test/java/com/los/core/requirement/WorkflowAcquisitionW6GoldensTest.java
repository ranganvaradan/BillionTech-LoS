package com.los.core.requirement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * W6 Acquisition Orchestration + Data Completeness Gate goldens.
 * No Policy / Scorecard / live UW execution.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAcquisitionW6GoldensTest {

    @Mock RequirementPlanRepository planRepository;
    @Mock RequirementItemRepository itemRepository;
    @Mock RequirementAcquisitionAttemptRepository attemptRepository;
    @Mock RequirementStateTransitionRepository transitionRepository;
    @Mock com.los.core.creditintelligence.repository.CiFactSnapshotRepository snapshotRepository;
    @Mock com.los.core.creditintelligence.repository.CiUnderwritingFactRepository factRepository;

    RequirementItemTransitionService transitionService;
    CanonicalFactLookupService factLookup;
    CanonicalFactReadinessReconciler reconciler;
    DataCompletenessGate gate;
    RequirementCompletenessEvaluator legacyEval;
    WorkflowAcquisitionCoordinator coordinator;

    Map<UUID, RequirementPlanEntity> planStore;
    Map<UUID, RequirementItemEntity> itemStore;
    Map<String, RequirementAcquisitionAttemptEntity> attemptStore;
    Map<String, AtomicInteger> callCounts;
    RecordingExecutor recordingExecutor;

    UUID appId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    UUID workflowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        planStore = new ConcurrentHashMap<>();
        itemStore = new ConcurrentHashMap<>();
        attemptStore = new ConcurrentHashMap<>();
        callCounts = new ConcurrentHashMap<>();

        legacyEval = new RequirementCompletenessEvaluator();
        transitionService = new RequirementItemTransitionService(itemRepository, transitionRepository);
        factLookup = new CanonicalFactLookupService(snapshotRepository, factRepository);
        W6EvaluationContextFactory evalFactory =
                new W6EvaluationContextFactory(snapshotRepository, factRepository);
        reconciler = new CanonicalFactReadinessReconciler(
                factLookup, transitionService, evalFactory, new W6CanonicalParameterExecutor());
        gate = new DataCompletenessGate(legacyEval);

        recordingExecutor = new RecordingExecutor(callCounts);
        AcquisitionExecutorRegistry registry = new AcquisitionExecutorRegistry(List.of(recordingExecutor));

        coordinator = new WorkflowAcquisitionCoordinator(
                planRepository, itemRepository, attemptRepository,
                transitionService, registry, reconciler, gate);

        lenient().when(snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(any()))
                .thenReturn(Optional.empty());
        lenient().when(transitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(planRepository.save(any())).thenAnswer(inv -> {
            RequirementPlanEntity p = inv.getArgument(0);
            planStore.put(p.getId(), p);
            return p;
        });
        lenient().when(planRepository.findByIdWithItems(any())).thenAnswer(inv ->
                Optional.ofNullable(planStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findByIdAndPlanId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.save(any())).thenAnswer(inv -> {
            RequirementItemEntity i = inv.getArgument(0);
            itemStore.put(i.getId(), i);
            return i;
        });
        lenient().when(itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(any())).thenAnswer(inv ->
                itemStore.values().stream()
                        .filter(i -> i.getPlan() != null && inv.getArgument(0).equals(i.getPlan().getId()))
                        .collect(Collectors.toList()));
        lenient().when(attemptRepository.findByExecutionKey(any())).thenAnswer(inv ->
                Optional.ofNullable(attemptStore.get(inv.getArgument(0))));
        lenient().when(attemptRepository.save(any())).thenAnswer(inv -> {
            RequirementAcquisitionAttemptEntity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            attemptStore.put(a.getExecutionKey(), a);
            return a;
        });
        lenient().when(attemptRepository.findByPlanIdOrderByCreatedAtAsc(any())).thenAnswer(inv ->
                attemptStore.values().stream()
                        .filter(a -> inv.getArgument(0).equals(a.getPlanId()))
                        .collect(Collectors.toList()));
    }

    private RequirementPlanEntity savePlan(List<RequirementItemEntity> items) {
        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .policyDocumentId(policyId)
                .workflowId(workflowId)
                .planVersion(1)
                .status(RequirementPlanStatus.ACTIVE)
                .metadata(new LinkedHashMap<>())
                .items(new ArrayList<>())
                .build();
        for (RequirementItemEntity item : items) {
            item.setId(UUID.randomUUID());
            item.setPlan(plan);
            plan.getItems().add(item);
            itemStore.put(item.getId(), item);
        }
        planStore.put(plan.getId(), plan);
        return plan;
    }

    private RequirementItemEntity item(String key, String param, RequirementClass clazz,
                                       boolean required, FulfilmentMode mode,
                                       CustomerFulfilmentState fulfil, DataReadinessState ready,
                                       SourceAcquisitionState source, Map<String, Object> hints) {
        return RequirementItemEntity.builder()
                .itemKey(key)
                .canonicalParameterId(param)
                .businessName(key)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(clazz)
                .phase(RequirementPhase.PRE_UNDERWRITING_DATA)
                .required(required)
                .customerFulfilmentState(fulfil)
                .dataReadinessState(ready)
                .sourceAcquisitionState(source)
                .allowedFulfilmentModes(new ArrayList<>(List.of(mode)))
                .sourceHints(hints != null ? new LinkedHashMap<>(hints) : new LinkedHashMap<>())
                .provenance(new LinkedHashMap<>())
                .policyRuleRefs(new ArrayList<>(List.of("rule-" + key)))
                .sortOrder(0)
                .build();
    }

    @Test
    void singleCoordinator_reusesExecutorRegistry_onlyPreferredSource() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("preferredMode", "AUTOMATIC_SOURCE");
        hints.put("preferredSourceKey", "ACCOUNT_AGGREGATOR");
        hints.put("alternativeSources", List.of(Map.of(
                "mode", "DOCUMENT_UPLOAD",
                "documentGroup", "BANK_STATEMENT")));
        RequirementPlanEntity plan = savePlan(List.of(
                item("bank.adb", "BANK_ADB", RequirementClass.AUTO_SOURCE, true,
                        FulfilmentMode.AUTOMATIC_SOURCE,
                        CustomerFulfilmentState.NOT_APPLICABLE,
                        DataReadinessState.NOT_AVAILABLE,
                        SourceAcquisitionState.NOT_STARTED, hints)));

        recordingExecutor.outcomes.put("ACCOUNT_AGGREGATOR",
                AcquisitionDtos.ExecutorOutcome.succeeded("AA", Map.of("BANK_ADB", true), Map.of()));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        assertEquals(1, callCounts.getOrDefault("ACCOUNT_AGGREGATOR", new AtomicInteger()).get());
        assertEquals(0, callCounts.getOrDefault("BANK_STATEMENT_UPLOAD", new AtomicInteger()).get());
        assertTrue(r.executedSources().stream().anyMatch(s -> s.contains("ACCOUNT_AGGREGATOR")));
        assertFalse(r.gate().policyAutoExecuted());
        assertFalse(r.gate().scorecardAutoExecuted());
    }

    @Test
    void alternativeSourceNotExecutedSimultaneously() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("preferredSourceKey", "ACCOUNT_AGGREGATOR");
        hints.put("alternativeSources", List.of(Map.of("mode", "DOCUMENT_UPLOAD", "documentGroup", "BANK_STATEMENT")));
        // Also allow DOCUMENT_UPLOAD on item
        RequirementItemEntity it = item("bank.adb", "BANK_ADB", RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE,
                CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED, hints);
        it.setAllowedFulfilmentModes(new ArrayList<>(List.of(
                FulfilmentMode.AUTOMATIC_SOURCE, FulfilmentMode.DOCUMENT_UPLOAD)));
        RequirementPlanEntity plan = savePlan(List.of(it));
        recordingExecutor.outcomes.put("ACCOUNT_AGGREGATOR",
                AcquisitionDtos.ExecutorOutcome.succeeded("AA", Map.of("BANK_ADB", true), Map.of()));

        coordinator.orchestrate(plan.getId(), "test");
        assertEquals(1, callCounts.getOrDefault("ACCOUNT_AGGREGATOR", new AtomicInteger()).get());
        assertEquals(0, callCounts.getOrDefault("BANK_STATEMENT_UPLOAD", new AtomicInteger()).get());
        assertEquals(0, callCounts.getOrDefault("DOCUMENT_EXTRACTION", new AtomicInteger()).get());
    }

    @Test
    void acquisitionIdempotent_secondCallDoesNotRePull() {
        Map<String, Object> hints = Map.of("preferredSourceKey", "BUREAU");
        RequirementPlanEntity plan = savePlan(List.of(
                item("bureau.score", "BUREAU_SCORE", RequirementClass.AUTO_SOURCE, true,
                        FulfilmentMode.AUTOMATIC_SOURCE,
                        CustomerFulfilmentState.NOT_APPLICABLE,
                        DataReadinessState.NOT_AVAILABLE,
                        SourceAcquisitionState.NOT_STARTED, hints)));
        recordingExecutor.outcomes.put("BUREAU",
                AcquisitionDtos.ExecutorOutcome.succeeded("BUREAU", Map.of("BUREAU_SCORE", true),
                        Map.of("creditScore", 750, "scorePresent", true)));

        coordinator.orchestrate(plan.getId(), "test");
        coordinator.orchestrate(plan.getId(), "test");
        assertEquals(1, callCounts.getOrDefault("BUREAU", new AtomicInteger()).get());
    }

    @Test
    void parallelIndependentSources_sameWave() {
        List<RequirementItemEntity> items = List.of(
                auto("bureau.score", "BUREAU_SCORE", "BUREAU"),
                auto("gst.turnover", "GST_TURNOVER", "GST"),
                auto("kyc.status", "KYC_STATUS", "KYC"));
        RequirementPlanEntity plan = savePlan(items);
        recordingExecutor.outcomes.put("BUREAU",
                AcquisitionDtos.ExecutorOutcome.succeeded("B", Map.of("BUREAU_SCORE", true), Map.of("scorePresent", true)));
        recordingExecutor.outcomes.put("GST",
                AcquisitionDtos.ExecutorOutcome.succeeded("G", Map.of("GST_TURNOVER", true), Map.of()));
        recordingExecutor.outcomes.put("KYC",
                AcquisitionDtos.ExecutorOutcome.succeeded("K", Map.of("KYC_STATUS", true), Map.of()));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        @SuppressWarnings("unchecked")
        List<List<String>> waves = (List<List<String>>) r.diagnostics().get("parallelWaves");
        assertNotNull(waves);
        assertFalse(waves.isEmpty());
        assertTrue(waves.get(0).size() >= 3, "Independent sources should share wave 0");
        assertEquals(Boolean.TRUE, r.diagnostics().get("parallelCapable"));
    }

    @Test
    void derivationWaitsForDependencies() {
        Map<String, Object> ebitdaHints = new LinkedHashMap<>();
        ebitdaHints.put("preferredMode", "DERIVATION");
        ebitdaHints.put("preferredSourceKey", "DERIVATION");
        ebitdaHints.put("dependsOn", List.of("EBITDA", "REVENUE"));
        ebitdaHints.put("derivationReady", true);

        RequirementItemEntity margin = item("ebitda.margin", "EBITDA_MARGIN", RequirementClass.DERIVABLE, true,
                FulfilmentMode.DERIVATION,
                CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED, ebitdaHints);
        RequirementItemEntity ebitda = item("ebitda", "EBITDA", RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE,
                CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredSourceKey", "GST"));
        RequirementItemEntity revenue = item("revenue", "REVENUE", RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE,
                CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredSourceKey", "GST"));

        RequirementPlanEntity plan = savePlan(List.of(margin, ebitda, revenue));
        recordingExecutor.outcomes.put("GST",
                AcquisitionDtos.ExecutorOutcome.succeeded("GST", Map.of("EBITDA", true, "REVENUE", true), Map.of()));
        recordingExecutor.outcomes.put("DERIVATION",
                AcquisitionDtos.ExecutorOutcome.succeeded("DERIV", Map.of("EBITDA_MARGIN", true), Map.of()));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        @SuppressWarnings("unchecked")
        List<List<String>> waves = (List<List<String>>) r.diagnostics().get("parallelWaves");
        // Derivation should not be in wave 0 with unmet deps — either deferred then later wave, or later wave
        assertTrue(waves.size() >= 2 || r.deferredForDependency().contains("ebitda.margin")
                || waves.stream().skip(1).anyMatch(w -> w.contains("ebitda.margin")));
    }

    @Test
    void customerWaitDoesNotBlockUnrelatedAutoSources() {
        RequirementItemEntity customer = item("fs.notes", "FS_NOTES", RequirementClass.CUSTOMER_PROVIDED, true,
                FulfilmentMode.DOCUMENT_UPLOAD,
                CustomerFulfilmentState.REQUIRED,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredMode", "DOCUMENT_UPLOAD", "pendingDocumentGroup", "FINANCIAL_STATEMENTS"));
        RequirementItemEntity bureau = auto("bureau.score", "BUREAU_SCORE", "BUREAU");
        RequirementItemEntity gst = auto("gst.turnover", "GST_TURNOVER", "GST");
        RequirementPlanEntity plan = savePlan(List.of(customer, bureau, gst));
        recordingExecutor.outcomes.put("BUREAU",
                AcquisitionDtos.ExecutorOutcome.succeeded("B", Map.of("BUREAU_SCORE", true), Map.of("scorePresent", true)));
        recordingExecutor.outcomes.put("GST",
                AcquisitionDtos.ExecutorOutcome.succeeded("G", Map.of("GST_TURNOVER", true), Map.of()));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        assertTrue(callCounts.getOrDefault("BUREAU", new AtomicInteger()).get() >= 1);
        assertTrue(callCounts.getOrDefault("GST", new AtomicInteger()).get() >= 1);
        assertEquals(0, callCounts.getOrDefault("DOCUMENT_EXTRACTION", new AtomicInteger()).get());
        assertTrue(r.skippedSources().stream().anyMatch(s -> s.startsWith("fs.notes")));
    }

    @Test
    void documentExtractionAfterUpload_partialFactsIndependent() {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("REVENUE", 100);
        extracted.put("NET_WORTH", 50);
        // EBITDA absent

        RequirementItemEntity revenue = docItem("rev", "REVENUE", extracted);
        RequirementItemEntity ebitda = docItem("ebitda", "EBITDA", extracted);
        RequirementItemEntity nw = docItem("nw", "NET_WORTH", extracted);
        RequirementPlanEntity plan = savePlan(List.of(revenue, ebitda, nw));

        coordinator.orchestrate(plan.getId(), "test");

        assertEquals(DataReadinessState.READY_FOR_POLICY, itemStore.get(revenue.getId()).getDataReadinessState());
        assertEquals(DataReadinessState.READY_FOR_POLICY, itemStore.get(nw.getId()).getDataReadinessState());
        assertEquals(DataReadinessState.DATA_INSUFFICIENT, itemStore.get(ebitda.getId()).getDataReadinessState());
        assertEquals(CustomerFulfilmentState.PROVIDED, itemStore.get(ebitda.getId()).getCustomerFulfilmentState());
        assertNotEquals(CustomerFulfilmentState.REUPLOAD_REQUIRED,
                itemStore.get(ebitda.getId()).getCustomerFulfilmentState());
        AcquisitionDtos.GateResult g = coordinator.gateStatus(plan.getId());
        assertNotEquals(DataCompletenessGateStatus.READY_FOR_POLICY, g.status());
    }

    @Test
    void extractionFailureDoesNotForceReupload() {
        RequirementItemEntity doc = docItem("rev", "REVENUE", null);
        doc.getSourceHints().put("forceExtractionFail", true);
        RequirementPlanEntity plan = savePlan(List.of(doc));
        recordingExecutor.outcomes.put("DOCUMENT_EXTRACTION",
                AcquisitionDtos.ExecutorOutcome.terminal("OCR", "parse error",
                        Map.of("documentOutcome", "EXTRACTION_FAILED")));

        coordinator.orchestrate(plan.getId(), "test");
        RequirementItemEntity after = itemStore.get(doc.getId());
        assertEquals(CustomerFulfilmentState.PROVIDED, after.getCustomerFulfilmentState());
        assertNotEquals(CustomerFulfilmentState.REUPLOAD_REQUIRED, after.getCustomerFulfilmentState());
    }

    @Test
    void providerSuccessNotDataReady_bureauMissingScore() {
        Map<String, Object> hints = Map.of("preferredSourceKey", "BUREAU");
        RequirementPlanEntity plan = savePlan(List.of(
                item("bureau.score", "BUREAU_SCORE", RequirementClass.AUTO_SOURCE, true,
                        FulfilmentMode.AUTOMATIC_SOURCE,
                        CustomerFulfilmentState.NOT_APPLICABLE,
                        DataReadinessState.NOT_AVAILABLE,
                        SourceAcquisitionState.NOT_STARTED, hints)));
        // SUCCEEDED but score absent — no invent 0
        recordingExecutor.outcomes.put("BUREAU", new AcquisitionDtos.ExecutorOutcome(
                SourceAcquisitionState.SUCCEEDED, "BUREAU", "tx-1", null, null,
                Map.of("creditScore", 0, "scorePresent", false, "providerHttpSuccess", true),
                Map.of("BUREAU_SCORE", false),
                true));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        RequirementItemEntity after = itemStore.get(plan.getItems().get(0).getId());
        assertEquals(SourceAcquisitionState.SUCCEEDED, after.getSourceAcquisitionState());
        assertEquals(DataReadinessState.DATA_INSUFFICIENT, after.getDataReadinessState());
        assertNotEquals(DataCompletenessGateStatus.READY_FOR_POLICY, r.gate().status());
    }

    @Test
    void explicitAaFallbackToBankStatement() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("preferredSourceKey", "ACCOUNT_AGGREGATOR");
        hints.put("alternativeSources", List.of(Map.of(
                "mode", "DOCUMENT_UPLOAD", "documentGroup", "BANK_STATEMENT")));
        RequirementItemEntity it = item("bank.adb", "BANK_ADB", RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE,
                CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE,
                SourceAcquisitionState.NOT_STARTED, hints);
        it.setAllowedFulfilmentModes(new ArrayList<>(List.of(
                FulfilmentMode.AUTOMATIC_SOURCE, FulfilmentMode.DOCUMENT_UPLOAD)));
        RequirementPlanEntity plan = savePlan(List.of(it));
        recordingExecutor.outcomes.put("ACCOUNT_AGGREGATOR",
                AcquisitionDtos.ExecutorOutcome.terminal("AA", "AA terminal unavailable", Map.of()));

        coordinator.orchestrate(plan.getId(), "test");
        RequirementItemEntity after = itemStore.get(it.getId());
        assertTrue(after.getSourceAcquisitionState().requiresCustomerAction());
        assertEquals("BANK_STATEMENT_UPLOAD", after.getSourceHints().get("activeSourceKey"));
        assertEquals(0, callCounts.getOrDefault("BANK_STATEMENT_UPLOAD", new AtomicInteger()).get());
        assertEquals(1, callCounts.getOrDefault("ACCOUNT_AGGREGATOR", new AtomicInteger()).get());
        RequirementAcquisitionAttemptEntity attempt = attemptStore.values().iterator().next();
        assertEquals("ACCOUNT_AGGREGATOR", attempt.getPreviousSourceKey());
        assertEquals("BANK_STATEMENT_UPLOAD", attempt.getFallbackSourceKey());
    }

    @Test
    void twelveItemGolden_waitingForCustomerUntilLast() {
        List<RequirementItemEntity> items = new ArrayList<>();
        // 4 already READY
        for (int i = 1; i <= 4; i++) {
            items.add(item("ready." + i, "READY_" + i, RequirementClass.ALREADY_AVAILABLE, true,
                    FulfilmentMode.AUTOMATIC_SOURCE,
                    CustomerFulfilmentState.NOT_APPLICABLE,
                    DataReadinessState.READY_FOR_POLICY,
                    SourceAcquisitionState.SUCCEEDED,
                    Map.of("preferredSourceKey", "BUREAU")));
        }
        // 3 automatic
        items.add(auto("auto.1", "AUTO_1", "BUREAU"));
        items.add(auto("auto.2", "AUTO_2", "GST"));
        items.add(auto("auto.3", "AUTO_3", "KYC"));
        // 2 derivations (deps ready after autos)
        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("preferredSourceKey", "DERIVATION");
        d1.put("preferredMode", "DERIVATION");
        d1.put("dependsOn", List.of("AUTO_1"));
        d1.put("derivationReady", true);
        items.add(item("der.1", "DER_1", RequirementClass.DERIVABLE, true,
                FulfilmentMode.DERIVATION, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED, d1));
        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("preferredSourceKey", "DERIVATION");
        d2.put("preferredMode", "DERIVATION");
        d2.put("dependsOn", List.of("AUTO_2"));
        d2.put("derivationReady", true);
        items.add(item("der.2", "DER_2", RequirementClass.DERIVABLE, true,
                FulfilmentMode.DERIVATION, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED, d2));
        // 3 customer — 2 provided, 1 outstanding
        items.add(custProvided("cust.1", "CUST_1"));
        items.add(custProvided("cust.2", "CUST_2"));
        items.add(item("cust.3", "CUST_3", RequirementClass.CUSTOMER_PROVIDED, true,
                FulfilmentMode.DIRECT_INPUT, CustomerFulfilmentState.REQUIRED,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredMode", "DIRECT_INPUT")));

        // Mark provided customers as READY already (processed)
        items.get(9).setDataReadinessState(DataReadinessState.READY_FOR_POLICY);
        items.get(10).setDataReadinessState(DataReadinessState.READY_FOR_POLICY);

        RequirementPlanEntity plan = savePlan(items);
        recordingExecutor.outcomes.put("BUREAU",
                AcquisitionDtos.ExecutorOutcome.succeeded("B", Map.of("AUTO_1", true), Map.of("scorePresent", true)));
        recordingExecutor.outcomes.put("GST",
                AcquisitionDtos.ExecutorOutcome.succeeded("G", Map.of("AUTO_2", true), Map.of()));
        recordingExecutor.outcomes.put("KYC",
                AcquisitionDtos.ExecutorOutcome.succeeded("K", Map.of("AUTO_3", true), Map.of()));
        recordingExecutor.outcomes.put("DERIVATION",
                AcquisitionDtos.ExecutorOutcome.succeeded("D", Map.of("DER_1", true, "DER_2", true), Map.of()));

        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        assertEquals(DataCompletenessGateStatus.WAITING_FOR_CUSTOMER, r.gate().status());
        assertTrue(r.gate().counters().customerOutstanding() >= 1);

        // Supply final customer requirement
        RequirementItemEntity last = items.get(11);
        transitionService.markDirectInput(plan.getId(), last.getId(), "val", true, "cust", "final");
        // Also mark ready (verified direct input)
        assertEquals(DataReadinessState.READY_FOR_POLICY,
                itemStore.get(last.getId()).getDataReadinessState());

        AcquisitionDtos.GateResult after = coordinator.gateStatus(plan.getId());
        assertEquals(DataCompletenessGateStatus.READY_FOR_POLICY, after.status());
        assertFalse(after.policyAutoExecuted());
        assertFalse(after.scorecardAutoExecuted());
    }

    @Test
    void requiredMissingBlocks_optionalNaDoesNot() {
        RequirementItemEntity required = item("req", "REQ", RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.DATA_INSUFFICIENT, SourceAcquisitionState.SUCCEEDED,
                Map.of("preferredSourceKey", "BUREAU"));
        RequirementItemEntity optional = item("opt", "OPT", RequirementClass.CUSTOMER_PROVIDED, false,
                FulfilmentMode.DIRECT_INPUT, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_REQUIRED,
                Map.of());
        RequirementPlanEntity plan = savePlan(List.of(required, optional));
        AcquisitionDtos.GateResult g = gate.evaluate(plan);
        assertNotEquals(DataCompletenessGateStatus.READY_FOR_POLICY, g.status());
    }

    @Test
    void circularDependencyFailClosed() {
        Map<String, Object> aHints = new LinkedHashMap<>();
        aHints.put("preferredSourceKey", "DERIVATION");
        aHints.put("dependsOn", List.of("B"));
        Map<String, Object> bHints = new LinkedHashMap<>();
        bHints.put("preferredSourceKey", "DERIVATION");
        bHints.put("dependsOn", List.of("A"));
        RequirementItemEntity a = item("a", "A", RequirementClass.DERIVABLE, true,
                FulfilmentMode.DERIVATION, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED, aHints);
        RequirementItemEntity b = item("b", "B", RequirementClass.DERIVABLE, true,
                FulfilmentMode.DERIVATION, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED, bHints);
        a.setId(UUID.randomUUID());
        b.setId(UUID.randomUUID());
        assertThrows(com.los.core.exception.BusinessRuleException.class,
                () -> AcquisitionDependencyGraph.schedule(List.of(a, b), java.util.Set.of()));
    }

    @Test
    void noPolicyScorecardInDiagnostics() {
        RequirementPlanEntity plan = savePlan(List.of(
                item("ready", "R", RequirementClass.ALREADY_AVAILABLE, true,
                        FulfilmentMode.AUTOMATIC_SOURCE, CustomerFulfilmentState.NOT_APPLICABLE,
                        DataReadinessState.READY_FOR_POLICY, SourceAcquisitionState.SUCCEEDED, Map.of())));
        AcquisitionDtos.OrchestrationResult r = coordinator.orchestrate(plan.getId(), "test");
        assertEquals(false, r.diagnostics().get("policyAutoExecution"));
        assertEquals(false, r.diagnostics().get("scorecardAutoExecution"));
        assertEquals(DataCompletenessGateStatus.READY_FOR_POLICY, r.gate().status());
    }

    private RequirementItemEntity auto(String key, String param, String source) {
        return item(key, param, RequirementClass.AUTO_SOURCE, true,
                FulfilmentMode.AUTOMATIC_SOURCE, CustomerFulfilmentState.NOT_APPLICABLE,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredSourceKey", source, "preferredMode", "AUTOMATIC_SOURCE"));
    }

    private RequirementItemEntity custProvided(String key, String param) {
        return item(key, param, RequirementClass.CUSTOMER_PROVIDED, true,
                FulfilmentMode.DIRECT_INPUT, CustomerFulfilmentState.PROVIDED,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED,
                Map.of("preferredMode", "DIRECT_INPUT"));
    }

    private RequirementItemEntity docItem(String key, String param, Map<String, Object> extracted) {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("preferredMode", "DOCUMENT_UPLOAD");
        hints.put("preferredSourceKey", "DOCUMENT_EXTRACTION");
        hints.put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");
        if (extracted != null) {
            hints.put("extractedParameters", extracted);
        }
        RequirementItemEntity it = item(key, param, RequirementClass.CUSTOMER_PROVIDED, true,
                FulfilmentMode.DOCUMENT_UPLOAD, CustomerFulfilmentState.PROVIDED,
                DataReadinessState.NOT_AVAILABLE, SourceAcquisitionState.NOT_STARTED, hints);
        it.setDocumentRef("FINANCIAL_STATEMENTS");
        it.setFulfilmentModeUsed(FulfilmentMode.DOCUMENT_UPLOAD);
        return it;
    }

    /** Fake executor covering all source keys for goldens. */
    static final class RecordingExecutor implements AcquisitionExecutorPort {
        final Map<String, AtomicInteger> callCounts;
        final Map<String, AcquisitionDtos.ExecutorOutcome> outcomes = new ConcurrentHashMap<>();

        RecordingExecutor(Map<String, AtomicInteger> callCounts) {
            this.callCounts = callCounts;
        }

        @Override
        public String sourceKey() {
            return "RECORDING";
        }

        @Override
        public boolean supports(String sourceKey) {
            return true;
        }

        @Override
        public AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx) {
            callCounts.computeIfAbsent(ctx.sourceKey(), k -> new AtomicInteger()).incrementAndGet();
            AcquisitionDtos.ExecutorOutcome o = outcomes.get(ctx.sourceKey());
            if (o != null) {
                // Per-parameter fact overlay for derivation multi-param
                if (o.factReadiness() != null && ctx.item().getCanonicalParameterId() != null
                        && o.factReadiness().containsKey(ctx.item().getCanonicalParameterId())) {
                    boolean ready = Boolean.TRUE.equals(
                            o.factReadiness().get(ctx.item().getCanonicalParameterId()));
                    return new AcquisitionDtos.ExecutorOutcome(
                            o.status(), o.providerRef(), o.externalRef(), o.failureClass(), o.failureReason(),
                            o.resultSummary(), Map.of(ctx.item().getCanonicalParameterId(), ready),
                            o.providerHttpSuccess());
                }
                return o;
            }
            // Document partial extraction from hints
            if (AcquisitionSourceResolver.DOCUMENT_EXTRACTION.equals(ctx.sourceKey())
                    || AcquisitionSourceResolver.BANK_STATEMENT_UPLOAD.equals(ctx.sourceKey())) {
                Object partial = ctx.item().getSourceHints() != null
                        ? ctx.item().getSourceHints().get("extractedParameters") : null;
                if (partial instanceof Map<?, ?> m) {
                    String param = ctx.item().getCanonicalParameterId();
                    boolean present = param != null && m.get(param) != null;
                    return new AcquisitionDtos.ExecutorOutcome(
                            SourceAcquisitionState.SUCCEEDED, "OCR", ctx.item().getDocumentRef(),
                            present ? null : "DATA_INSUFFICIENT",
                            present ? null : "missing",
                            Map.of("partial", true),
                            param != null ? Map.of(param, present) : Map.of(),
                            true);
                }
            }
            return AcquisitionDtos.ExecutorOutcome.succeeded(ctx.sourceKey(),
                    ctx.item().getCanonicalParameterId() != null
                            ? Map.of(ctx.item().getCanonicalParameterId(), true) : Map.of(),
                    Map.of());
        }
    }
}
