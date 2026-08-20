package com.los.core.customercategory.selection;

import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.customercategory.MatchWildcard;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Credit Vintage discovery matching — backward compatibility (legacy 'ANY'-scoped Categories
 * must keep matching every application regardless of credit vintage) plus fail-closed behavior
 * for Categories that are actually scoped to a specific vintage.
 */
@ExtendWith(MockitoExtension.class)
class CreditVintageEligibilityTest {

    @Mock CustomerCategoryRepository categoryRepository;
    @Mock LoanApplicationRepository applicationRepository;

    CustomerCategoryEligibilityService eligibilityService;

    @BeforeEach
    void setUp() {
        eligibilityService = new CustomerCategoryEligibilityService(categoryRepository, applicationRepository);
    }

    private CustomerCategoryEntity category(String code, String creditVintage) {
        return CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(code)
                .status(ConfigLifecycleStatus.ACTIVE)
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .creditVintage(creditVintage)
                .workflowId(UUID.randomUUID())
                .policyApplicabilityId(UUID.randomUUID())
                .policyDocumentId(UUID.randomUUID())
                .governanceJson(new LinkedHashMap<>())
                .build();
    }

    private CategorySelectionDtos.EligibilityContext ctx(String creditVintage) {
        return new CategorySelectionDtos.EligibilityContext(
                UUID.randomUUID(), "BORROWER", "INDIVIDUAL", "BUSINESS_TERM_LOAN", creditVintage,
                new BigDecimal("100000"), Instant.now(), null, null, null, false);
    }

    @Test
    void anyScopedCategory_matchesRegardlessOfApplicationCreditVintage() {
        CustomerCategoryEntity anyCat = category("CC_ANY", MatchWildcard.ANY);
        when(categoryRepository.findByStatus(ConfigLifecycleStatus.ACTIVE)).thenReturn(List.of(anyCat));

        List<CustomerCategoryEntity> matchedForNew = eligibilityService.findEligibleEntities(ctx("NEW"));
        List<CustomerCategoryEntity> matchedForUnset = eligibilityService.findEligibleEntities(ctx(null));

        assertEquals(1, matchedForNew.size());
        assertEquals(1, matchedForUnset.size());
    }

    @Test
    void vintageScopedCategory_onlyMatchesExactVintage() {
        CustomerCategoryEntity existingCustomerCat = category("CC_EXISTING", "EXISTING_CUSTOMER");
        when(categoryRepository.findByStatus(ConfigLifecycleStatus.ACTIVE)).thenReturn(List.of(existingCustomerCat));

        assertTrue(eligibilityService.findEligibleEntities(ctx("EXISTING_CUSTOMER")).size() == 1);
        assertTrue(eligibilityService.findEligibleEntities(ctx("NEW")).isEmpty());
    }

    @Test
    void vintageScopedCategory_failsClosedWhenApplicationCreditVintageUnset() {
        CustomerCategoryEntity groupCat = category("CC_GROUP", "EXISTING_CUSTOMER_OF_GROUP");
        when(categoryRepository.findByStatus(ConfigLifecycleStatus.ACTIVE)).thenReturn(List.of(groupCat));

        assertTrue(eligibilityService.findEligibleEntities(ctx(null)).isEmpty());
    }
}
