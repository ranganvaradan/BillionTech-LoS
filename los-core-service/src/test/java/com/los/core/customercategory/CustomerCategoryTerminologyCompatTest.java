package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.domain.CreditTerminologyCompatibility;
import com.los.core.domain.CustomerRole;
import com.los.core.domain.EntityType;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
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
 * LOS-CREDIT-ARCHITECTURE-RECONCILIATION-STEP1 — terminology + compatibility only.
 * No live routing / schema renames / activation.
 */
@ExtendWith(MockitoExtension.class)
class CustomerCategoryTerminologyCompatTest {

    @Mock UnderwritingRuleSetRepository ruleSetRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock PolicySetRepository policySetRepository;
    @Mock CustomerCategoryRepository categoryRepository;
    @Mock AdminConfigAuditSupport auditSupport;

    CustomerCategoryValidator validator;
    CustomerCategoryService categoryService;
    Actor actor;

    @BeforeEach
    void setUp() {
        validator = new CustomerCategoryValidator(ruleSetRepository, scorecardRepository);
        categoryService = new CustomerCategoryService(
                categoryRepository, policySetRepository, validator, auditSupport);
        actor = new Actor("jwt-user", "CM Maker", "CREDIT_MANAGER");
    }

    @Test
    void borrowerIntakeMapsToCustomerRoleBorrower() {
        assertEquals(CustomerRole.BORROWER, CustomerRole.fromIntakeSegment(IntakeSegment.BORROWER));
        assertEquals(CustomerRole.BORROWER, CustomerRole.fromIntakeSegmentValue("BORROWER"));
        assertEquals("BORROWER",
                CreditTerminologyCompatibility.resolveCustomerRoleForStorage(null, "BORROWER"));
    }

    @Test
    void anchorMapsToCustomerRoleAnchor() {
        assertEquals(CustomerRole.ANCHOR, CustomerRole.fromIntakeSegment(IntakeSegment.ANCHOR));
        assertEquals(CustomerRole.ANCHOR, CustomerRole.fromIntakeSegmentValue("anchor"));
        assertEquals("ANCHOR",
                CreditTerminologyCompatibility.resolveCustomerRoleForStorage("ANCHOR", null));
    }

    @Test
    void existingIntakeSegmentRequestsStillWork() {
        UUID psId = stubLivePolicySet();
        when(categoryRepository.findByCodeAndVersionNo("CC_LEGACY_ROLE", 1)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_LEGACY_ROLE", "Legacy Role", null,
                "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER",
                null, null, psId, null, null, null), actor);

        assertEquals("BORROWER", res.intakeSegment());
        assertEquals("BORROWER", res.customerRole());
        assertEquals("INDIVIDUAL", res.borrowerType());
        assertEquals("INDIVIDUAL", res.entityType());
    }

    @Test
    void newCustomerRoleRequestsWork() {
        UUID psId = stubLivePolicySet();
        when(categoryRepository.findByCodeAndVersionNo("CC_CANON_ROLE", 1)).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerCategoryEntity> cap = ArgumentCaptor.forClass(CustomerCategoryEntity.class);
        when(categoryRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_CANON_ROLE", "Canon Role", null,
                null, "PERSONAL_LOAN", null,
                null, null, psId, null, null, null,
                "COMPANY", "ANCHOR"), actor);

        assertEquals("ANCHOR", res.customerRole());
        assertEquals("ANCHOR", res.intakeSegment());
        assertEquals("ANCHOR", cap.getValue().getIntakeSegment());
        assertEquals("COMPANY", cap.getValue().getBorrowerType());
    }

    @Test
    void conflictingCustomerRoleAndIntakeSegmentFailsClosed() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> CreditTerminologyCompatibility.resolveCustomerRoleForStorage("BORROWER", "ANCHOR"));
        assertEquals(CreditTerminologyCompatibility.CONFLICT_CUSTOMER_ROLE, ex.getReason());

        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        BusinessRuleException api = assertThrows(BusinessRuleException.class,
                () -> categoryService.createDraft(new CategoryRequest(
                        "CC_CONFLICT_ROLE", "Conflict", null,
                        "INDIVIDUAL", "PL", "ANCHOR",
                        null, null, UUID.randomUUID(), null, null, null,
                        null, "BORROWER"), actor));
        assertEquals(CreditTerminologyCompatibility.CONFLICT_CUSTOMER_ROLE, api.getReason());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void existingBorrowerTypeRequestsStillWork() {
        UUID psId = stubLivePolicySet();
        when(categoryRepository.findByCodeAndVersionNo("CC_LEGACY_ET", 1)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_LEGACY_ET", "Legacy ET", null,
                "PROPRIETOR", "TERM_LOAN", "BORROWER",
                null, null, psId, null, null, null), actor);

        assertEquals("PROPRIETOR", res.borrowerType());
        assertEquals("PROPRIETOR", res.entityType());
    }

    @Test
    void entityTypeMappingWorks() {
        assertEquals(EntityType.INDIVIDUAL, EntityType.fromBorrowerTypeValue("INDIVIDUAL"));
        assertEquals(EntityType.PARTNERSHIP, EntityType.fromBorrowerTypeValue("partnership"));
        assertEquals("COMPANY",
                CreditTerminologyCompatibility.resolveEntityTypeForStorage("COMPANY", null));
        assertEquals("COMPANY",
                CreditTerminologyCompatibility.resolveEntityTypeForStorage(null, "COMPANY"));
        assertEquals(MatchWildcard.ANY,
                CreditTerminologyCompatibility.resolveEntityTypeForStorage("ANY", "any"));
    }

    @Test
    void conflictingEntityTypeAndBorrowerTypeFailsClosed() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> CreditTerminologyCompatibility.resolveEntityTypeForStorage("INDIVIDUAL", "COMPANY"));
        assertEquals(CreditTerminologyCompatibility.CONFLICT_ENTITY_TYPE, ex.getReason());

        when(categoryRepository.findByCodeAndVersionNo(any(), eq(1))).thenReturn(Optional.empty());
        BusinessRuleException api = assertThrows(BusinessRuleException.class,
                () -> categoryService.createDraft(new CategoryRequest(
                        "CC_CONFLICT_ET", "Conflict", null,
                        "COMPANY", "PL", "BORROWER",
                        null, null, UUID.randomUUID(), null, null, null,
                        "INDIVIDUAL", null), actor));
        assertEquals(CreditTerminologyCompatibility.CONFLICT_ENTITY_TYPE, api.getReason());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void agreeingAliasesAcceptedAndPersistedUnchangedSemantics() {
        UUID psId = stubLivePolicySet();
        when(categoryRepository.findByCodeAndVersionNo("CC_AGREE", 1)).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerCategoryEntity> cap = ArgumentCaptor.forClass(CustomerCategoryEntity.class);
        when(categoryRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse res = categoryService.createDraft(new CategoryRequest(
                "CC_AGREE", "Agree", null,
                "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER",
                new BigDecimal("1000"), new BigDecimal("5000"), psId, null, null, null,
                "individual", "borrower"), actor);

        assertEquals("INDIVIDUAL", cap.getValue().getBorrowerType());
        assertEquals("BORROWER", cap.getValue().getIntakeSegment());
        assertEquals(res.borrowerType(), res.entityType());
        assertEquals(res.intakeSegment(), res.customerRole());
    }

    @Test
    void matchingResultUnchangedForIdenticalCriteria() {
        var a = new CustomerCategoryOverlapDetector.CategoryCriteria(
                "A", "A", "INDIVIDUAL", "PL", "BORROWER",
                new BigDecimal("100"), new BigDecimal("200"));
        var b = new CustomerCategoryOverlapDetector.CategoryCriteria(
                "B", "B", "INDIVIDUAL", "PL", "BORROWER",
                new BigDecimal("150"), new BigDecimal("250"));
        assertEquals(1, CustomerCategoryOverlapDetector.findOverlaps(List.of(a, b)).size());
        assertTrue(CustomerCategoryOverlapDetector.amountInRange(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("200")));
    }

    private UUID stubLivePolicySet() {
        UUID psId = UUID.randomUUID();
        when(policySetRepository.findById(psId)).thenReturn(Optional.of(PolicySetEntity.builder()
                .id(psId).code("PS_X").versionNo(1).name("PS")
                .status(ConfigLifecycleStatus.DRAFT)
                .primaryRuleSetId(UUID.randomUUID()).build()));
        return psId;
    }
}
