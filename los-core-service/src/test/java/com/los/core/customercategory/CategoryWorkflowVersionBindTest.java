package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.workflow.WorkflowContentHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * W2 Category → Workflow Version bind — config only; no live routing.
 * application.workflow_id is unchanged (unit — no LoanApplication mocks).
 */
@ExtendWith(MockitoExtension.class)
class CategoryWorkflowVersionBindTest {

    @Mock UnderwritingRuleSetRepository ruleSetRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock PolicySetRepository policySetRepository;
    @Mock CustomerCategoryRepository categoryRepository;
    @Mock AdminConfigAuditSupport auditSupport;
    @Mock CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock PolicyCatalogueService policyCatalogueService;
    @Mock CreditIntelligenceProperties creditIntelligenceProperties;
    @Mock WorkflowConfigRepository workflowConfigRepository;

    CustomerCategoryValidator validator;
    CategoryPolicyBindService policyBindService;
    CategoryWorkflowBindService workflowBindService;
    CustomerCategoryService categoryService;
    Actor actor;

    UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID appId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID wfId = UUID.randomUUID();
    UUID wfId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(creditIntelligenceProperties.getDefaultTenantId()).thenReturn(tenant);
        validator = new CustomerCategoryValidator(ruleSetRepository, scorecardRepository);
        policyBindService = new CategoryPolicyBindService(
                applicabilityRepository, policyCatalogueService, creditIntelligenceProperties);
        workflowBindService = new CategoryWorkflowBindService(workflowConfigRepository);
        categoryService = new CustomerCategoryService(
                categoryRepository, policySetRepository, validator, auditSupport,
                policyBindService, workflowBindService, applicabilityRepository);
        actor = new Actor("u1", "Maker", "CREDIT_MANAGER");
    }

    private WorkflowConfig compatibleWorkflow(UUID id) {
        return WorkflowConfig.builder()
                .id(id)
                .name("Starter Journey")
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .version(1)
                .active(true)
                .workflowFamilyId(id)
                .publicationStatus("ACTIVE")
                .steps(List.of(
                        Map.of("stepKey", "INTAKE"),
                        Map.of("stepKey", "KYC"),
                        Map.of("stepKey", "UNDERWRITE")))
                .build();
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

    private CategoryRequest draftWithWorkflow(String code, UUID workflowId, Integer workflowVersion) {
        return new CategoryRequest(
                code, "WF Cat", null,
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"),
                null, null, null, null,
                null, null, null, null, null,
                workflowId, workflowVersion);
    }

    @Test
    void draftCategoryCanSelectWorkflowVersion() {
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(compatibleWorkflow(wfId)));
        when(categoryRepository.findByCodeAndVersionNo("CC_WF", 1)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(draftWithWorkflow("CC_WF", wfId, null), actor);

        assertEquals("DRAFT", res.status());
        assertEquals(wfId, res.workflowId());
        assertEquals(1, res.workflowVersion());
        assertEquals("Starter Journey", res.workflowName());
        assertEquals("LINKED", res.workflowLinkageStatus());
        assertNotNull(res.workflowContentHash());
        verify(auditSupport).captureCreate(eq("CUSTOMER_CATEGORY"), any(), any(), any());
    }

    @Test
    void exactWorkflowVersionPersisted() {
        WorkflowConfig cfg = compatibleWorkflow(wfId);
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(cfg));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerCategoryEntity> cap = ArgumentCaptor.forClass(CustomerCategoryEntity.class);
        when(categoryRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(draftWithWorkflow("CC_EXACT", wfId, 1), actor);

        assertEquals(wfId, res.workflowId());
        assertEquals(1, res.workflowVersion());
        assertEquals(WorkflowContentHash.of(cfg), res.workflowContentHash());
        assertEquals(wfId, cap.getValue().getWorkflowId());
        assertEquals(1, cap.getValue().getWorkflowVersion());
        assertEquals(WorkflowContentHash.of(cfg), cap.getValue().getWorkflowContentHash());
    }

    @Test
    void policyAndWorkflowIndependentBothSet() {
        when(applicabilityRepository.findById(appId)).thenReturn(Optional.of(approvedPolicy()));
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(compatibleWorkflow(wfId)));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_BOTH", "Both", null,
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"),
                null, null, null, null,
                null, null, appId, docId, "v1",
                wfId, 1), actor);

        assertEquals("LINKED", res.policyLinkageStatus());
        assertEquals(appId, res.policyApplicabilityId());
        assertEquals("LINKED", res.workflowLinkageStatus());
        assertEquals(wfId, res.workflowId());
    }

    @Test
    void invalidWorkflowBlocked() {
        when(workflowConfigRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.createDraft(draftWithWorkflow("CC_MISS", UUID.randomUUID(), null), actor));
        assertEquals(CategoryWorkflowBindService.WORKFLOW_NOT_FOUND, ex.getReason());
    }

    @Test
    void incompatibleWorkflowTypedReason() {
        WorkflowConfig bad = compatibleWorkflow(wfId);
        bad.setBorrowerType("COMPANY");
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(bad));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.createDraft(draftWithWorkflow("CC_INCOMP", wfId, null), actor));
        assertEquals(CategoryWorkflowCompatibility.WORKFLOW_SCOPE_INCOMPATIBLE, ex.getReason());
        assertNotNull(ex.getContext());
        assertTrue(String.valueOf(ex.getContext().get("reasons")).contains(
                CategoryWorkflowCompatibility.ENTITY_TYPE_NOT_COVERED));
    }

    @Test
    void categoryWithoutWorkflowRemainsLinkageRequiredInReadiness() {
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_NO_WF").versionNo(1).name("No WF")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("PL").intakeSegment("BORROWER")
                .governanceJson(new LinkedHashMap<>())
                .build();
        assertEquals(CategoryWorkflowBindService.LINKAGE_REQUIRED, CategoryWorkflowBindService.linkageStatus(e));
        when(categoryRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));

        var ready = categoryService.activationReadiness(e.getId());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "WORKFLOW_SELECTED".equals(c.code()) && !c.ok()));
        assertTrue(ready.checks().stream().anyMatch(c ->
                c.detail() != null && c.detail().contains("WORKFLOW LINKAGE REQUIRED")));
        assertEquals(CategoryWorkflowBindService.LINKAGE_REQUIRED,
                CategoryWorkflowBindService.linkageStatus(e));
    }

    @Test
    void noAutoBindingWithoutWorkflowId() {
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_NO_AUTO", "No Auto", null,
                "INDIVIDUAL", "PL", "BORROWER",
                null, null, null, null, null, null), actor);

        assertNull(res.workflowId());
        assertNull(res.workflowVersion());
        assertNull(res.workflowContentHash());
        assertNull(res.workflowName());
        assertEquals(CategoryWorkflowBindService.LINKAGE_REQUIRED, res.workflowLinkageStatus());
        verify(workflowConfigRepository, never()).findById(any());
    }

    @Test
    void multipleCategoriesCanReferenceSameWorkflow() {
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(compatibleWorkflow(wfId)));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse a = categoryService.createDraft(draftWithWorkflow("CC_SHARE_A", wfId, 1), actor);
        CategoryResponse b = categoryService.createDraft(draftWithWorkflow("CC_SHARE_B", wfId, 1), actor);

        assertEquals(wfId, a.workflowId());
        assertEquals(wfId, b.workflowId());
        assertNotEquals(a.code(), b.code());
    }

    @Test
    void oneCategoryCannotPointToMultipleWorkflows_singleBindOverwrites() {
        WorkflowConfig first = compatibleWorkflow(wfId);
        WorkflowConfig second = compatibleWorkflow(wfId2);
        second.setName("Alt Journey");
        when(workflowConfigRepository.findById(wfId2)).thenReturn(Optional.of(second));

        CustomerCategoryEntity existing = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_ONE").versionNo(1).name("One WF")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .minAmount(new BigDecimal("20000")).maxAmount(new BigDecimal("500000"))
                .workflowId(wfId).workflowVersion(1)
                .workflowContentHash(WorkflowContentHash.of(first))
                .workflowName(first.getName())
                .governanceJson(new LinkedHashMap<>())
                .createdBy(actor.identity())
                .build();
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findAll()).thenReturn(List.of(existing));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.update(existing.getId(), new CategoryRequest(
                "CC_ONE", "One WF", null,
                "INDIVIDUAL", "BUSINESS_TERM_LOAN", "BORROWER",
                new BigDecimal("20000"), new BigDecimal("500000"),
                null, null, null, "rebind",
                null, null, null, null, null,
                wfId2, 1), actor);

        assertEquals(wfId2, res.workflowId());
        assertEquals("Alt Journey", res.workflowName());
        assertNotEquals(wfId, res.workflowId());
    }

    @Test
    void mutationHashMismatchSurfacesInReadiness() {
        WorkflowConfig cfg = compatibleWorkflow(wfId);
        String originalHash = WorkflowContentHash.of(cfg);
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_MUT").versionNo(1).name("Mut")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .workflowId(wfId).workflowVersion(1)
                .workflowContentHash(originalHash)
                .workflowName(cfg.getName())
                .governanceJson(new LinkedHashMap<>())
                .build();

        // Mutate steps → content hash changes while version stays 1
        List<Map<String, Object>> mutated = new ArrayList<>(cfg.getSteps());
        mutated.add(Map.of("stepKey", "EXTRA"));
        cfg.setSteps(mutated);
        assertNotEquals(originalHash, WorkflowContentHash.of(cfg));

        when(categoryRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(cfg));

        var ready = categoryService.activationReadiness(e.getId());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && !c.ok()));
        assertTrue(ready.checks().stream().anyMatch(c ->
                CategoryWorkflowBindService.WORKFLOW_VERSION_MUTATED.equals(c.code())
                        || (c.detail() != null && c.detail().contains(
                        CategoryWorkflowBindService.WORKFLOW_VERSION_MUTATED))));
    }

    @Test
    void applicationWorkflowIdUnchanged_unitCommentOnly() {
        // W2 is Category config bind only — no LoanApplication mocks; application.workflow_id unchanged.
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(compatibleWorkflow(wfId)));
        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(draftWithWorkflow("CC_APP_WF", wfId, 1), actor);
        assertEquals(wfId, res.workflowId());
        // No application repository interactions — Category bind does not touch loan_applications.workflow_id
        verifyNoInteractions(ruleSetRepository);
    }

    @Test
    void activationValidatesExactBoundVersion_notLatestSibling() {
        WorkflowConfig v1 = compatibleWorkflow(wfId);
        v1.setVersion(1);
        v1.setActive(false);
        v1.setPublicationStatus("SUPERSEDED");
        WorkflowConfig v3 = compatibleWorkflow(wfId2);
        v3.setName("Starter Journey");
        v3.setVersion(3);
        v3.setWorkflowFamilyId(wfId);
        v3.setPublicationStatus("ACTIVE");
        String hash = WorkflowContentHash.of(v1);
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_PIN_V1").versionNo(1).name("Pinned v1")
                .status(ConfigLifecycleStatus.APPROVED)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .workflowId(wfId).workflowVersion(1)
                .workflowContentHash(hash)
                .workflowName(v1.getName())
                .governanceJson(new LinkedHashMap<>())
                .build();
        when(categoryRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(workflowConfigRepository.findById(wfId)).thenReturn(Optional.of(v1));

        var ready = categoryService.activationReadiness(e.getId());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok()));
        assertTrue(ready.checks().stream().anyMatch(c ->
                c.detail() != null && c.detail().contains("exactVersionId=" + wfId)));
        assertFalse(ready.checks().stream().anyMatch(c ->
                c.detail() != null && c.detail().contains("current v3")));
        assertEquals(wfId, e.getWorkflowId());
        assertEquals(1, e.getWorkflowVersion());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "WORKFLOW_APPLICABILITY_COMPATIBLE".equals(c.code()) && c.ok()));
        assertTrue(ready.checks().stream().noneMatch(c ->
                c.detail() != null && c.detail().contains("WORKFLOW_NOT_ACTIVE")));
    }

    @Test
    void explicitRebindToLaterVersionValidatesThatVersion() {
        WorkflowConfig v3 = compatibleWorkflow(wfId2);
        v3.setVersion(3);
        v3.setWorkflowFamilyId(wfId);
        String hash = WorkflowContentHash.of(v3);
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("CC_PIN_V3").versionNo(2).name("Pinned v3")
                .status(ConfigLifecycleStatus.APPROVED)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .workflowId(wfId2).workflowVersion(3)
                .workflowContentHash(hash)
                .workflowName(v3.getName())
                .governanceJson(new LinkedHashMap<>())
                .build();
        when(categoryRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(categoryRepository.findAll()).thenReturn(List.of(e));
        when(workflowConfigRepository.findById(wfId2)).thenReturn(Optional.of(v3));

        var ready = categoryService.activationReadiness(e.getId());
        assertTrue(ready.checks().stream().anyMatch(c ->
                "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok()
                        && c.detail() != null && c.detail().contains("exactVersionId=" + wfId2)));
    }
}
