package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.repository.ExternalProductMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalProductMappingPinningServiceTest {

    @Mock
    private ExternalProductMappingRepository externalProductMappingRepository;

    @InjectMocks
    private ExternalProductMappingPinningService service;

    @Test
    void alreadyHasAppLmsProductCode_skipsPinning() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-LEGACY-1")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .lmsProductCode("LEGACY_CODE")
                .build();

        service.pinEncoreMappingIfNeeded(app);

        assertEquals("LEGACY_CODE", app.getLmsProductCode());
        assertNull(app.getExternalProductMappingId());
        verify(externalProductMappingRepository, never())
                .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    void invoiceDiscountingProduct_skipsPinning() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-ID-1")
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .build();

        service.pinEncoreMappingIfNeeded(app);

        verify(externalProductMappingRepository, never()).findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    void missingMapping_throwsLmsProductMappingMissing() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-1")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .categorySelectedAt(Instant.now())
                .build();

        LocalDate asOf = LocalDate.now();
        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.eq(StandardLoanProduct.TERM_LOAN),
                        org.mockito.ArgumentMatchers.eq("ENCORE"),
                        org.mockito.ArgumentMatchers.eq("ACTIVE"),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());
        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.eq(StandardLoanProduct.TERM_LOAN),
                        org.mockito.ArgumentMatchers.eq("ENCORE"),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());

        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.eq(StandardLoanProduct.TERM_LOAN),
                        org.mockito.ArgumentMatchers.eq("ENCORE")))
                .thenReturn(List.of());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING, ex.getReason());
    }

    @Test
    void notEffectiveMapping_throwsLmsProductMappingNotEffective() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-2")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .categorySelectedAt(Instant.now())
                .build();

        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("ACTIVE"),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());

        ExternalProductMapping anyStatusCovering = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("CODE_X")
                .version(1)
                .status("INACTIVE")
                .effectiveFrom(LocalDate.now().minusDays(10))
                .effectiveTo(LocalDate.now().plusDays(10))
                .build();

        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(anyStatusCovering));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_NOT_EFFECTIVE, ex.getReason());
    }

    @Test
    void ambiguousMapping_throwsLmsProductMappingAmbiguous() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-3")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .categorySelectedAt(Instant.now())
                .build();

        ExternalProductMapping m1 = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("CODE_1")
                .version(1)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.now().minusDays(10))
                .effectiveTo(LocalDate.now().plusDays(10))
                .build();

        ExternalProductMapping m2 = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("CODE_1")
                .version(2)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.now().minusDays(10))
                .effectiveTo(LocalDate.now().plusDays(10))
                .build();

        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("ACTIVE"),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(m2, m1));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_AMBIGUOUS, ex.getReason());
    }

    @Test
    void singleEffectiveActiveMapping_pinsExternalIdVersionAndCode() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("APP-4")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .categorySelectedAt(Instant.now())
                .build();

        ExternalProductMapping mapping = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("PINNED_CODE")
                .version(7)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.now().minusDays(10))
                .effectiveTo(LocalDate.now().plusDays(10))
                .build();

        when(externalProductMappingRepository
                .findByLosProductCodeAndExternalSystemAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                        org.mockito.ArgumentMatchers.eq(StandardLoanProduct.TERM_LOAN),
                        org.mockito.ArgumentMatchers.eq("ENCORE"),
                        org.mockito.ArgumentMatchers.eq("ACTIVE"),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(mapping));

        service.pinEncoreMappingIfNeeded(app);

        assertEquals(mapping.getId(), app.getExternalProductMappingId());
        assertEquals(mapping.getVersion(), app.getExternalProductMappingVersion());
        assertEquals("PINNED_CODE", app.getLmsProductCode());
    }
}

