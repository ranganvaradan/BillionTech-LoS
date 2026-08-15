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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * W5 customer-facing projection goldens — RequirementPlan is the only UI authority.
 */
@ExtendWith(MockitoExtension.class)
class CustomerRequirementsW5GoldensTest {

    @Mock RequirementPlanRepository planRepository;
    @Mock RequirementItemRepository itemRepository;
    @Mock RequirementStateTransitionRepository transitionRepository;

    CustomerRequirementsViewService viewService;
    RequirementItemTransitionService transitionService;
    RequirementCompletenessEvaluator completenessEvaluator;

    Map<UUID, RequirementPlanEntity> planStore;
    Map<UUID, RequirementItemEntity> itemStore;

    UUID appId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        viewService = new CustomerRequirementsViewService(planRepository);
        transitionService = new RequirementItemTransitionService(itemRepository, transitionRepository);
        completenessEvaluator = new RequirementCompletenessEvaluator();
        planStore = new ConcurrentHashMap<>();
        itemStore = new ConcurrentHashMap<>();

        lenient().when(planRepository.findByApplicationIdWithItems(any())).thenAnswer(inv ->
                planStore.values().stream()
                        .filter(p -> inv.getArgument(0).equals(p.getApplicationId()))
                        .collect(Collectors.toList()));
        lenient().when(planRepository.findByIdWithItems(any())).thenAnswer(inv ->
                Optional.ofNullable(planStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findByIdAndPlanId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(any())).thenAnswer(inv ->
                itemStore.values().stream()
                        .filter(i -> i.getPlan() != null && inv.getArgument(0).equals(i.getPlan().getId()))
                        .collect(Collectors.toList()));
        lenient().when(itemRepository.save(any())).thenAnswer(inv -> {
            RequirementItemEntity i = inv.getArgument(0);
            itemStore.put(i.getId(), i);
            return i;
        });
        lenient().when(transitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RequirementPlanEntity newPlan() {
        RequirementPlanEntity plan = RequirementPlanEntity.builder()
                .id(planId)
                .applicationId(appId)
                .planVersion(1)
                .status(RequirementPlanStatus.ACTIVE)
                .metadata(new LinkedHashMap<>())
                .items(new ArrayList<>())
                .build();
        planStore.put(planId, plan);
        return plan;
    }

    private RequirementItemEntity add(RequirementPlanEntity plan, String key, RequirementClass clazz,
                                      List<FulfilmentMode> modes,
                                      CustomerFulfilmentState fulfilment,
                                      DataReadinessState readiness) {
        RequirementItemEntity i = RequirementItemEntity.builder()
                .id(UUID.randomUUID())
                .plan(plan)
                .itemKey(key)
                .canonicalParameterId(key)
                .businessName(key.contains(".") ? key.substring(key.lastIndexOf('.') + 1).replace('_', ' ') : key)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(clazz)
                .phase(RequirementPhase.PRE_UNDERWRITING_DATA)
                .required(true)
                .customerFulfilmentState(fulfilment)
                .dataReadinessState(readiness)
                .sourceAcquisitionState(SourceAcquisitionState.NOT_STARTED)
                .allowedFulfilmentModes(new ArrayList<>(modes))
                .sourceHints(new LinkedHashMap<>())
                .provenance(new LinkedHashMap<>())
                .policyRuleRefs(new ArrayList<>())
                .sortOrder(plan.getItems().size())
                .build();
        plan.addItem(i);
        itemStore.put(i.getId(), i);
        return i;
    }

    @Test
    void twelveItemGolden_customerSeesExactlyThree() {
        RequirementPlanEntity plan = newPlan();
        for (int n = 1; n <= 4; n++) {
            add(plan, "ready." + n, RequirementClass.ALREADY_AVAILABLE,
                    List.of(FulfilmentMode.DERIVATION),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY);
        }
        for (int n = 1; n <= 3; n++) {
            add(plan, "auto." + n, RequirementClass.AUTO_SOURCE,
                    List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE);
        }
        for (int n = 1; n <= 2; n++) {
            add(plan, "deriv." + n, RequirementClass.DERIVABLE,
                    List.of(FulfilmentMode.DERIVATION),
                    CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE);
        }
        add(plan, "application.business_vintage_months", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        RequirementItemEntity b = add(plan, "financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        b.getSourceHints().put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");
        add(plan, "customer.dual", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT, FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE)
                .getSourceHints().put("pendingDocumentGroup", "OTHER_DOC");

        assertEquals(12, plan.getItems().size());
        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(3, view.summary().remainingActionsCount());
        assertEquals(3, view.actions().stream().filter(CustomerRequirementDtos.CustomerAction::actionable).count());
        assertTrue(view.actions().stream().noneMatch(a ->
                a.title() != null && a.title().toLowerCase().contains("bureau")));
        assertTrue(view.actions().stream().noneMatch(a ->
                a.allowedModes().contains(FulfilmentMode.AUTOMATIC_SOURCE)
                        && a.allowedModes().size() == 1));
    }

    @Test
    void autoSourceAndDerivationHidden() {
        RequirementPlanEntity plan = newPlan();
        add(plan, "bureau.score", RequirementClass.AUTO_SOURCE,
                List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE);
        add(plan, "financial.dscr", RequirementClass.DERIVABLE,
                List.of(FulfilmentMode.DERIVATION),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE);
        add(plan, "application.requested_amount", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);

        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(1, view.actions().size());
        assertFalse(view.actions().get(0).title().toLowerCase().contains("bureau"));
        assertTrue(CustomerRequirementsViewService.isCustomerFacingItem(plan.getItems().get(2)));
        assertFalse(CustomerRequirementsViewService.isCustomerFacingItem(plan.getItems().get(0)));
    }

    @Test
    void financialStatements_oneDocumentCard() {
        RequirementPlanEntity plan = newPlan();
        for (String id : List.of("financial.revenue", "financial.ebitda", "financial.networth")) {
            RequirementItemEntity i = add(plan, id, RequirementClass.CUSTOMER_PROVIDED,
                    List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                    CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
            i.getSourceHints().put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");
            i.setBusinessName(id.substring(id.lastIndexOf('.') + 1));
        }
        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(1, view.actions().size());
        assertEquals("Financial Statements", view.actions().get(0).title());
        assertEquals(3, view.actions().get(0).itemIds().size());
        assertEquals(1, view.summary().documentsRequiredCount());
    }

    @Test
    void providedShowsUploadedProcessing_notRequired() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity i = add(plan, "financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        i.getSourceHints().put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");

        transitionService.markDocumentUploaded(planId, i.getId(), "doc-1", null, "CUSTOMER", null);

        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(0, view.summary().remainingActionsCount());
        assertFalse(view.actions().stream().anyMatch(CustomerRequirementDtos.CustomerAction::actionable));
        assertFalse(view.providedOrProcessing().isEmpty());
        CustomerRequirementDtos.CustomerAction card = view.providedOrProcessing().get(0);
        assertEquals("Uploaded", card.customerStatusLabel());
        assertEquals("Processing…", card.processingLabel());
        assertNotEquals("Action needed", card.customerStatusLabel());
    }

    @Test
    void extractionFailed_doesNotRequireReupload() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity i = add(plan, "financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.PROVIDED, DataReadinessState.PROCESSING);
        i.getSourceHints().put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");
        i.setDocumentRef("doc-1");

        transitionService.markExtractionFailed(planId, i.getId(), "system", "parser error");

        assertEquals(CustomerFulfilmentState.PROVIDED, i.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.FAILED, i.getDataReadinessState());
        assertEquals("EXTRACTION_FAILED", i.getSourceHints().get("documentOutcome"));

        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(0, view.summary().remainingActionsCount());
        assertTrue(view.providedOrProcessing().stream().anyMatch(CustomerRequirementDtos.CustomerAction::extractionFailed));
        assertFalse(view.actions().stream().anyMatch(CustomerRequirementDtos.CustomerAction::reuploadRequired));
    }

    @Test
    void documentRejected_requiresReupload() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity i = add(plan, "financial.revenue", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.PROVIDED, DataReadinessState.PROCESSING);
        i.getSourceHints().put("pendingDocumentGroup", "FINANCIAL_STATEMENTS");

        transitionService.markDocumentRejected(planId, i.getId(), "rm", "illegible");

        assertEquals(CustomerFulfilmentState.REUPLOAD_REQUIRED, i.getCustomerFulfilmentState());
        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertTrue(view.summary().remainingActionsCount() >= 1);
        assertTrue(view.actions().stream().anyMatch(CustomerRequirementDtos.CustomerAction::reuploadRequired));
    }

    @Test
    void dualMode_showsChoice() {
        RequirementPlanEntity plan = newPlan();
        add(plan, "customer.dual", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT, FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);

        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertEquals(1, view.actions().size());
        assertTrue(view.actions().get(0).showChoice());
    }

    @Test
    void bureauNeverDirectInputInCustomerView() {
        RequirementPlanEntity plan = newPlan();
        add(plan, "bureau.score", RequirementClass.AUTO_SOURCE,
                List.of(FulfilmentMode.AUTOMATIC_SOURCE),
                CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.NOT_AVAILABLE);
        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertTrue(view.actions().isEmpty());
        List<CustomerRequirementDtos.AdminCustomerDebugRow> debug = viewService.adminDebug(planId);
        assertEquals(1, debug.size());
        assertFalse(debug.get(0).customerActionRequired());
    }

    @Test
    void providedNotReasked_afterOneOfThreeProvided() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity a = add(plan, "application.business_vintage_months", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        add(plan, "application.requested_amount", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        add(plan, "application.tenure_months", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);

        assertEquals(3, viewService.project(plan).summary().remainingActionsCount());
        transitionService.markDirectInput(planId, a.getId(), "24", false, "CUSTOMER", null);
        assertEquals(2, viewService.project(plan).summary().remainingActionsCount());
    }

    @Test
    void saveDraft_doesNotMarkProvided() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity a = add(plan, "application.business_vintage_months", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);

        transitionService.saveDirectInputDraft(planId, a.getId(), "18", "rm1", "RM");
        assertEquals(CustomerFulfilmentState.REQUESTED, a.getCustomerFulfilmentState());
        assertEquals("18", a.getSourceHints().get("draftValue"));
        assertEquals("RM", a.getSourceHints().get("lastActorRole"));
    }

    @Test
    void chooseMode_persistsChoice() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity dual = add(plan, "customer.dual", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT, FulfilmentMode.DOCUMENT_UPLOAD),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);

        transitionService.chooseFulfilmentMode(planId, dual.getId(), FulfilmentMode.DOCUMENT_UPLOAD, "cust", "CUSTOMER");
        CustomerRequirementDtos.CustomerRequirementsView view = viewService.project(plan);
        assertFalse(view.actions().get(0).showChoice());
        assertEquals(FulfilmentMode.DOCUMENT_UPLOAD, view.actions().get(0).preferredMode());
    }

    @Test
    void noGacatIdInCustomerTitles() {
        RequirementPlanEntity plan = newPlan();
        RequirementItemEntity i = add(plan, "application.business_vintage_months", RequirementClass.CUSTOMER_PROVIDED,
                List.of(FulfilmentMode.DIRECT_INPUT),
                CustomerFulfilmentState.REQUESTED, DataReadinessState.NOT_AVAILABLE);
        i.setBusinessName("Business vintage");
        CustomerRequirementDtos.CustomerAction action = viewService.project(plan).actions().get(0);
        assertEquals("Business vintage", action.title());
        assertFalse(action.title().contains("application."));
    }
}
