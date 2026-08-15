package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetRequest;
import com.los.core.customercategory.CustomerCategoryDtos.SeedApplyResponse;
import com.los.core.customercategory.CustomerCategoryDtos.SeedPreviewResponse;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.CategoryCriteria;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.OverlapWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CUSTOMER-CATEGORY-IMPLEMENTATION-STEP1 mandatory coverage.
 * Live underwriting is not invoked — Step 1 is configuration-only.
 */
@ExtendWith(MockitoExtension.class)
class CustomerCategoryStep1Test {

    @Mock UnderwritingRuleSetRepository ruleSetRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock PolicySetRepository policySetRepository;
    @Mock CustomerCategoryRepository categoryRepository;
    @Mock AdminConfigAuditSupport auditSupport;
    @Mock CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock PolicyCatalogueService policyCatalogueService;
    @Mock CreditIntelligenceProperties creditIntelligenceProperties;
    @Mock CategoryWorkflowBindService workflowBindService;

    CustomerCategoryValidator validator;
    PolicySetService policySetService;
    CustomerCategoryService categoryService;
    CustomerCategorySeedService seedService;
    CategoryPolicyBindService policyBindService;

    Actor actor;
    UUID policyAppId = UUID.randomUUID();
    UUID policyDocId = UUID.randomUUID();
    UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(creditIntelligenceProperties.getDefaultTenantId()).thenReturn(tenant);
        lenient().when(workflowBindService.workflowActivationChecks(any())).thenReturn(List.of());
        validator = new CustomerCategoryValidator(ruleSetRepository, scorecardRepository);
        policySetService = new PolicySetService(policySetRepository, categoryRepository, validator, auditSupport);
        policyBindService = new CategoryPolicyBindService(
                applicabilityRepository, policyCatalogueService, creditIntelligenceProperties);
        categoryService = new CustomerCategoryService(
                categoryRepository, policySetRepository, validator, auditSupport,
                policyBindService, workflowBindService, applicabilityRepository);
        seedService = new CustomerCategorySeedService(
                ruleSetRepository, scorecardRepository, policySetRepository, categoryRepository, auditSupport);
        actor = new Actor("jwt-user-42", "CM Reviewer", "CREDIT_MANAGER");
        lenient().when(applicabilityRepository.findById(policyAppId)).thenReturn(Optional.of(
                CiPolicyApplicability.builder()
                        .id(policyAppId).tenantId(tenant).policyDocumentId(policyDocId)
                        .policyName("Step1 Policy").policyVersionLabel("v1")
                        .businessStatus("APPROVED").dataReadinessStatus("PASSED")
                        .products(List.of()).borrowerTypes(List.of()).build()));
    }

    // --- 1 create valid DRAFT ---
    @Test
    void createValidDraftCategory() {
        UUID rsId = UUID.randomUUID();
        UUID psId = stubLivePolicySet(rsId);
        when(categoryRepository.findByCodeAndVersionNo("CC_TEST", 1)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_TEST", "Test Cat", "desc",
                "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER",
                new BigDecimal("1000"), new BigDecimal("50000"), psId, null, null, null), actor);

        assertEquals("DRAFT", res.status());
        assertEquals("INDIVIDUAL", res.borrowerType());
        assertEquals(psId, res.policySetId());
        assertEquals("CM Reviewer", res.createdBy());
        verify(auditSupport).captureCreate(eq("CUSTOMER_CATEGORY"), any(), any(), any());
    }

    // --- 2–4 ANY dims ---
    @Test
    void anyBorrowerTypeAccepted() {
        UUID psId = stubLivePolicySet(UUID.randomUUID());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_ANY_B", "Any B", null, "ANY", "PERSONAL_LOAN", "BORROWER",
                null, null, psId, null, null, null), actor);
        assertEquals(MatchWildcard.ANY, res.borrowerType());
    }

    @Test
    void anyProductAccepted() {
        UUID psId = stubLivePolicySet(UUID.randomUUID());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_ANY_P", "Any P", null, "COMPANY", "ANY", "ANCHOR",
                null, null, psId, null, null, null), actor);
        assertEquals(MatchWildcard.ANY, res.loanProduct());
    }

    @Test
    void anyIntakeAccepted() {
        UUID psId = stubLivePolicySet(UUID.randomUUID());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_ANY_I", "Any I", null, "COMPANY", "TERM_LOAN", "ANY",
                null, null, psId, null, null, null), actor);
        assertEquals(MatchWildcard.ANY, res.intakeSegment());
    }

    // --- 5–7 null amounts ---
    @Test
    void nullMinimumUnbounded() {
        assertDoesNotThrow(() -> validator.validateAmountRange(null, new BigDecimal("100")));
    }

    @Test
    void nullMaximumUnbounded() {
        assertDoesNotThrow(() -> validator.validateAmountRange(new BigDecimal("100"), null));
    }

    @Test
    void bothNullAmountsUnbounded() {
        assertDoesNotThrow(() -> validator.validateAmountRange(null, null));
    }

    // --- 8–9 inclusive boundaries (match helper) ---
    @Test
    void exactMinimumBoundaryIncluded() {
        assertTrue(CustomerCategoryOverlapDetector.amountInRange(
                new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("5000")));
    }

    @Test
    void exactMaximumBoundaryIncluded() {
        assertTrue(CustomerCategoryOverlapDetector.amountInRange(
                new BigDecimal("5000"), new BigDecimal("1000"), new BigDecimal("5000")));
    }

    // --- 10 invalid min > max ---
    @Test
    void invalidMinGreaterThanMaxRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> validator.validateAmountRange(new BigDecimal("900"), new BigDecimal("100")));
        assertEquals("INVALID_AMOUNT_RANGE", ex.getReason());
    }

    // --- 11 Policy Set no longer required for Category composition ---
    @Test
    void categoryDoesNotRequirePolicySet() {
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_NO_PS", "No PS", null, "INDIVIDUAL", "PL", "BORROWER",
                null, null, null, null, null, null), actor);
        assertEquals("DRAFT", res.status());
        assertNull(res.policySetId());
        assertEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, res.policyLinkageStatus());
    }

    // --- 12–13 Policy Set live rule set refs ---
    @Test
    void policySetReferencesExistingLiveRuleSet() {
        UUID rsId = UUID.randomUUID();
        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(activeRuleSet(rsId, "INDIVIDUAL", "PL")));
        when(policySetRepository.findByCodeAndVersionNo("PS1", 1)).thenReturn(Optional.empty());
        when(policySetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = policySetService.createDraft(new PolicySetRequest("PS1", "PS One", null, rsId, List.of(), null, null, null, null), actor);
        assertEquals("DRAFT", res.status());
        assertEquals(rsId, res.primaryRuleSetId());
    }

    @Test
    void invalidRuleSetRejected() {
        UUID missing = UUID.randomUUID();
        when(ruleSetRepository.findById(missing)).thenReturn(Optional.empty());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> policySetService.createDraft(new PolicySetRequest("PS_BAD", "Bad", null, missing, List.of(), null, null, null, null), actor));
        assertEquals("RULE_SET_NOT_FOUND", ex.getReason());
    }

    @Test
    void inactiveRuleSetRejectedAsNotLiveReady() {
        UUID rsId = UUID.randomUUID();
        UnderwritingRuleSet inactive = activeRuleSet(rsId, "INDIVIDUAL", "PL");
        inactive.setActive(false);
        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(inactive));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> policySetService.createDraft(new PolicySetRequest("PS_INACTIVE", "Bad", null, rsId, List.of(), null, null, null, null), actor));
        assertEquals("RULE_SET_NOT_LIVE_READY", ex.getReason());
    }

    // --- 14–17 overlap ---
    @Test
    void overlapExactExact() {
        CategoryCriteria a = crit("A", "INDIVIDUAL", "PL", "BORROWER", "100", "500");
        CategoryCriteria b = crit("B", "INDIVIDUAL", "PL", "BORROWER", "200", "600");
        List<OverlapWarning> w = CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b));
        assertEquals(1, w.size());
        assertTrue(w.get(0).reasons().stream().anyMatch(r -> r.contains("amount")));
    }

    @Test
    void overlapAnyExact() {
        CategoryCriteria a = crit("A", "ANY", "PL", "BORROWER", "0", "1000");
        CategoryCriteria b = crit("B", "INDIVIDUAL", "PL", "BORROWER", "100", "200");
        assertEquals(1, CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b)).size());
    }

    @Test
    void overlappingAmountBands() {
        CategoryCriteria a = crit("A", "COMPANY", "TL", "ANY", "0", "100");
        CategoryCriteria b = crit("B", "COMPANY", "TL", "ANY", "100", "200"); // inclusive touch
        assertEquals(1, CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b)).size());
    }

    @Test
    void nonOverlappingAmountBands() {
        CategoryCriteria a = crit("A", "COMPANY", "TL", "ANY", "0", "99");
        CategoryCriteria b = crit("B", "COMPANY", "TL", "ANY", "100", "200");
        assertTrue(CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b)).isEmpty());
    }

    // --- 18–20 seed ---
    @Test
    void seedDeterministicAndDraftOnlyAndIdempotent() {
        UUID rsId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UnderwritingRuleSet rs = activeRuleSet(rsId, "INDIVIDUAL", "PERSONAL_LOAN");
        rs.setMinAmount(new BigDecimal("10000"));
        rs.setMaxAmount(new BigDecimal("200000"));
        when(ruleSetRepository.findAll()).thenReturn(List.of(rs));
        when(scorecardRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                any(), any())).thenReturn(List.of());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(policySetRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());

        SeedPreviewResponse p1 = seedService.preview();
        SeedPreviewResponse p2 = seedService.preview();
        assertEquals(1, p1.candidateCount());
        assertEquals(p1.candidates().get(0).categoryCode(), p2.candidates().get(0).categoryCode());
        assertEquals(CustomerCategorySeedService.categoryCode(rsId), p1.candidates().get(0).categoryCode());
        assertEquals("REVIEW_REQUIRED", p1.candidates().get(0).reviewStatus());
        assertEquals(MatchWildcard.ANY, p1.candidates().get(0).intakeSegment());

        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(rs));
        when(policySetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            CustomerCategoryEntity e = inv.getArgument(0);
            assertEquals(ConfigLifecycleStatus.DRAFT, e.getStatus());
            return e;
        });

        SeedApplyResponse apply1 = seedService.applyDrafts(actor);
        assertEquals(1, apply1.createdCategories());
        assertTrue(apply1.note().contains("DRAFT"));

        // second apply: already seeded (either PS or CC present marks alreadySeeded)
        when(categoryRepository.findByCodeAndVersionNo(CustomerCategorySeedService.categoryCode(rsId), 1))
                .thenReturn(Optional.of(CustomerCategoryEntity.builder()
                        .id(UUID.randomUUID()).code(CustomerCategorySeedService.categoryCode(rsId))
                        .versionNo(1).name("x").status(ConfigLifecycleStatus.DRAFT)
                        .borrowerType("INDIVIDUAL").loanProduct("PERSONAL_LOAN").intakeSegment("ANY")
                        .policySetId(UUID.randomUUID()).build()));

        SeedApplyResponse apply2 = seedService.applyDrafts(actor);
        assertEquals(0, apply2.createdCategories());
        assertTrue(apply2.skippedExisting() >= 1);
    }

    // --- 21 seed does not change live underwriting (structural: no CreditControl touch) ---
    @Test
    void seedDoesNotChangeLiveUnderwriting() {
        // Step-1 seed only writes policy_set / customer_category; never mutates rule sets.
        UUID rsId = UUID.randomUUID();
        UnderwritingRuleSet rs = activeRuleSet(rsId, "COMPANY", "TERM_LOAN");
        when(ruleSetRepository.findAll()).thenReturn(List.of(rs));
        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(rs));
        when(scorecardRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(any(), any()))
                .thenReturn(List.of());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(policySetRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(policySetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        seedService.applyDrafts(actor);
        verify(ruleSetRepository, never()).save(any());
        verify(scorecardRepository, never()).save(any());
        assertTrue(rs.isActive());
    }

    // --- 22 ACTIVE matching protected ---
    @Test
    void activeCategoryProtectedFromInPlaceMatchingEdit() {
        UUID id = UUID.randomUUID();
        UUID psId = UUID.randomUUID();
        CustomerCategoryEntity active = CustomerCategoryEntity.builder()
                .id(id).code("CC_ACT").versionNo(1).name("Live Cat")
                .status(ConfigLifecycleStatus.ACTIVE)
                .borrowerType("INDIVIDUAL").loanProduct("PL").intakeSegment("BORROWER")
                .minAmount(new BigDecimal("1000")).maxAmount(new BigDecimal("10000"))
                .policySetId(psId).build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(active));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.update(id, new CategoryRequest(
                        "CC_ACT", "Live Cat", null, "COMPANY", "PL", "BORROWER",
                        new BigDecimal("1000"), new BigDecimal("10000"), psId, null, null, null), actor));
        assertEquals("ACTIVE_CATEGORY_IMMUTABLE", ex.getReason());
    }

    // --- 23 RETIRED lifecycle ---
    @Test
    void retiredLifecycleBlocksEdit() {
        UUID id = UUID.randomUUID();
        CustomerCategoryEntity retired = CustomerCategoryEntity.builder()
                .id(id).code("CC_RET").versionNo(1).name("Old")
                .status(ConfigLifecycleStatus.RETIRED)
                .borrowerType("INDIVIDUAL").loanProduct("PL").intakeSegment("ANY")
                .policySetId(UUID.randomUUID()).build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(retired));
        when(categoryRepository.findAll()).thenReturn(List.of(retired));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.update(id, new CategoryRequest(
                        "CC_RET", "Old2", null, "INDIVIDUAL", "PL", "ANY", null, null,
                        retired.getPolicySetId(), null, null, null), actor));
        assertEquals("CATEGORY_RETIRED", ex.getReason());

        CategoryResponse again = categoryService.retire(id, new LifecycleActionRequest(null, "retired"), actor);
        assertEquals("RETIRED", again.status());
    }

    // --- 24 audit actor from security context (not body) ---
    @Test
    void auditActorFromSecurityContext() {
        UUID rsId = UUID.randomUUID();
        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(activeRuleSet(rsId, "INDIVIDUAL", "PL")));
        when(policySetRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        ArgumentCaptor<PolicySetEntity> cap = ArgumentCaptor.forClass(PolicySetEntity.class);
        when(policySetRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        Actor fromJwt = new Actor("auth-sub-99", "Verified User", "ADMIN");
        policySetService.createDraft(new PolicySetRequest("PS_AUD", "Aud", null, rsId, List.of(), null, null, null, null), fromJwt);
        assertEquals("Verified User", cap.getValue().getCreatedBy());
        verify(auditSupport).captureCreate(eq("POLICY_SET"), any(), any(), any());
    }

    @Test
    void activateDoesNotBlockOnOverlapWarning() {
        UUID catId = UUID.randomUUID();
        UUID psId = UUID.randomUUID();
        CustomerCategoryEntity approved = CustomerCategoryEntity.builder()
                .id(catId).code("CC1").versionNo(1).name("C1")
                .status(ConfigLifecycleStatus.APPROVED)
                .borrowerType("ANY").loanProduct("ANY").intakeSegment("ANY")
                .policySetId(psId)
                .policyApplicabilityId(policyAppId)
                .policyDocumentId(policyDocId)
                .policyVersionLabel("v1")
                .workflowId(UUID.randomUUID())
                .workflowVersion(1)
                .workflowContentHash("test-workflow-hash")
                .workflowName("Test Journey")
                .governanceJson(new java.util.LinkedHashMap<>())
                .build();
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(approved));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // overlap warning only — findAll drives overlapReport; Policy readiness uses setUp stub
        when(categoryRepository.findAll()).thenReturn(List.of(approved));

        CategoryResponse res = categoryService.activate(catId, actor);
        assertEquals("ACTIVE", res.status());
    }

    // helpers
    private UUID stubLivePolicySet(UUID ruleSetId) {
        UUID psId = UUID.randomUUID();
        when(policySetRepository.findById(psId)).thenReturn(Optional.of(PolicySetEntity.builder()
                .id(psId).code("PS_X").versionNo(1).name("PS")
                .status(ConfigLifecycleStatus.DRAFT)
                .primaryRuleSetId(ruleSetId).build()));
        return psId;
    }

    private static UnderwritingRuleSet activeRuleSet(UUID id, String borrower, String product) {
        return UnderwritingRuleSet.builder()
                .id(id)
                .name(borrower + "-" + product)
                .borrowerType(borrower)
                .loanProduct(product)
                .priority(100)
                .active(true)
                .rulesJson(java.util.Map.of())
                .build();
    }

    private static CategoryCriteria crit(
            String code, String borrower, String product, String intake, String min, String max) {
        return new CategoryCriteria(code, code, borrower, product, intake,
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max));
    }
}
