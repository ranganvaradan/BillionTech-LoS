package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * W3 Requirement Plan goldens — fulfilment ≠ readiness; Mockito unit style (W2 pattern).
 */
@ExtendWith(MockitoExtension.class)
class RequirementPlanW3GoldensTest {

    @Mock RequirementPlanRepository planRepository;
    @Mock RequirementItemRepository itemRepository;
    @Mock RequirementStateTransitionRepository transitionRepository;

    RequirementCompletenessEvaluator completenessEvaluator;
    RequirementItemTransitionService transitionService;
    RequirementPlanService planService;

    Map<UUID, RequirementItemEntity> itemStore;
    List<RequirementStateTransitionEntity> audits;

    UUID appId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        completenessEvaluator = new RequirementCompletenessEvaluator();
        transitionService = new RequirementItemTransitionService(itemRepository, transitionRepository);
        planService = new RequirementPlanService(planRepository, completenessEvaluator, mock(DataRequirementPlanner.class));

        itemStore = new ConcurrentHashMap<>();
        audits = new ArrayList<>();

        lenient().when(itemRepository.save(any())).thenAnswer(inv -> {
            RequirementItemEntity i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(UUID.randomUUID());
            }
            itemStore.put(i.getId(), i);
            return i;
        });
        lenient().when(itemRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findByIdAndPlanId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(any())).thenAnswer(inv ->
                itemStore.values().stream()
                        .filter(i -> i.getPlan() != null && inv.getArgument(0).equals(i.getPlan().getId()))
                        .collect(Collectors.toList()));
        lenient().when(transitionRepository.save(any())).thenAnswer(inv -> {
            RequirementStateTransitionEntity t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            audits.add(t);
            return t;
        });
        lenient().when(planRepository.save(any())).thenAnswer(inv -> {
            RequirementPlanEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(planId);
            }
            for (RequirementItemEntity item : p.getItems()) {
                if (item.getId() == null) {
                    item.setId(UUID.randomUUID());
                }
                itemStore.put(item.getId(), item);
            }
            return p;
        });
        lenient().when(planRepository.findByIdWithItems(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id == null) {
                return Optional.empty();
            }
            List<RequirementItemEntity> items = itemStore.values().stream()
                    .filter(i -> i.getPlan() != null && id.equals(i.getPlan().getId()))
                    .collect(Collectors.toList());
            if (items.isEmpty() && !id.equals(planId)) {
                return Optional.empty();
            }
            RequirementPlanEntity p = RequirementPlanEntity.builder()
                    .id(id)
                    .applicationId(appId)
                    .planVersion(1)
                    .status(RequirementPlanStatus.ACTIVE)
                    .build();
            for (RequirementItemEntity item : items) {
                p.addItem(item);
            }
            return Optional.of(p);
        });
    }

    private RequirementItemEntity item(String key, RequirementClass clazz, List<FulfilmentMode> modes,
                                       CustomerFulfilmentState fulfilment, DataReadinessState readiness,
                                       boolean required) {
        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .id(planId)
                .applicationId(appId)
                .planVersion(1)
                .status(RequirementPlanStatus.ACTIVE)
                .build();
        RequirementItemEntity i = RequirementItemEntity.builder()
                .id(UUID.randomUUID())
                .plan(plan)
                .itemKey(key)
                .canonicalParameterId(key)
                .businessName(key)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(clazz)
                .phase(RequirementPhase.PRE_UNDERWRITING_DATA)
                .required(required)
                .customerFulfilmentState(fulfilment)
                .dataReadinessState(readiness)
                .sourceAcquisitionState(SourceAcquisitionState.NOT_STARTED)
                .allowedFulfilmentModes(new ArrayList<>(modes))
                .sourceHints(new java.util.LinkedHashMap<>())
                .provenance(new java.util.LinkedHashMap<>())
                .policyRuleRefs(new ArrayList<>())
                .sortOrder(itemStore.size())
                .build();
        itemStore.put(i.getId(), i);
        return i;
    }

    @Test
    void fulfilmentNotEqualReadiness_documentUpload() {
        RequirementItemEntity i = item("financial.ebitda", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);

        transitionService.markDocumentUploaded(planId, i.getId(), "doc-fs-1", null, "tester", null);

        assertEquals(CustomerFulfilmentState.PROVIDED, i.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.PROCESSING, i.getDataReadinessState());
        assertNotEquals(i.getCustomerFulfilmentState().name(), i.getDataReadinessState().name());
        assertNotEquals(DataReadinessState.READY_FOR_POLICY, i.getDataReadinessState());
    }

    @Test
    void documentUpload_providedImmediately_readinessProcessing() {
        RequirementItemEntity i = item("doc.financial_statements", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUIRED, DataReadinessState.NOT_AVAILABLE, true);

        transitionService.markDocumentUploaded(planId, i.getId(), "DOC-001", null, "rm1", "upload");

        assertEquals(CustomerFulfilmentState.PROVIDED, i.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.PROCESSING, i.getDataReadinessState());
        assertEquals(FulfilmentMode.DOCUMENT_UPLOAD, i.getFulfilmentModeUsed());
        assertEquals("DOC-001", i.getDocumentRef());
        assertNotNull(i.getProvidedAt());
    }

    @Test
    void multiParameterDocument_oneUpload_threeItems_independentReadiness() {
        String group = "FINANCIAL_STATEMENTS";
        RequirementItemEntity revenue = item("financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        RequirementItemEntity ebitda = item("financial.ebitda", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        RequirementItemEntity networth = item("financial.networth", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        revenue.getSourceHints().put("pendingDocumentGroup", group);
        ebitda.getSourceHints().put("pendingDocumentGroup", group);
        networth.getSourceHints().put("pendingDocumentGroup", group);

        List<RequirementItemEntity> updated = transitionService.markDocumentUploaded(
                planId, revenue.getId(), "fs-upload-9", null, "system", null);

        assertEquals(3, updated.size());
        for (RequirementItemEntity i : List.of(revenue, ebitda, networth)) {
            assertEquals(CustomerFulfilmentState.PROVIDED, i.getCustomerFulfilmentState());
            assertEquals(DataReadinessState.PROCESSING, i.getDataReadinessState());
        }

        transitionService.advanceReadiness(planId, revenue.getId(), DataReadinessState.READY_FOR_POLICY, "parser", null);
        transitionService.advanceReadiness(planId, ebitda.getId(), DataReadinessState.VERIFIED, "parser", null);
        transitionService.advanceReadiness(planId, networth.getId(), DataReadinessState.DATA_INSUFFICIENT, "parser", null);

        assertEquals(DataReadinessState.READY_FOR_POLICY, revenue.getDataReadinessState());
        assertEquals(DataReadinessState.VERIFIED, ebitda.getDataReadinessState());
        assertEquals(DataReadinessState.DATA_INSUFFICIENT, networth.getDataReadinessState());
        assertEquals(CustomerFulfilmentState.PROVIDED, revenue.getCustomerFulfilmentState());
        assertEquals(CustomerFulfilmentState.PROVIDED, ebitda.getCustomerFulfilmentState());
        assertEquals(CustomerFulfilmentState.PROVIDED, networth.getCustomerFulfilmentState());
    }

    @Test
    void twelveItemGolden_customerUnresolvedAndUploadB() {
        List<RequirementItemEntity> items = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            items.add(item("auto.available." + n, RequirementClass.ALREADY_AVAILABLE,
                    List.of(FulfilmentMode.DERIVATION),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY, true));
        }
        for (int n = 1; n <= 3; n++) {
            items.add(item("derivable." + n, RequirementClass.DERIVABLE,
                    List.of(FulfilmentMode.DERIVATION),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY, true));
        }
        for (int n = 1; n <= 3; n++) {
            items.add(item("auto.source." + n, RequirementClass.AUTO_SOURCE,
                    List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY, true));
        }
        RequirementItemEntity a = item("customer.A", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        RequirementItemEntity b = item("customer.B", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        RequirementItemEntity c = item("customer.C", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT, FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        items.add(a);
        items.add(b);
        items.add(c);
        assertEquals(12, items.size());

        RequirementDtos.CompletenessResult before = completenessEvaluator.evaluate(items);
        assertEquals(3, before.customerUnresolvedCount());

        transitionService.markDocumentUploaded(planId, b.getId(), "B-DOC", null, "cust", null);

        assertEquals(CustomerFulfilmentState.PROVIDED, b.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.PROCESSING, b.getDataReadinessState());
        assertFalse(RequirementCompletenessEvaluator.isCustomerUnresolved(b),
                "B must not be asked again while processing");

        RequirementDtos.CompletenessResult after = completenessEvaluator.evaluate(items);
        assertEquals(2, after.customerUnresolvedCount());
        assertEquals(CompletenessStatus.INCOMPLETE, after.status());
    }

    @Test
    void bureauScore_automaticOnly_noDirectInputFallback() {
        RequirementItemEntity bureau = item("bureau.score", RequirementClass.AUTO_SOURCE,
                List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE, true);

        assertThrows(BusinessRuleException.class, () ->
                transitionService.markDirectInput(planId, bureau.getId(), "hack", false, "tester", null));

        transitionService.advanceSource(planId, bureau.getId(), SourceAcquisitionState.UNAVAILABLE, "provider", "down");

        assertEquals(SourceAcquisitionState.UNAVAILABLE, bureau.getSourceAcquisitionState());
        assertTrue(bureau.getDataReadinessState() == DataReadinessState.DATA_INSUFFICIENT
                || bureau.getDataReadinessState() == DataReadinessState.NOT_AVAILABLE);
        assertEquals(CustomerFulfilmentState.NOT_APPLICABLE, bureau.getCustomerFulfilmentState());

        assertThrows(BusinessRuleException.class, () ->
                transitionService.advanceSource(planId, bureau.getId(),
                        SourceAcquisitionState.CUSTOMER_FALLBACK, "system", null));
    }

    @Test
    void requiredFailedReadiness_blocksCompleteness() {
        RequirementItemEntity ok = item("ok.param", RequirementClass.ALREADY_AVAILABLE,
                List.of(FulfilmentMode.DERIVATION),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY, true);
        RequirementItemEntity failed = item("bad.param", RequirementClass.AUTO_SOURCE,
                List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.FAILED, true);

        RequirementDtos.CompletenessResult r = completenessEvaluator.evaluate(List.of(ok, failed));
        assertEquals(CompletenessStatus.BLOCKED, r.status());
        assertTrue(r.blockedCount() >= 1);
    }

    @Test
    void optionalNotApplicable_works() {
        RequirementItemEntity optional = item("optional.note", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUIRED, DataReadinessState.NOT_AVAILABLE, false);

        transitionService.markNotApplicable(planId, optional.getId(), "rm", "not needed");

        assertEquals(CustomerFulfilmentState.NOT_APPLICABLE, optional.getCustomerFulfilmentState());
        assertFalse(RequirementCompletenessEvaluator.isCustomerUnresolved(optional));

        RequirementItemEntity required = item("required.note", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUIRED, DataReadinessState.NOT_AVAILABLE, true);
        assertThrows(BusinessRuleException.class, () ->
                transitionService.markNotApplicable(planId, required.getId(), "rm", null));
    }

    @Test
    void existingCanonicalReady_notInCustomerUnresolved() {
        RequirementItemEntity existing = item("identity.pan", RequirementClass.ALREADY_AVAILABLE,
                List.of(FulfilmentMode.DERIVATION),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY, true);
        RequirementItemEntity providedNoAsk = item("identity.name", RequirementClass.ALREADY_AVAILABLE,
                List.of(FulfilmentMode.DERIVATION),
                CustomerFulfilmentState.PROVIDED, DataReadinessState.READY_FOR_POLICY, true);

        assertFalse(RequirementCompletenessEvaluator.isCustomerUnresolved(existing));
        assertFalse(RequirementCompletenessEvaluator.isCustomerUnresolved(providedNoAsk));
        assertEquals(0, completenessEvaluator.evaluate(List.of(existing, providedNoAsk)).customerUnresolvedCount());
        assertEquals(CompletenessStatus.COMPLETE_FOR_NEXT_STAGE,
                completenessEvaluator.evaluate(List.of(existing, providedNoAsk)).status());
    }

    @Test
    void auditTransitionsWritten() {
        RequirementItemEntity i = item("financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);

        transitionService.markDocumentUploaded(planId, i.getId(), "aud-doc", null, "actor-x", "uat");
        transitionService.advanceReadiness(planId, i.getId(), DataReadinessState.EXTRACTED, "ocr", "parsed");

        assertTrue(audits.size() >= 3); // fulfilment + readiness on upload + readiness advance
        assertTrue(audits.stream().anyMatch(t ->
                RequirementStateTransitionEntity.FIELD_FULFILMENT.equals(t.getFieldName())
                        && "PROVIDED".equals(t.getToState())));
        assertTrue(audits.stream().anyMatch(t ->
                RequirementStateTransitionEntity.FIELD_READINESS.equals(t.getFieldName())
                        && "EXTRACTED".equals(t.getToState())));
        assertTrue(audits.stream().anyMatch(t -> "actor-x".equals(t.getActor())));
    }

    @Test
    void automaticOnly_cannotMarkDirectInput() {
        RequirementItemEntity bureau = item("bureau.score", RequirementClass.AUTO_SOURCE,
                List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                CustomerFulfilmentState.REQUIRED, DataReadinessState.NOT_AVAILABLE, true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                transitionService.markDirectInput(planId, bureau.getId(), "v", true, "x", null));
        assertEquals("AUTOMATIC_ONLY_NO_DIRECT_INPUT", ex.getReason());
        assertEquals(CustomerFulfilmentState.REQUIRED, bureau.getCustomerFulfilmentState());
    }

    @Test
    void directInput_verifiedBecomesReady_unverifiedStaysProcessing() {
        RequirementItemEntity verified = item("business.employee_count", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        transitionService.markDirectInput(planId, verified.getId(), "val-10", true, "rm", null);
        assertEquals(CustomerFulfilmentState.PROVIDED, verified.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.READY_FOR_POLICY, verified.getDataReadinessState());

        RequirementItemEntity unverified = item("business.turnover_declared", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE, true);
        transitionService.markDirectInput(planId, unverified.getId(), "val-20", false, "rm", null);
        assertEquals(CustomerFulfilmentState.PROVIDED, unverified.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.PROCESSING, unverified.getDataReadinessState());
    }

    @Test
    void createPlan_andSummarize_viaService() {
        RequirementDtos.CreatePlanRequest req = new RequirementDtos.CreatePlanRequest(
                appId, null, null, null, null, 1, RequirementPlanStatus.ACTIVE, Map.of("w3", true),
                List.of(
                        new RequirementDtos.ItemSpec(
                                "customer.A", RequirementType.CANONICAL_PARAMETER, RequirementClass.CUSTOMER_PROVIDED,
                                RequirementPhase.APPLICATION_INPUT, "customer.A", "A", true,
                                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE,
                                SourceAcquisitionState.NOT_STARTED, List.of(FulfilmentMode.DIRECT_INPUT),
                                List.of(), Map.of(), Map.of(), 0, null, null)
                ));

        RequirementDtos.PlanResponse created = planService.createPlan(req);
        assertNotNull(created.id());
        assertEquals(1, created.items().size());
        assertNotNull(created.planHash());

        ArgumentCaptor<RequirementPlanEntity> cap = ArgumentCaptor.forClass(RequirementPlanEntity.class);
        verify(planRepository).save(cap.capture());
        assertEquals(appId, cap.getValue().getApplicationId());
    }

    @Test
    void dataRequirementPlanner_stubStillExplicitlyUnsupported() {
        DataRequirementPlanner stub = new StubDataRequirementPlanner();
        assertThrows(UnsupportedOperationException.class, () ->
                stub.plan(PlanInputs.of(appId, UUID.randomUUID(), UUID.randomUUID())));
    }
}
