package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauProductMapping;
import com.los.core.creditintelligence.bureau.repository.CiBureauProductMappingRepository;
import com.los.core.creditintelligence.bureau.service.BureauProductTaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BureauProductTaxonomyServiceTest {

    @Mock
    private CiBureauProductMappingRepository mappingRepository;

    private BureauProductTaxonomyService service;

    @BeforeEach
    void setUp() {
        service = new BureauProductTaxonomyService(mappingRepository);
        service.seedCache("EQUIFAX", BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1, List.of(
                CiBureauProductMapping.builder()
                        .providerCode("EQUIFAX").providerProductCode("05")
                        .providerProductDesc("Personal Loan")
                        .canonicalCategory("PERSONAL_LOAN").secured(false).revolving(false)
                        .mappingVersion(BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1).build(),
                CiBureauProductMapping.builder()
                        .providerCode("EQUIFAX").providerProductCode("02")
                        .providerProductDesc("Housing Loan")
                        .canonicalCategory("HOME_LOAN").secured(true).revolving(false)
                        .mappingVersion(BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1).build(),
                CiBureauProductMapping.builder()
                        .providerCode("EQUIFAX").providerProductCode(null)
                        .providerProductDesc("CREDIT CARD")
                        .canonicalCategory("CREDIT_CARD").secured(false).revolving(true)
                        .mappingVersion(BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1).build()
        ));
    }

    @Test
    void resolvesKnownUnsecuredByCode() {
        var r = service.resolve("EQUIFAX", "05", null);
        assertEquals(BureauProductCategory.PERSONAL_LOAN, r.category());
        assertFalse(r.secured());
        assertTrue(r.known());
    }

    @Test
    void resolvesKnownSecuredByCode() {
        var r = service.resolve("EQUIFAX", "02", "Housing Loan");
        assertEquals(BureauProductCategory.HOME_LOAN, r.category());
        assertTrue(r.secured());
    }

    @Test
    void resolvesUnknownWithNullSecured() {
        var r = service.resolve("EQUIFAX", "99", "Mystery Product XYZ");
        assertEquals(BureauProductCategory.UNKNOWN, r.category());
        assertNull(r.secured());
        assertFalse(r.known());
    }

    @Test
    void resolvesByDescriptionFallback() {
        var r = service.resolve("EQUIFAX", null, "CREDIT CARD");
        assertEquals(BureauProductCategory.CREDIT_CARD, r.category());
        assertTrue(r.revolving());
        assertFalse(r.secured());
    }

    @Test
    void resolvesHyphenAndEnDashAccountTypeVariants() {
        service.seedCache("EQUIFAX", BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1, List.of(
                CiBureauProductMapping.builder()
                        .providerCode("EQUIFAX").providerProductCode(null)
                        .providerProductDesc("Business Loan - General")
                        .canonicalCategory("PERSONAL_LOAN").secured(false).revolving(false)
                        .mappingVersion(BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1).build()
        ));
        var hyphen = service.resolve("EQUIFAX", null, "Business Loan - General");
        var enDash = service.resolve("EQUIFAX", null, "Business Loan – General");
        assertEquals(hyphen.category(), enDash.category());
        assertEquals(BureauProductCategory.PERSONAL_LOAN, enDash.category());
    }
}
