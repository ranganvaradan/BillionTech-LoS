package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CUSTOMER-CATEGORY-GOVERNANCE-IMPLEMENTATION-1 + STEP-2 Policy bind coverage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerCategoryGovernanceTest {

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
    CategoryPolicyBindService policyBindService;
    EligibleComponentCatalogueService catalogue;

    Actor maker = new Actor("maker-1", "Maker One", "CREDIT_MANAGER");
    Actor checker = new Actor("checker-1", "Checker One", "POLICY_CHECKER");
    Actor activator = new Actor("act-1", "Activator", "CREDIT_MANAGER");

    ConcurrentHashMap<UUID, PolicySetEntity> psById = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, PolicySetEntity> psByCode = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, CustomerCategoryEntity> ccById = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, CustomerCategoryEntity> ccByCode = new ConcurrentHashMap<>();

    UUID rsId;
    UUID scId;
    UUID policyAppId;
    UUID policyDocId;
    UUID workflowId;
    UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        when(creditIntelligenceProperties.getDefaultTenantId()).thenReturn(tenant);
        lenient().when(workflowBindService.workflowActivationChecks(any())).thenReturn(List.of());
        lenient().when(workflowBindService.resolveBind(any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            Integer ver = inv.getArgument(1);
            return new CategoryWorkflowBindService.ResolvedWorkflowBind(
                    id, ver != null ? ver : 1, "wf-hash", "Gov Journey", true);
        });
        lenient().doNothing().when(workflowBindService).requireCompatible(
                any(), any(), any(), any());
        lenient().doAnswer(inv -> {
            CustomerCategoryEntity e = inv.getArgument(0);
            CategoryWorkflowBindService.ResolvedWorkflowBind b = inv.getArgument(1);
            e.setWorkflowId(b.workflowId());
            e.setWorkflowVersion(b.workflowVersion());
            e.setWorkflowContentHash(b.contentHash());
            e.setWorkflowName(b.workflowName());
            return null;
        }).when(workflowBindService).applyBind(any(), any());
        validator = new CustomerCategoryValidator(ruleSetRepository, scorecardRepository);
        policySetService = new PolicySetService(policySetRepository, categoryRepository, validator, auditSupport);
        policyBindService = new CategoryPolicyBindService(
                applicabilityRepository, policyCatalogueService, creditIntelligenceProperties);
        categoryService = new CustomerCategoryService(
                categoryRepository, policySetRepository, validator, auditSupport,
                policyBindService, workflowBindService, applicabilityRepository);
        catalogue = new EligibleComponentCatalogueService(ruleSetRepository, scorecardRepository);

        rsId = UUID.randomUUID();
        scId = UUID.randomUUID();
        policyAppId = UUID.randomUUID();
        policyDocId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        when(ruleSetRepository.findById(rsId)).thenReturn(Optional.of(rs(rsId, "INDIVIDUAL", "TERM_LOAN")));
        when(scorecardRepository.findById(scId)).thenReturn(Optional.of(sc(scId, "INDIVIDUAL", "TERM_LOAN")));
        when(ruleSetRepository.findAll()).thenReturn(List.of(rs(rsId, "INDIVIDUAL", "TERM_LOAN")));
        when(scorecardRepository.findAll()).thenReturn(List.of(sc(scId, "INDIVIDUAL", "TERM_LOAN")));
        when(applicabilityRepository.findById(policyAppId)).thenReturn(Optional.of(approvedPolicy()));

        when(policySetRepository.findByCodeAndVersionNo(any(), anyInt())).thenAnswer(inv -> {
            PolicySetEntity e = psByCode.get(inv.getArgument(0) + "@" + inv.getArgument(1));
            return Optional.ofNullable(e);
        });
        when(policySetRepository.findFirstByCodeOrderByVersionNoDesc(any())).thenAnswer(inv ->
                psById.values().stream().filter(p -> p.getCode().equals(inv.getArgument(0)))
                        .max(java.util.Comparator.comparingInt(PolicySetEntity::getVersionNo)));
        when(policySetRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(psById.get(inv.getArgument(0))));
        when(policySetRepository.existsById(any())).thenAnswer(inv -> psById.containsKey(inv.getArgument(0)));
        when(policySetRepository.save(any())).thenAnswer(inv -> {
            PolicySetEntity e = inv.getArgument(0);
            psById.put(e.getId(), e);
            psByCode.put(e.getCode() + "@" + e.getVersionNo(), e);
            return e;
        });
        when(policySetRepository.findAllByOrderByCodeAscVersionNoDesc()).thenAnswer(inv ->
                new ArrayList<>(psById.values()));

        when(categoryRepository.findByCodeAndVersionNo(any(), anyInt())).thenAnswer(inv ->
                Optional.ofNullable(ccByCode.get(inv.getArgument(0) + "@" + inv.getArgument(1))));
        when(categoryRepository.findFirstByCodeOrderByVersionNoDesc(any())).thenAnswer(inv ->
                ccById.values().stream().filter(c -> c.getCode().equals(inv.getArgument(0)))
                        .max(java.util.Comparator.comparingInt(CustomerCategoryEntity::getVersionNo)));
        when(categoryRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(ccById.get(inv.getArgument(0))));
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            CustomerCategoryEntity e = inv.getArgument(0);
            ccById.put(e.getId(), e);
            ccByCode.put(e.getCode() + "@" + e.getVersionNo(), e);
            return e;
        });
        when(categoryRepository.findAll()).thenAnswer(inv -> new ArrayList<>(ccById.values()));
        when(categoryRepository.findAllByOrderByCodeAscVersionNoDesc()).thenAnswer(inv ->
                new ArrayList<>(ccById.values()));
        doNothing().when(categoryRepository).delete(any());
    }

    private CiPolicyApplicability approvedPolicy() {
        return CiPolicyApplicability.builder()
                .id(policyAppId)
                .tenantId(tenant)
                .policyDocumentId(policyDocId)
                .policyName("Gov Test Policy")
                .policyVersionLabel("v1")
                .businessStatus("APPROVED")
                .dataReadinessStatus("PASSED")
                .products(List.of("TERM_LOAN"))
                .borrowerTypes(List.of("INDIVIDUAL"))
                .build();
    }

    @Test
    void categoryCreateEditSubmitSelfApproveBlockedApproveActivateUsesPolicyNotPolicySet() {
        Actor adminMaker = new Actor("admin-1", "Admin Maker", "ADMINISTRATOR");
        PolicySetResponse ps = policySetService.createDraft(psReq("PS_G1"), adminMaker);
        CategoryResponse cat = categoryService.createDraft(catReq("CC_G1", ps.id()), adminMaker);
        assertEquals("DRAFT", cat.status());
        assertEquals("LINKED", cat.policyLinkageStatus());

        cat = categoryService.update(cat.id(), catReq("CC_G1", ps.id()), adminMaker);
        assertEquals("DRAFT", cat.status());

        CategoryResponse submitted = categoryService.submit(cat.id(), new LifecycleActionRequest("please review", null), adminMaker);
        assertEquals("IN_REVIEW", submitted.status());
        UUID submittedId = submitted.id();

        BusinessRuleException self = assertThrows(BusinessRuleException.class,
                () -> categoryService.approve(submittedId, new LifecycleActionRequest("ok", null), adminMaker));
        assertEquals("SELF_APPROVAL_FORBIDDEN", self.getReason());

        CategoryResponse approved = categoryService.approve(submittedId, new LifecycleActionRequest("ok", null), checker);
        assertEquals("APPROVED", approved.status());
        UUID approvedId = approved.id();

        // Policy Set still DRAFT — Category activation uses Policy Version, not Policy Set
        CategoryResponse active = categoryService.activate(approvedId, activator);
        assertEquals("ACTIVE", active.status());
        assertEquals("LINKED", active.policyLinkageStatus());
    }

    @Test
    void activeCategoryEditBlockedAndCopyCreatesDraft() {
        PolicySetResponse ps = fullActivePolicySet("PS_G2");
        CategoryResponse cat = fullActiveCategory("CC_G2", ps.id());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> categoryService.update(cat.id(),
                        new CategoryRequest("CC_G2", "x", null, "COMPANY", "TERM_LOAN", "BORROWER",
                                new BigDecimal("1"), new BigDecimal("2"), ps.id(), null, null, null,
                                null, null, policyAppId, policyDocId, "v1"), maker));
        assertEquals("ACTIVE_CATEGORY_IMMUTABLE", ex.getReason());

        CategoryResponse copy = categoryService.copyVersion(cat.id(),
                new LifecycleActionRequest(null, "replacement"), maker);
        assertEquals("DRAFT", copy.status());
        assertEquals(2, copy.versionNo());
        assertEquals(cat.id(), copy.replacesCategoryId());
        assertEquals(policyAppId, copy.policyApplicabilityId());

        CategoryResponse retired = categoryService.retire(cat.id(),
                new LifecycleActionRequest(null, "superseded"), activator);
        assertEquals("RETIRED", retired.status());
        assertEquals("superseded", retired.retirementReason());

        BusinessRuleException ro = assertThrows(BusinessRuleException.class,
                () -> categoryService.update(retired.id(),
                        catReq("CC_G2", ps.id()), maker));
        assertEquals("CATEGORY_RETIRED", ro.getReason());
    }

    @Test
    void policySetLifecycleAndValidators() {
        PolicySetResponse ps = policySetService.createDraft(psReq("PS_G3"), maker);
        ps = policySetService.updateDraft(ps.id(),
                new PolicySetRequest("PS_G3", "Renamed", "d", rsId, List.of(), scId, null, null, "edit"), maker);
        assertEquals("Renamed", ps.name());

        BusinessRuleException multi = assertThrows(BusinessRuleException.class,
                () -> policySetService.createDraft(
                        new PolicySetRequest("PS_MULTI", "m", null, rsId, List.of(UUID.randomUUID()), scId,
                                null, null, null), maker));
        assertEquals("MULTI_RULE_SET_NOT_ENABLED", multi.getReason());

        ps = policySetService.submit(ps.id(), new LifecycleActionRequest("s", null), maker);
        assertEquals("IN_REVIEW", ps.status());
        ps = policySetService.approve(ps.id(), new LifecycleActionRequest("a", null), checker);
        assertEquals("APPROVED", ps.status());
        UUID approvedPsId = ps.id();

        // draft authority blocked on activate: deactivate scorecard
        UnderwritingScorecard draftSc = sc(scId, "INDIVIDUAL", "TERM_LOAN");
        draftSc.setActive(false);
        draftSc.setStatus("DRAFT");
        when(scorecardRepository.findById(scId)).thenReturn(Optional.of(draftSc));
        BusinessRuleException badSc = assertThrows(BusinessRuleException.class,
                () -> policySetService.activate(approvedPsId, activator));
        assertEquals("SCORECARD_NOT_LIVE_READY", badSc.getReason());

        when(scorecardRepository.findById(scId)).thenReturn(Optional.of(sc(scId, "INDIVIDUAL", "TERM_LOAN")));
        PolicySetResponse activePs = policySetService.activate(approvedPsId, activator);
        assertEquals("ACTIVE", activePs.status());
        UUID activePsId = activePs.id();

        BusinessRuleException activeEdit = assertThrows(BusinessRuleException.class,
                () -> policySetService.updateDraft(activePsId, psReq("PS_G3"), maker));
        assertEquals("POLICY_SET_NOT_DRAFT", activeEdit.getReason());

        PolicySetResponse copy = policySetService.copyVersion(activePsId,
                new LifecycleActionRequest(null, "v2"), maker);
        assertEquals("DRAFT", copy.status());
        assertEquals(2, copy.versionNo());

        PolicySetResponse retired = policySetService.retire(activePsId,
                new LifecycleActionRequest(null, "done"), activator);
        assertEquals("RETIRED", retired.status());
    }

    @Test
    void draftToActiveDirectlyForbidden() {
        PolicySetResponse ps = policySetService.createDraft(psReq("PS_G4"), maker);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> policySetService.activate(ps.id(), activator));
        assertEquals("POLICY_SET_NOT_APPROVED", ex.getReason());

        CategoryResponse cat = categoryService.createDraft(catReq("CC_G4", ps.id()), maker);
        BusinessRuleException ex2 = assertThrows(BusinessRuleException.class,
                () -> categoryService.activate(cat.id(), activator));
        assertEquals("CATEGORY_NOT_APPROVED", ex2.getReason());
    }

    @Test
    void eligiblePickersAndJwtRequired() {
        assertFalse(catalogue.eligibleRuleSets("INDIVIDUAL", "TERM_LOAN", new BigDecimal("100000")).isEmpty());
        assertFalse(catalogue.eligibleScorecards("INDIVIDUAL", "TERM_LOAN", new BigDecimal("100000")).isEmpty());

        Actor anon = new Actor("", "x", "CREDIT_MANAGER");
        assertThrows(ForbiddenException.class, () -> policySetService.createDraft(psReq("PS_X"), anon));
    }

    @Test
    void historyPresentAfterLifecycle() {
        PolicySetResponse ps = fullActivePolicySet("PS_HIST");
        assertFalse(ps.history().isEmpty());
        assertTrue(ps.history().stream().anyMatch(h -> "ACTIVATED".equals(h.get("event"))));
    }

    private PolicySetResponse fullActivePolicySet(String code) {
        PolicySetResponse ps = policySetService.createDraft(psReq(code), maker);
        ps = policySetService.submit(ps.id(), new LifecycleActionRequest("s", null), maker);
        ps = policySetService.approve(ps.id(), new LifecycleActionRequest("a", null), checker);
        return policySetService.activate(ps.id(), activator);
    }

    private CategoryResponse fullActiveCategory(String code, UUID psId) {
        CategoryResponse cat = categoryService.createDraft(catReq(code, psId), maker);
        cat = categoryService.submit(cat.id(), new LifecycleActionRequest("s", null), maker);
        cat = categoryService.approve(cat.id(), new LifecycleActionRequest("a", null), checker);
        return categoryService.activate(cat.id(), activator);
    }

    private PolicySetRequest psReq(String code) {
        return new PolicySetRequest(code, code + " name", null, rsId, List.of(), scId, null, null, null);
    }

    private CategoryRequest catReq(String code, UUID psId) {
        return new CategoryRequest(code, code + " name", null,
                "INDIVIDUAL", "TERM_LOAN", "BORROWER",
                new BigDecimal("50000"), new BigDecimal("50000000"),
                psId, null, null, null,
                null, null, policyAppId, policyDocId, "v1",
                workflowId, 1);
    }

    private static UnderwritingRuleSet rs(UUID id, String bt, String lp) {
        return UnderwritingRuleSet.builder()
                .id(id).name(bt + "-" + lp).borrowerType(bt).loanProduct(lp)
                .priority(100).active(true).minAmount(new BigDecimal("50000"))
                .maxAmount(new BigDecimal("50000000")).rulesJson(java.util.Map.of()).build();
    }

    private static UnderwritingScorecard sc(UUID id, String bt, String lp) {
        return UnderwritingScorecard.builder()
                .id(id).name("SC-" + bt).borrowerType(bt).loanProduct(lp)
                .priority(100).active(true).status("ACTIVE")
                .minAmount(new BigDecimal("50000")).maxAmount(new BigDecimal("50000000"))
                .scorecardJson(java.util.Map.of()).thresholdsJson(java.util.Map.of())
                .hardRulesJson(java.util.Map.of()).safetyJson(java.util.Map.of())
                .governanceJson(new LinkedHashMap<>()).build();
    }
}
