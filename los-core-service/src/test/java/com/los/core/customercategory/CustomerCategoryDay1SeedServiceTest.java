package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.Day1ApprovedSeedCatalog.Day1CategorySpec;
import com.los.core.customercategory.Day1ApprovedSeedCatalog.Day1PolicySetSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerCategoryDay1SeedServiceTest {

    @Mock UnderwritingRuleSetRepository ruleSetRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock PolicySetRepository policySetRepository;
    @Mock CustomerCategoryRepository categoryRepository;
    @Mock AdminConfigAuditSupport auditSupport;

    CustomerCategoryDay1SeedService service;
    Actor actor = new Actor("day1-seed", "Day1 Seeder", "ADMINISTRATOR");

    Map<UUID, UnderwritingRuleSet> rules = new ConcurrentHashMap<>();
    Map<UUID, UnderwritingScorecard> cards = new ConcurrentHashMap<>();
    Map<UUID, WorkflowConfig> wfs = new ConcurrentHashMap<>();
    Map<String, PolicySetEntity> psStore = new ConcurrentHashMap<>();
    Map<String, CustomerCategoryEntity> ccStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        service = new CustomerCategoryDay1SeedService(
                ruleSetRepository, scorecardRepository, workflowConfigRepository,
                policySetRepository, categoryRepository, auditSupport);
        stubCatalogRefs();
    }

    @Test
    void exactFifteenAndTwelveDraftOnly() {
        CustomerCategoryDay1SeedService.Day1ApplyResult r = service.applyDrafts(actor);
        assertEquals(12, r.createdPolicySets());
        assertEquals(15, r.createdCategories());
        assertEquals(12, r.policySetCount());
        assertEquals(15, r.categoryCount());
        assertEquals(12, r.draftPolicySets());
        assertEquals(15, r.draftCategories());
        assertEquals(0, r.activePolicySets());
        assertEquals(0, r.activeCategories());
        assertEquals(0, r.unintendedOverlaps());
        assertFalse(r.c100Included());
        assertFalse(r.excludedProductsPresent());
        assertTrue(r.rows().stream().allMatch(row -> "DRAFT".equals(row.categoryStatus())));
        assertTrue(r.rows().stream().allMatch(row -> "DRAFT".equals(row.policySetStatus())));
    }

    @Test
    void invoicePolicySetSharing() {
        service.applyDrafts(actor);
        long propInv = ccStore.values().stream()
                .filter(c -> c.getCode().contains("PROPRIETOR_BUSINESS_WC_INVOICE"))
                .map(CustomerCategoryEntity::getPolicySetId).distinct().count();
        assertEquals(1, propInv);
        assertEquals(12, psStore.size());
        assertEquals(15, ccStore.size());
    }

    @Test
    void c100AndExcludedProductsAbsent() {
        CustomerCategoryDay1SeedService.Day1ApplyResult r = service.applyDrafts(actor);
        assertFalse(r.c100Included());
        assertTrue(psStore.values().stream().noneMatch(p ->
                Day1ApprovedSeedCatalog.LEGACY_PROP_TL_RS.equals(p.getPrimaryRuleSetId().toString())));
        assertTrue(ccStore.values().stream().noneMatch(c ->
                Day1ApprovedSeedCatalog.EXCLUDED_PRODUCTS.contains(c.getLoanProduct())));
        // PROP TL locked
        Day1PolicySetSpec propTl = Day1ApprovedSeedCatalog.POLICY_SETS.stream()
                .filter(p -> p.code().equals("PS_DAY1_PROPRIETOR_TL")).findFirst().orElseThrow();
        assertEquals(UUID.fromString("c3320000-0000-4000-a000-000000000013"), propTl.primaryRuleSetId());
        assertEquals(UUID.fromString("d3320000-0000-4000-a000-000000000013"), propTl.scorecardId());
        Day1PolicySetSpec coBtl = Day1ApprovedSeedCatalog.POLICY_SETS.stream()
                .filter(p -> p.code().equals("PS_DAY1_COMPANY_BTL")).findFirst().orElseThrow();
        assertEquals(UUID.fromString("d3320000-0000-4000-a000-000000000026"), coBtl.scorecardId());
    }

    @Test
    void idempotentSecondApplyCreatesNoDuplicates() {
        service.applyDrafts(actor);
        int ps = psStore.size();
        int cc = ccStore.size();
        CustomerCategoryDay1SeedService.Day1ApplyResult r2 = service.applyDrafts(actor);
        assertEquals(0, r2.createdPolicySets());
        assertEquals(0, r2.createdCategories());
        assertEquals(ps, psStore.size());
        assertEquals(cc, ccStore.size());
        assertTrue(r2.skippedExisting() >= 15);
    }

    @Test
    void configurationConflictFailsClosed() {
        service.applyDrafts(actor);
        PolicySetEntity bad = psStore.get("PS_DAY1_COMPANY_TL");
        bad.setPrimaryRuleSetId(UUID.randomUUID());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.applyDrafts(actor));
        assertEquals("CONFIGURATION_CONFLICT", ex.getReason());
    }

    @Test
    void draftHasZeroLiveAuthorityStructural() {
        // Day-1 seed only writes customer_category / policy_set — never mutates engines' tables
        service.applyDrafts(actor);
        verify(ruleSetRepository, never()).save(any());
        verify(scorecardRepository, never()).save(any());
        verify(workflowConfigRepository, never()).save(any());
        assertTrue(ccStore.values().stream().allMatch(c -> c.getStatus() == ConfigLifecycleStatus.DRAFT));
    }

    @Test
    void transactionRollbackOnMissingRuleSet() {
        // break one rule set lookup mid-catalog validation
        UUID missing = Day1ApprovedSeedCatalog.POLICY_SETS.get(0).primaryRuleSetId();
        when(ruleSetRepository.findById(missing)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> service.applyDrafts(actor));
        assertTrue(psStore.isEmpty());
        assertTrue(ccStore.isEmpty());
    }

    @Test
    void zeroUnintendedOverlaps() {
        CustomerCategoryDay1SeedService.Day1ApplyResult r = service.applyDrafts(actor);
        assertEquals(0, r.unintendedOverlaps());
    }

    private void stubCatalogRefs() {
        for (Day1PolicySetSpec ps : Day1ApprovedSeedCatalog.POLICY_SETS) {
            UnderwritingRuleSet rs = UnderwritingRuleSet.builder()
                    .id(ps.primaryRuleSetId())
                    .name("RS " + ps.code())
                    .borrowerType(borrowerForPs(ps.code()))
                    .loanProduct(productForPs(ps.code()))
                    .minAmount(new BigDecimal("50000.00"))
                    .maxAmount(ps.code().endsWith("_INV") ? new BigDecimal("10000000.00") : new BigDecimal("50000000.00"))
                    .active(true)
                    .priority(100)
                    .rulesJson(Map.of())
                    .build();
            rules.put(rs.getId(), rs);
            when(ruleSetRepository.findById(ps.primaryRuleSetId())).thenReturn(Optional.of(rs));

            UnderwritingScorecard sc = UnderwritingScorecard.builder()
                    .id(ps.scorecardId())
                    .name("SC " + ps.code())
                    .borrowerType(rs.getBorrowerType())
                    .loanProduct(rs.getLoanProduct())
                    .minAmount(rs.getMinAmount())
                    .maxAmount(rs.getMaxAmount())
                    .active(true)
                    .status("ACTIVE")
                    .priority(100)
                    .scorecardJson(Map.of("rows", List.of()))
                    .thresholdsJson(Map.of())
                    .hardRulesJson(Map.of("rules", List.of()))
                    .build();
            cards.put(sc.getId(), sc);
            when(scorecardRepository.findById(ps.scorecardId())).thenReturn(Optional.of(sc));
        }
        for (Day1CategorySpec cat : Day1ApprovedSeedCatalog.CATEGORIES) {
            WorkflowConfig wf = WorkflowConfig.builder()
                    .id(cat.workflowId())
                    .name("WF " + cat.code())
                    .borrowerType(cat.borrowerType())
                    .loanProduct(cat.loanProduct())
                    .intakeSegment(cat.intakeSegment())
                    .active(true)
                    .steps(List.of())
                    .build();
            wfs.put(wf.getId(), wf);
            when(workflowConfigRepository.findById(cat.workflowId())).thenReturn(Optional.of(wf));
        }

        when(policySetRepository.findByCodeAndVersionNo(any(), eq(1))).thenAnswer(inv ->
                Optional.ofNullable(psStore.get(inv.getArgument(0))));
        when(policySetRepository.save(any())).thenAnswer(inv -> {
            PolicySetEntity e = inv.getArgument(0);
            psStore.put(e.getCode(), e);
            return e;
        });
        when(policySetRepository.findAll()).thenAnswer(inv -> new ArrayList<>(psStore.values()));
        when(policySetRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return psStore.values().stream().filter(p -> p.getId().equals(id)).findFirst();
        });

        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenAnswer(inv ->
                Optional.ofNullable(ccStore.get(inv.getArgument(0))));
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            CustomerCategoryEntity e = inv.getArgument(0);
            ccStore.put(e.getCode(), e);
            return e;
        });
        when(categoryRepository.findAll()).thenAnswer(inv -> new ArrayList<>(ccStore.values()));
    }

    private static String borrowerForPs(String code) {
        if (code.contains("INDIVIDUAL")) return "INDIVIDUAL";
        if (code.contains("PROPRIETOR")) return "PROPRIETOR";
        if (code.contains("PARTNERSHIP")) return "PARTNERSHIP";
        return "COMPANY";
    }

    private static String productForPs(String code) {
        if (code.endsWith("_TL")) return "TERM_LOAN";
        if (code.endsWith("_BTL")) return "BUSINESS_TERM_LOAN";
        return "BUSINESS_WC_INVOICE_DISCOUNTING";
    }
}
