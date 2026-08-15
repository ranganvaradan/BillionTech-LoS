package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphOperand;
import com.los.core.creditintelligence.policystudio.graph.PolicyRuleGraphService;
import com.los.core.exception.BusinessRuleException;
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
import static org.mockito.Mockito.*;

/**
 * W4 DataRequirementPlanner goldens — Policy graph inventory → plan (no source execution).
 */
@ExtendWith(MockitoExtension.class)
class DataRequirementPlannerW4GoldensTest {

    @Mock PolicyRuleGraphService policyRuleGraphService;
    @Mock RequirementPlanRepository planRepository;
    @Mock com.los.core.creditintelligence.repository.CiFactSnapshotRepository snapshotRepository;
    @Mock com.los.core.creditintelligence.repository.CiUnderwritingFactRepository factRepository;
    @Mock RequirementItemRepository itemRepository;
    @Mock RequirementStateTransitionRepository transitionRepository;

    PolicyRequirementInventoryBuilder inventoryBuilder;
    FulfilmentPathResolver fulfilmentPathResolver;
    CanonicalFactLookupService factLookupService;
    RequirementCompletenessEvaluator completenessEvaluator;
    PolicyDrivenDataRequirementPlanner planner;
    RequirementPlanService planService;
    RequirementItemTransitionService transitionService;

    Map<UUID, RequirementPlanEntity> planStore;
    Map<UUID, RequirementItemEntity> itemStore;

    UUID appId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    UUID workflowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inventoryBuilder = new PolicyRequirementInventoryBuilder(policyRuleGraphService);
        fulfilmentPathResolver = new FulfilmentPathResolver();
        factLookupService = new CanonicalFactLookupService(snapshotRepository, factRepository);
        completenessEvaluator = new RequirementCompletenessEvaluator();
        planner = new PolicyDrivenDataRequirementPlanner(
                inventoryBuilder, fulfilmentPathResolver, factLookupService, planRepository, completenessEvaluator);
        planService = new RequirementPlanService(planRepository, completenessEvaluator, planner);
        transitionService = new RequirementItemTransitionService(itemRepository, transitionRepository);

        planStore = new ConcurrentHashMap<>();
        itemStore = new ConcurrentHashMap<>();

        lenient().when(snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(any()))
                .thenReturn(Optional.empty());
        lenient().when(planRepository.save(any())).thenAnswer(inv -> {
            RequirementPlanEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            for (RequirementItemEntity item : p.getItems()) {
                if (item.getId() == null) {
                    item.setId(UUID.randomUUID());
                }
                itemStore.put(item.getId(), item);
            }
            planStore.put(p.getId(), p);
            return p;
        });
        lenient().when(planRepository.findByIdWithItems(any())).thenAnswer(inv ->
                Optional.ofNullable(planStore.get(inv.getArgument(0))));
        lenient().when(planRepository.findByApplicationIdWithItems(any())).thenAnswer(inv ->
                planStore.values().stream()
                        .filter(p -> inv.getArgument(0).equals(p.getApplicationId()))
                        .collect(Collectors.toList()));
        lenient().when(planRepository.findByApplicationIdOrderByPlanVersionDesc(any())).thenAnswer(inv ->
                planStore.values().stream()
                        .filter(p -> inv.getArgument(0).equals(p.getApplicationId()))
                        .sorted((a, b) -> Integer.compare(b.getPlanVersion(), a.getPlanVersion()))
                        .collect(Collectors.toList()));
        lenient().when(itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(any())).thenAnswer(inv ->
                itemStore.values().stream()
                        .filter(i -> i.getPlan() != null && inv.getArgument(0).equals(i.getPlan().getId()))
                        .collect(Collectors.toList()));
        lenient().when(itemRepository.findByIdAndPlanId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.getArgument(0))));
        lenient().when(itemRepository.save(any())).thenAnswer(inv -> {
            RequirementItemEntity i = inv.getArgument(0);
            itemStore.put(i.getId(), i);
            return i;
        });
        lenient().when(transitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Map<String, Object> param(String id, boolean required, String... ruleRefs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalParameterId", id);
        m.put("resolutionStatus", CiPolicyRuleGraphOperand.RESOLVED);
        m.put("required", required);
        m.put("ruleReferences", List.of(ruleRefs));
        m.put("usageTypes", List.of(CiPolicyRuleGraphOperand.USAGE_HARD_RULE));
        m.put("businessName", id);
        return m;
    }

    private Map<String, Object> unresolved(String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalParameterId", null);
        m.put("originalToken", token);
        m.put("resolutionStatus", CiPolicyRuleGraphOperand.UNRESOLVED_CANONICAL_PARAMETER);
        m.put("required", true);
        m.put("ruleReferences", List.of("node-unresolved"));
        m.put("usageTypes", List.of(CiPolicyRuleGraphOperand.USAGE_HARD_RULE));
        return m;
    }

    private Map<String, Object> inventory(List<Map<String, Object>> parameters) {
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("graphPresent", true);
        inv.put("graphId", UUID.randomUUID().toString());
        inv.put("graphHash", "hash-" + parameters.size());
        inv.put("unresolvedOperandCount", parameters.stream()
                .filter(p -> p.get("canonicalParameterId") == null).count());
        inv.put("parameters", parameters);
        inv.put("inventoryState", "LOADED_WITH_PARAMETERS");
        inv.put("derivedFrom", "PERSISTED_POLICY_GRAPH");
        return inv;
    }

    private RequirementPlanEntity planWith(Map<String, Object> hints) {
        PlanInputs inputs = new PlanInputs(
                appId, policyId, workflowId, null, null, "1.0", null, hints);
        return planner.plan(inputs);
    }

    @Test
    void policyGraphInventoryDrivesRequirements_exactGacatIds() {
        List<Map<String, Object>> params = List.of(
                param("bureau.score", true, "r1"),
                param("bureau.score", true, "r2"), // duplicate operand — inventory builder dedupes by key
                param("kyc.pan.verified", true, "r3")
        );
        // Simulate DP-3 inventory already deduped
        Map<String, Object> inv = inventory(List.of(
                param("bureau.score", true, "r1", "r2"),
                param("kyc.pan.verified", true, "r3")));

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inv);
        hints.put("automaticSourceParameterIds", List.of("bureau.score"));
        hints.put("existingFacts", Map.of(
                "kyc.pan.verified", Map.of("readyForPolicy", true, "provenance", Map.of("kycStep", "PAN_VERIFY"))));

        RequirementPlanEntity plan = planWith(hints);
        assertEquals(2, plan.getItems().size());
        assertTrue(plan.getItems().stream().allMatch(i ->
                i.getCanonicalParameterId() != null && !i.getCanonicalParameterId().contains(" ")));
        assertEquals("PERSISTED_POLICY_GRAPH", plan.getMetadata().get("derivedFrom"));
    }

    @Test
    void twelveItemGolden() {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(param("ready.a", true, "r"));
        params.add(param("ready.b", true, "r"));
        params.add(param("ready.c", true, "r"));
        params.add(param("ready.d", true, "r"));
        params.add(param("auto.x", true, "r"));
        params.add(param("auto.y", true, "r"));
        params.add(param("auto.z", true, "r"));
        params.add(param("deriv.p", true, "r"));
        params.add(param("deriv.q", true, "r"));
        params.add(param("application.business_vintage_months", true, "r")); // A direct
        params.add(param("financial.revenue", true, "r")); // B document
        params.add(param("customer.dual", true, "r")); // C both

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(params));
        hints.put("existingFacts", Map.of(
                "ready.a", Map.of("readyForPolicy", true),
                "ready.b", Map.of("readyForPolicy", true),
                "ready.c", Map.of("readyForPolicy", true),
                "ready.d", Map.of("readyForPolicy", true)));
        hints.put("automaticSourceParameterIds", List.of("auto.x", "auto.y", "auto.z"));
        hints.put("derivationParameterIds", List.of("deriv.p", "deriv.q"));
        hints.put("workflowDocumentRequirements", List.of("FINANCIAL_STATEMENTS", "OTHER_DOC"));
        hints.put("documentMappings", Map.of(
                "financial.revenue", "FINANCIAL_STATEMENTS",
                "customer.dual", "OTHER_DOC"));
        hints.put("allowDirectInputParameterIds", List.of(
                "application.business_vintage_months", "customer.dual"));
        hints.put("dualModeParameterIds", List.of("customer.dual"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        assertEquals(12, plan.getItems().size());

        RequirementDtos.PlanningSummary summary = planService.buildPlanningSummary(plan);
        assertEquals(4, summary.alreadySatisfiedCount());
        assertEquals(3, summary.automaticAcquisitionCandidateCount());
        assertEquals(2, summary.derivationCandidateCount());
        assertEquals(3, summary.customerRequestCount());
        assertTrue(summary.customerRequests().stream().anyMatch(c ->
                FulfilmentMode.DIRECT_INPUT.name().equals(c.mode())));
        assertTrue(summary.customerRequests().stream().anyMatch(c ->
                FulfilmentMode.DOCUMENT_UPLOAD.name().equals(c.mode())));
        assertTrue(summary.customerRequests().stream().anyMatch(c ->
                "DOCUMENT_UPLOAD_OR_DIRECT_INPUT".equals(c.mode())));
    }

    @Test
    void financialStatementsGolden_oneDocumentThreeParams() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(
                param("financial.revenue", true, "r1"),
                param("financial.ebitda", true, "r2"),
                param("financial.networth", true, "r3"))));
        hints.put("workflowDocumentRequirements", List.of("FINANCIAL_STATEMENTS"));
        hints.put("documentMappings", Map.of(
                "financial.revenue", "FINANCIAL_STATEMENTS",
                "financial.ebitda", "FINANCIAL_STATEMENTS",
                "financial.networth", "FINANCIAL_STATEMENTS"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        assertEquals(3, plan.getItems().size());
        for (RequirementItemEntity i : plan.getItems()) {
            assertEquals(RequirementClass.CUSTOMER_PROVIDED, i.getRequirementClass());
            assertTrue(i.allows(FulfilmentMode.DOCUMENT_UPLOAD));
            assertFalse(i.allows(FulfilmentMode.DIRECT_INPUT),
                    "Must not create three direct input fields for financials");
            assertEquals("FINANCIAL_STATEMENTS", i.getSourceHints().get("pendingDocumentGroup"));
            assertEquals(CustomerFulfilmentState.REQUESTED, i.getCustomerFulfilmentState());
            assertEquals(DataReadinessState.NOT_AVAILABLE, i.getDataReadinessState());
        }

        RequirementDtos.PlanningSummary summary = planService.buildPlanningSummary(plan);
        assertEquals(1, summary.customerRequestCount());
        assertEquals("FINANCIAL_STATEMENTS", summary.customerRequests().get(0).documentGroup());
        assertEquals(3, summary.customerRequests().get(0).linkedCanonicalParameterIds().size());

        // Simulated upload via W3 transition
        RequirementItemEntity first = plan.getItems().get(0);
        List<RequirementItemEntity> updated = transitionService.markDocumentUploaded(
                plan.getId(), first.getId(), "fs-1", null, "cust", null);
        assertEquals(3, updated.size());
        for (RequirementItemEntity i : updated) {
            assertEquals(CustomerFulfilmentState.PROVIDED, i.getCustomerFulfilmentState());
            assertEquals(DataReadinessState.PROCESSING, i.getDataReadinessState());
        }
    }

    @Test
    void bureauGolden_automaticOnly_noCustomerField() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("bureau.score", true, "bureau-rule"))));
        // Prefer real GACAT/workflow path; also allow force for determinism
        hints.put("automaticSourceParameterIds", List.of("bureau.score"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        RequirementItemEntity bureau = plan.getItems().get(0);
        assertEquals(RequirementClass.AUTO_SOURCE, bureau.getRequirementClass());
        assertTrue(bureau.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE));
        assertEquals(CustomerFulfilmentState.NOT_APPLICABLE, bureau.getCustomerFulfilmentState());
        assertFalse(bureau.allows(FulfilmentMode.DIRECT_INPUT));
        assertFalse(bureau.allows(FulfilmentMode.DOCUMENT_UPLOAD));

        assertThrows(BusinessRuleException.class, () ->
                transitionService.markDirectInput(plan.getId(), bureau.getId(), "hack", false, "x", null));
    }

    @Test
    void existingKycPanFactGolden_readyNoNewKyc() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("kyc.pan.verified", true, "kyc-rule"))));
        hints.put("existingFacts", Map.of(
                "kyc.pan.verified", Map.of(
                        "readyForPolicy", true,
                        "provenance", Map.of("source", "PAN_VERIFY", "kycStep", "PAN_VERIFY"))));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        RequirementItemEntity item = plan.getItems().get(0);
        assertEquals(RequirementClass.ALREADY_AVAILABLE, item.getRequirementClass());
        assertEquals(DataReadinessState.READY_FOR_POLICY, item.getDataReadinessState());
        assertEquals(CustomerFulfilmentState.NOT_APPLICABLE, item.getCustomerFulfilmentState());
        assertTrue(item.getProvenance().containsKey("kycStep")
                || String.valueOf(item.getProvenance().get("source")).contains("PAN")
                || item.getProvenance().containsKey("existingFactPreferred")
                || item.getProvenance().get("factSource") != null
                || item.getProvenance().containsKey("source"));
        RequirementDtos.PlanningSummary summary = planService.buildPlanningSummary(plan);
        assertEquals(0, summary.customerRequestCount());
        assertEquals(1, summary.alreadySatisfiedCount());
    }

    @Test
    void noFulfilmentPath_blocksCompleteness() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("exotic.unsourceable.param.x", true, "r"))));
        hints.put("denyDirectInputParameterIds", List.of("exotic.unsourceable.param.x"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        RequirementItemEntity item = plan.getItems().get(0);
        assertEquals(RequirementClass.UNAVAILABLE_BLOCKER, item.getRequirementClass());
        assertEquals("NO_FULFILMENT_PATH", item.getSourceHints().get("blockingReason"));
        RequirementDtos.CompletenessResult c = completenessEvaluator.evaluate(plan);
        assertEquals(CompletenessStatus.BLOCKED, c.status());
        assertTrue(c.blockedCount() >= 1);
    }

    @Test
    void unresolvedOperand_failsClosed() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(unresolved("Some Fuzzy Token"))));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        RequirementItemEntity item = plan.getItems().get(0);
        assertEquals(RequirementClass.UNAVAILABLE_BLOCKER, item.getRequirementClass());
        assertEquals("UNRESOLVED_CANONICAL_PARAMETER", item.getSourceHints().get("blockingReason"));
        assertNull(item.getCanonicalParameterId());
        assertEquals(CompletenessStatus.BLOCKED, completenessEvaluator.evaluate(plan).status());
    }

    @Test
    void idempotentPlanning_sameSemanticHash() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("application.requested_amount", true, "r"))));
        hints.put("allowDirectInputParameterIds", List.of("application.requested_amount"));

        RequirementPlanEntity first = planWith(hints);
        String hash1 = String.valueOf(first.getMetadata().get("semanticHash"));
        UUID id1 = first.getId();

        RequirementPlanEntity second = planWith(hints);
        assertEquals(id1, second.getId());
        assertEquals(hash1, String.valueOf(second.getMetadata().get("semanticHash")));
        assertEquals(1, planStore.size());
    }

    @Test
    void controlledReplan_preservesProvidedDocument() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(
                param("financial.revenue", true, "r1"),
                param("financial.ebitda", true, "r2"),
                param("financial.networth", true, "r3"))));
        hints.put("workflowDocumentRequirements", List.of("FINANCIAL_STATEMENTS"));
        hints.put("documentMappings", Map.of(
                "financial.revenue", "FINANCIAL_STATEMENTS",
                "financial.ebitda", "FINANCIAL_STATEMENTS",
                "financial.networth", "FINANCIAL_STATEMENTS"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity first = planWith(hints);
        RequirementItemEntity revenue = first.getItems().stream()
                .filter(i -> "financial.revenue".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        transitionService.markDocumentUploaded(first.getId(), revenue.getId(), "fs-up", null, "c", null);
        transitionService.advanceReadiness(first.getId(), revenue.getId(),
                DataReadinessState.READY_FOR_POLICY, "parser", null);
        RequirementItemEntity ebitda = first.getItems().stream()
                .filter(i -> "financial.ebitda".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        // ebitda stays PROCESSING after upload
        RequirementItemEntity networth = first.getItems().stream()
                .filter(i -> "financial.networth".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        transitionService.advanceReadiness(first.getId(), networth.getId(),
                DataReadinessState.DATA_INSUFFICIENT, "parser", null);

        Map<String, Object> replanHints = new LinkedHashMap<>(hints);
        replanHints.put("forceNewPlan", true);
        PlanInputs replanInputs = new PlanInputs(
                appId, policyId, workflowId, null, null, "1.0", first.getId(), replanHints);
        RequirementPlanEntity second = planner.plan(replanInputs);

        assertEquals(2, second.getPlanVersion());
        assertEquals(RequirementPlanStatus.SUPERSEDED, first.getStatus());

        RequirementItemEntity rev2 = second.getItems().stream()
                .filter(i -> "financial.revenue".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        assertEquals(CustomerFulfilmentState.PROVIDED, rev2.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.READY_FOR_POLICY, rev2.getDataReadinessState());
        assertEquals("fs-up", rev2.getDocumentRef());

        RequirementItemEntity ebitda2 = second.getItems().stream()
                .filter(i -> "financial.ebitda".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        assertEquals(CustomerFulfilmentState.PROVIDED, ebitda2.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.PROCESSING, ebitda2.getDataReadinessState());

        RequirementItemEntity nw2 = second.getItems().stream()
                .filter(i -> "financial.networth".equals(i.getCanonicalParameterId())).findFirst().orElseThrow();
        assertEquals(CustomerFulfilmentState.PROVIDED, nw2.getCustomerFulfilmentState());
        assertEquals(DataReadinessState.DATA_INSUFFICIENT, nw2.getDataReadinessState());

        // Do not create a second FINANCIAL_STATEMENTS customer request while PROVIDED
        RequirementDtos.PlanningSummary summary = planService.buildPlanningSummary(second);
        assertEquals(0, summary.customerRequestCount());
    }

    @Test
    void productionReadinessRetainedOnItems() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("bureau.score", true, "r"))));
        hints.put("automaticSourceParameterIds", List.of("bureau.score"));
        hints.put("forceNewPlan", true);
        RequirementPlanEntity plan = planWith(hints);
        assertNotNull(plan.getItems().get(0).getSourceHints().get("productionReadiness"));
    }

    @Test
    void scorecardCannotIntroduceOutsidePolicyParameters() {
        Map<String, Object> inv = inventory(List.of(param("bureau.score", true, "r")));
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inv);
        hints.put("scorecardCanonicalParameterIds", List.of("bureau.score", "outside.policy.param"));
        hints.put("automaticSourceParameterIds", List.of("bureau.score"));
        hints.put("forceNewPlan", true);

        RequirementPlanEntity plan = planWith(hints);
        assertEquals(1, plan.getItems().size());
        assertEquals("bureau.score", plan.getItems().get(0).getCanonicalParameterId());
        assertFalse(plan.getItems().stream().anyMatch(i ->
                "outside.policy.param".equals(i.getCanonicalParameterId())));
    }

    @Test
    void explainabilityFieldsPresent() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("policyInventory", inventory(List.of(param("application.requested_amount", true, "rule-9"))));
        hints.put("allowDirectInputParameterIds", List.of("application.requested_amount"));
        hints.put("forceNewPlan", true);
        RequirementPlanEntity plan = planWith(hints);
        RequirementDtos.CandidateSummary exp = planService.buildPlanningSummary(plan).explanations().get(0);
        assertNotNull(exp.explanation().get("whyRequired"));
        assertNotNull(exp.explanation().get("whyChosen"));
        assertNotNull(exp.explanation().get("canonicalParameterId"));
        assertNotNull(exp.explanation().get("currentCustomerFulfilment"));
        assertNotNull(exp.explanation().get("currentDataReadiness"));
    }
}
