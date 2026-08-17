package com.los.core.customercategory;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CUSTOMER-CATEGORY-POLICY-SCOPE-COMPATIBILITY-1 goldens (config only).
 */
class CustomerCategoryPolicyScopeCompatibilityTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static CustomerCategoryPolicyScopeCompatibility.CategoryScope starterCat() {
        return new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                "BORROWER", "INDIVIDUAL", "BUSINESS_TERM_LOAN",
                new BigDecimal("20000"), new BigDecimal("500000"),
                null, null);
    }

    private static CiPolicyApplicability basePolicy(String name) {
        return CiPolicyApplicability.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .policyDocumentId(UUID.randomUUID())
                .policyName(name)
                .policyVersionLabel("v1")
                .businessStatus("APPROVED")
                .products(List.of("BUSINESS_TERM_LOAN"))
                .borrowerTypes(List.of("INDIVIDUAL"))
                .minLoanAmount(BigDecimal.ZERO)
                .maxLoanAmount(new BigDecimal("1000000"))
                .build();
    }

    @Test
    void starterLoanGolden_policyA_compatible() {
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), basePolicy("Policy A"));
        assertTrue(r.compatible());
        assertEquals(CustomerCategoryPolicyScopeCompatibility.STATUS_COMPATIBLE, r.status());
        assertTrue(r.reasons().isEmpty());
    }

    @Test
    void bankStarterGolden_policyB_sameScopeDifferentContent_compatible() {
        CiPolicyApplicability b = basePolicy("Policy B");
        b.setReasonForChange("Different underwriting content — irrelevant to scope");
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), b);
        assertTrue(r.compatible());
    }

    @Test
    void identicalDimensionsDifferentPolicies_bothValid() {
        var a = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), basePolicy("A"));
        var b = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), basePolicy("B"));
        assertTrue(a.compatible());
        assertTrue(b.compatible());
    }

    @Test
    void exactMatch_compatible() {
        CiPolicyApplicability p = basePolicy("Exact");
        p.setMinLoanAmount(new BigDecimal("20000"));
        p.setMaxLoanAmount(new BigDecimal("500000"));
        assertTrue(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p).compatible());
    }

    @Test
    void broaderPolicyAmountRange_compatible() {
        assertTrue(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), basePolicy("Broad")).compatible());
    }

    @Test
    void openEndedBounds_compatible() {
        CiPolicyApplicability p = basePolicy("Open");
        p.setMinLoanAmount(null);
        p.setMaxLoanAmount(null);
        assertTrue(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p).compatible());
    }

    @Test
    void wrongCustomerRole_incompatible() {
        CiPolicyApplicability p = basePolicy("Role");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("customerRoles", List.of("ANCHOR"));
        p.setMetadata(meta);
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertFalse(r.compatible());
        assertEquals(CustomerCategoryPolicyScopeCompatibility.STATUS_INCOMPATIBLE, r.status());
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.CUSTOMER_ROLE_NOT_COVERED));
    }

    @Test
    void wrongEntityType_incompatible() {
        CiPolicyApplicability p = basePolicy("Entity");
        p.setBorrowerTypes(List.of("COMPANY"));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.ENTITY_TYPE_NOT_COVERED));
    }

    @Test
    void wrongProduct_incompatible() {
        CiPolicyApplicability p = basePolicy("Product");
        p.setProducts(List.of("PERSONAL_LOAN"));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.PRODUCT_NOT_COVERED));
    }

    @Test
    void productTokenNormalization_businessTermLoan_compatible() {
        CiPolicyApplicability p = basePolicy("Spaces");
        p.setProducts(List.of("BUSINESS TERM LOAN"));
        assertTrue(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p).compatible());
    }

    @Test
    void policyMinAboveCategoryMin_incompatible() {
        CiPolicyApplicability p = basePolicy("MinHigh");
        p.setMinLoanAmount(new BigDecimal("100000"));
        p.setMaxLoanAmount(new BigDecimal("1000000"));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.AMOUNT_RANGE_NOT_COVERED));
        assertFalse(r.compatible());
    }

    @Test
    void policyMaxBelowCategoryMax_incompatible() {
        CiPolicyApplicability p = basePolicy("MaxLow");
        p.setMinLoanAmount(BigDecimal.ZERO);
        p.setMaxLoanAmount(new BigDecimal("300000"));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.AMOUNT_RANGE_NOT_COVERED));
    }

    @Test
    void partialAmountOverlap_notAccepted() {
        // Policy 100k–1M vs Category 20k–500k: partial overlap must fail
        CiPolicyApplicability p = basePolicy("Partial");
        p.setMinLoanAmount(new BigDecimal("100000"));
        p.setMaxLoanAmount(new BigDecimal("1000000"));
        assertFalse(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p).compatible());
    }

    @Test
    void policyEffectivePeriodShorterThanCategory_incompatible() {
        var cat = new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                "BORROWER", "INDIVIDUAL", "BUSINESS_TERM_LOAN",
                new BigDecimal("20000"), new BigDecimal("500000"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-12-31T00:00:00Z"));
        CiPolicyApplicability p = basePolicy("ShortEff");
        p.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        p.setEffectiveUntil(LocalDate.of(2026, 6, 30));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(cat, p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.EFFECTIVE_PERIOD_NOT_COVERED));
    }

    @Test
    void categoryOpenEndedButPolicyEnds_incompatible() {
        var cat = new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                "BORROWER", "INDIVIDUAL", "BUSINESS_TERM_LOAN",
                new BigDecimal("20000"), new BigDecimal("500000"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null);
        CiPolicyApplicability p = basePolicy("Ends");
        p.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        p.setEffectiveUntil(LocalDate.of(2027, 1, 1));
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(cat, p);
        assertTrue(r.reasons().contains(CustomerCategoryPolicyScopeCompatibility.EFFECTIVE_PERIOD_NOT_COVERED));
    }

    @Test
    void additionalPolicyScopeCannotBeProven_needsContext() {
        CiPolicyApplicability p = basePolicy("Extra");
        p.setCustomerSegment("SALARIED");
        p.setSecuredUnsecured("UNSECURED");
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertFalse(r.compatible());
        assertEquals(CustomerCategoryPolicyScopeCompatibility.STATUS_NEEDS_CONTEXT, r.status());
        assertTrue(r.reasons().contains(
                CustomerCategoryPolicyScopeCompatibility.ADDITIONAL_SCOPE_CONTEXT_REQUIRED));
    }

    @Test
    void missingPolicyCustomerRoleColumn_treatedAsAny() {
        // No metadata Role → unconstrained
        assertTrue(CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), basePolicy("NoRole")).compatible());
    }

    @Test
    void borrowerIntakeRelationship_isNotAdditionalCommercialSegment() {
        CiPolicyApplicability p = basePolicy("Vikasam Bureau");
        p.setCustomerSegment("BORROWER");
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(starterCat(), p);
        assertTrue(r.compatible());
        assertEquals(CustomerCategoryPolicyScopeCompatibility.STATUS_COMPATIBLE, r.status());
        assertFalse(r.reasons().contains(
                CustomerCategoryPolicyScopeCompatibility.ADDITIONAL_SCOPE_CONTEXT_REQUIRED));
    }

    @Test
    void emptyCategoryContext_doesNotThrow_andNeedsContext() {
        var empty = new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                null, null, null, null, null, null, null);
        var r = CustomerCategoryPolicyScopeCompatibility.evaluate(empty, basePolicy("Unfiltered"));
        assertEquals(CustomerCategoryPolicyScopeCompatibility.STATUS_NEEDS_CONTEXT, r.status());
        assertFalse(r.compatible());
    }
}
