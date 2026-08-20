package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.exception.BusinessRuleException;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * STEP-2 Category → Policy Version bind — config only; no live routing.
 */
@ExtendWith(MockitoExtension.class)
class CategoryPolicyVersionBindTest {

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
    CategoryPolicyBindService policyBindService;
    CustomerCategoryService categoryService;
    Actor actor;

    UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID appId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(creditIntelligenceProperties.getDefaultTenantId()).thenReturn(tenant);
        lenient().when(workflowBindService.workflowActivationChecks(any())).thenReturn(List.of());
        validator = new CustomerCategoryValidator(ruleSetRepository, scorecardRepository);
        policyBindService = new CategoryPolicyBindService(
                applicabilityRepository, policyCatalogueService, creditIntelligenceProperties);
        categoryService = new CustomerCategoryService(
                categoryRepository, policySetRepository, validator, auditSupport,
                policyBindService, workflowBindService, applicabilityRepository);
        actor = new Actor("u1", "Maker", "CREDIT_MANAGER");
    }

    private CiPolicyApplicability approvedPolicy() {
        return CiPolicyApplicability.builder()
                .id(appId)
                .tenantId(tenant)
                .policyDocumentId(docId)
                .policyName("Starter Credit Policy")
                .policyVersionLabel("v1")
                .businessStatus("APPROVED")
                .dataReadinessStatus("PASSED")
                .products(List.of("BUSINESS_TERM_LOAN"))
                .borrowerTypes(List.of("INDIVIDUAL"))
                .build();
    }

    @Test
    void incompatiblePolicySaveBlockedWithTypedError() {
        CiPolicyApplicability bad = approvedPolicy();
        bad.setBorrowerTypes(List.of("COMPANY"));
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(bad));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.createDraft(new CategoryRequest(
                        "CC_BAD", "Bad", null,
                        "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                        new BigDecimal("20000"), new BigDecimal("500000"),
                        null, null, null, null,
                        null, null, appId, docId, "v1"), actor));
        assertEquals(CustomerCategoryPolicyScopeCompatibility.POLICY_SCOPE_INCOMPATIBLE, ex.getReason());
        assertNotNull(ex.getContext());
        assertTrue(String.valueOf(ex.getContext().get("reasons")).contains(
                CustomerCategoryPolicyScopeCompatibility.ENTITY_TYPE_NOT_COVERED));
    }

    @Test
    void draftCategoryCanReferencePolicy() {
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));
        when(categoryRepository.findByCodeAndVersionNo("CC_POL", 1)).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerCategoryEntity> cap = ArgumentCaptor.forClass(CustomerCategoryEntity.class);
        when(categoryRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_POL", "Pol Cat", null,
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"),
                null, null, null, null,
                null, null, appId, docId, "v1"), actor);

        assertEquals("DRAFT", res.status());
        assertEquals(appId, res.policyApplicabilityId());
        assertEquals(docId, res.policyDocumentId());
        assertEquals("v1", res.policyVersionLabel());
        assertEquals("LINKED", res.policyLinkageStatus());
        assertNull(cap.getValue().getPolicySetId());
        verify(auditSupport).captureCreate(eq("CUSTOMER_CATEGORY"), any(), any(), any());
    }

    @Test
    void exactPolicyVersionReferencePreserved() {
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));
        CategoryPolicyBindService.ResolvedPolicyBind bind =
                policyBindService.resolveBind(appId, docId, "v1");
        assertEquals("v1", bind.versionLabel());
        assertEquals(docId, bind.documentId());
        assertEquals(appId, bind.applicabilityId());
    }

    @Test
    void invalidPolicyFailsTypedValidation() {
        when(applicabilityRepository.findById(any())).thenReturn(Optional.empty());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> policyBindService.requireApplicability(UUID.randomUUID()));
        assertEquals(CategoryPolicyBindService.POLICY_NOT_FOUND, ex.getReason());
    }

    @Test
    void invalidPolicyVersionLabelFails() {
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> policyBindService.resolveBind(appId, docId, "v99"));
        assertEquals(CategoryPolicyBindService.POLICY_VERSION_LABEL_MISMATCH, ex.getReason());
    }

    @Test
    void categoryLinkageDoesNotRequirePolicySet() {
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_NO_PS", "No PS", null,
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                null, null, null, null, null, null,
                null, null, appId, null, null), actor);
        assertNull(res.policySetId());
        assertEquals("LINKED", res.policyLinkageStatus());
    }

    @Test
    void day1StyleCategoryWithoutPolicyShowsLinkageRequired() {
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_DAY1").versionNo(1).name("Day1")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("PL").intakeSegment("BORROWER")
                .policySetId(UUID.randomUUID())
                .governanceJson(new LinkedHashMap<>())
                .build();
        assertEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, CategoryPolicyBindService.linkageStatus(e));
        when(categoryRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(policySetRepository.findById(e.getPolicySetId())).thenReturn(Optional.of(
                PolicySetEntity.builder().id(e.getPolicySetId()).code("PS").versionNo(1).name("PS")
                        .status(ConfigLifecycleStatus.DRAFT).primaryRuleSetId(UUID.randomUUID()).build()));

        var ready = categoryService.activationReadiness(e.getId());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "POLICY_SELECTED".equals(c.code()) && !c.ok()));
        assertTrue(ready.checks().stream().anyMatch(c ->
                c.detail() != null && c.detail().contains("POLICY LINKAGE REQUIRED")));
    }

    @Test
    void identicalDimensionsDifferentPoliciesValidAsAlsoEligible() {
        var a = new CustomerCategoryOverlapDetector.CategoryCriteria(
                "A", "Starter", "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"));
        var b = new CustomerCategoryOverlapDetector.CategoryCriteria(
                "B", "Bank Starter", "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"));
        assertEquals(1, CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b)).size());
        // Warning only — not a configuration conflict / activation block
    }

    @Test
    void policyPickerUsesCatalogue() {
        when(policyCatalogueService.listCatalogue(tenant)).thenReturn(List.of(Map.of(
                "applicabilityId", appId.toString(),
                "documentId", docId.toString(),
                "policyName", "Starter Credit Policy",
                "policyVersion", "v1",
                "status", "APPROVED",
                "products", List.of("BUSINESS_TERM_LOAN"),
                "borrowerTypes", List.of("INDIVIDUAL"),
                "dataReadinessStatus", "PASSED",
                "productionAuthority", "DISABLED",
                "allowCanonicalAuthority", false
        )));
        var views = policyBindService.listEligiblePolicies(
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER", null, null);
        assertEquals(1, views.size());
        assertTrue(views.get(0).compatibleWithCategory());
        assertEquals(appId, views.get(0).policyApplicabilityId());
        assertTrue(views.get(0).eligibleForCategoryLinkage());
        assertEquals(PolicyCanonicalLifecycleAuthority.NAME, views.get(0).lifecycleAuthority());
        assertEquals(PolicyCanonicalLifecycleAuthority.OWNER_TYPE, views.get(0).linkageOwnerType());
    }

    @Test
    void policyPicker_intakeRelationshipBorrower_selectable() {
        Map<String, Object> row = new LinkedHashMap<>(Map.of(
                "applicabilityId", appId.toString(),
                "documentId", docId.toString(),
                "policyName", "Vikasam Bureau",
                "policyVersion", "v1",
                "status", "APPROVED",
                "products", List.of("BUSINESS_TERM_LOAN"),
                "borrowerTypes", List.of("INDIVIDUAL"),
                "customerSegment", "BORROWER"
        ));
        when(policyCatalogueService.listCatalogue(tenant)).thenReturn(List.of(row));
        var views = policyBindService.listEligiblePolicies(
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("10000"), new BigDecimal("1000000"));
        assertEquals(1, views.size());
        assertTrue(views.get(0).eligibleForCategoryLinkage());
        assertTrue(views.get(0).compatibleWithCategory());
        assertEquals("COMPATIBLE", views.get(0).compatibilityStatus());
    }

    @Test
    void draftPolicy_notEligibleForCategoryLinkage() {
        when(policyCatalogueService.listCatalogue(tenant)).thenReturn(List.of(Map.of(
                "applicabilityId", appId.toString(),
                "documentId", docId.toString(),
                "policyName", "Draft Only Policy",
                "policyVersion", "v1",
                "status", "DRAFT",
                "products", List.of("BUSINESS_TERM_LOAN"),
                "borrowerTypes", List.of("INDIVIDUAL")
        )));
        var views = policyBindService.listEligiblePolicies(
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER", null, null);
        assertEquals(1, views.size());
        assertFalse(views.get(0).eligibleForCategoryLinkage());
        assertEquals("DRAFT_NOT_ELIGIBLE", views.get(0).ineligibleReason());
    }

    @Test
    void resolveBind_rejectsDraftLifecycle() {
        CiPolicyApplicability draft = approvedPolicy();
        draft.setBusinessStatus("DRAFT");
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(draft));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                policyBindService.resolveBind(appId, docId, "v1"));
        assertEquals(CategoryPolicyBindService.POLICY_LIFECYCLE_NOT_ELIGIBLE, ex.getReason());
    }

    @Test
    void noAutomaticRsToPolicyGuessing() {
        // Creating without policyApplicabilityId leaves LINKAGE_REQUIRED — no RS UUID inference
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_NO_GUESS", "No Guess", null,
                "INDIVIDUAL", "PL", "BORROWER",
                null, null, null, null, null, null), actor);
        assertEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, res.policyLinkageStatus());
        assertNull(res.policyApplicabilityId());
        verify(applicabilityRepository, never()).findById(any());
    }

    @Test
    void submitWithoutPersistedLink_rejectedAndStaysDraft() {
        UUID id = UUID.randomUUID();
        CustomerCategoryEntity e = draftUnlinked(id);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(e));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                categoryService.submit(id, new LifecycleActionRequest("please review", null), actor));
        assertEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, ex.getReason());
        assertEquals(ConfigLifecycleStatus.DRAFT, e.getStatus());
        assertEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, CategoryPolicyBindService.linkageStatus(e));
    }

    @Test
    void submitWithPendingApplicability_persistsCanonicalLinkAndTransitions() {
        UUID id = UUID.randomUUID();
        CustomerCategoryEntity e = draftUnlinked(id);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));

        CategoryResponse res = categoryService.submit(id,
                new LifecycleActionRequest("please review", null, appId, docId, "v1"), actor);

        assertEquals("IN_REVIEW", res.status());
        assertEquals("LINKED", res.policyLinkageStatus());
        assertEquals(appId, res.policyApplicabilityId());
        assertEquals(docId, res.policyDocumentId());
        assertEquals("v1", res.policyVersionLabel());
        assertEquals(appId, e.getPolicyApplicabilityId());
        assertEquals(ConfigLifecycleStatus.IN_REVIEW, e.getStatus());
        assertNotEquals(CategoryPolicyBindService.LINKAGE_REQUIRED, res.policyLinkageStatus());
    }

    @Test
    void updateThenReadBack_sameApplicabilityId() {
        UUID id = UUID.randomUUID();
        CustomerCategoryEntity e = draftUnlinked(id);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));

        CategoryResponse saved = categoryService.update(id, new CategoryRequest(
                e.getCode(), e.getName(), null,
                e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                e.getMinAmount(), e.getMaxAmount(),
                null, null, null, null,
                null, null, appId, docId, "v1"), actor);
        assertEquals(appId, saved.policyApplicabilityId());
        assertEquals("LINKED", saved.policyLinkageStatus());

        CategoryResponse read = categoryService.get(id);
        assertEquals(saved.policyApplicabilityId(), read.policyApplicabilityId());
        assertEquals("LINKED", read.policyLinkageStatus());
    }

    @Test
    void updatePolicyLink_doesNotFailWhenPersistedWorkflowVersionIsStale() {
        UUID id = UUID.randomUUID();
        UUID wfId = UUID.randomUUID();
        CustomerCategoryEntity e = draftUnlinked(id);
        e.setWorkflowId(wfId);
        e.setWorkflowVersion(1);
        e.setWorkflowName("Vikasam Business Loan");
        when(categoryRepository.findById(id)).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));

        CategoryResponse saved = categoryService.update(id, new CategoryRequest(
                e.getCode(), e.getName(), null,
                e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                e.getMinAmount(), e.getMaxAmount(),
                null, null, null, null,
                null, null, appId, docId, "v1",
                wfId, 1, null), actor);
        assertEquals("LINKED", saved.policyLinkageStatus());
        assertEquals(appId, saved.policyApplicabilityId());
        assertEquals(1, e.getWorkflowVersion());
        assertEquals(wfId, e.getWorkflowId());
        verify(workflowBindService, never()).resolveBind(any(), any());
        verify(workflowBindService, never()).applyBind(any(), any());
    }

    @Test
    void wrongApplicabilityId_rejectedDeterministically() {
        UUID missing = UUID.randomUUID();
        when(applicabilityRepository.findById(missing)).thenReturn(Optional.empty());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                policyBindService.resolveBind(missing, docId, "v1"));
        assertEquals(CategoryPolicyBindService.POLICY_NOT_FOUND, ex.getReason());
    }

    private static CustomerCategoryEntity draftUnlinked(UUID id) {
        return CustomerCategoryEntity.builder()
                .id(id)
                .code("VIKCAT_T")
                .versionNo(1)
                .name("Vikasan Bureau")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("1000000"))
                .governanceJson(new LinkedHashMap<>())
                .build();
    }
}
