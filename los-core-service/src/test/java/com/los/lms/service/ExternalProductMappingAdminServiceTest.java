package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.repository.ExternalProductMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalProductMappingAdminServiceTest {

    @Mock
    private ExternalProductMappingRepository externalProductMappingRepository;

    @InjectMocks
    private ExternalProductMappingAdminService service;

    @Test
    void createMapping_activeOverlap_isRejectedFailClosed() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusDays(10);

        ExternalProductMapping existing = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode("TERM_LOAN")
                .externalSystem("ENCORE")
                .externalProductCode("CODE_OLD")
                .version(1)
                .status("ACTIVE")
                .effectiveFrom(from.minusDays(2))
                .effectiveTo(to.plusDays(2))
                .build();

        when(externalProductMappingRepository.findActiveOverlapping(
                        eq("TERM_LOAN"),
                        eq("ENCORE"),
                        eq("ACTIVE"),
                        eq(from),
                        eq(to),
                        isNull()))
                .thenReturn(List.of(existing));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.createMapping(
                new ExternalProductMappingAdminService.CreateMappingRequest(
                        "TERM_LOAN",
                        "ENCORE",
                        "CODE_NEW",
                        2,
                        "ACTIVE",
                        from,
                        to,
                        Map.of())));

        assertEquals("LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP", ex.getReason());
    }

    @Test
    void createMapping_activeNonOverlapping_isAllowed() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().minusDays(1);

        when(externalProductMappingRepository.findActiveOverlapping(
                        eq("TERM_LOAN"),
                        eq("ENCORE"),
                        eq("ACTIVE"),
                        eq(from),
                        eq(to),
                        isNull()))
                .thenReturn(List.of());

        ExternalProductMapping saved = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode("TERM_LOAN")
                .externalSystem("ENCORE")
                .externalProductCode("CODE_NEW")
                .version(2)
                .status("ACTIVE")
                .effectiveFrom(from)
                .effectiveTo(to)
                .metadataJson(Map.of())
                .build();

        when(externalProductMappingRepository.save(any(ExternalProductMapping.class)))
                .thenReturn(saved);

        ExternalProductMapping created = service.createMapping(
                new ExternalProductMappingAdminService.CreateMappingRequest(
                        "TERM_LOAN",
                        "ENCORE",
                        "CODE_NEW",
                        2,
                        "ACTIVE",
                        from,
                        to,
                        Map.of()));

        assertEquals(saved.getId(), created.getId());
        assertEquals("CODE_NEW", created.getExternalProductCode());
        assertEquals(2, created.getVersion());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    void patchStatus_activateOverlapping_isRejected() {
        UUID mappingId = UUID.randomUUID();
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusDays(10);

        ExternalProductMapping existing = ExternalProductMapping.builder()
                .id(mappingId)
                .losProductCode("TERM_LOAN")
                .externalSystem("ENCORE")
                .externalProductCode("CODE_OLD")
                .version(1)
                .status("INACTIVE")
                .effectiveFrom(from)
                .effectiveTo(to)
                .build();

        when(externalProductMappingRepository.findById(eq(mappingId)))
                .thenReturn(java.util.Optional.of(existing));

        ExternalProductMapping overlap = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode("TERM_LOAN")
                .externalSystem("ENCORE")
                .externalProductCode("CODE_X")
                .version(2)
                .status("ACTIVE")
                .effectiveFrom(from.minusDays(1))
                .effectiveTo(to.plusDays(1))
                .build();

        when(externalProductMappingRepository.findActiveOverlapping(
                        eq("TERM_LOAN"),
                        eq("ENCORE"),
                        eq("ACTIVE"),
                        eq(from),
                        eq(to),
                        eq(mappingId)))
                .thenReturn(List.of(overlap));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.patchStatus(mappingId, "ACTIVE"));

        assertEquals("LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP", ex.getReason());
        verify(externalProductMappingRepository, never()).save(existing);
    }

    @Test
    void createMapping_concurrentRaceCaughtByDbExclusionConstraint_isTranslatedToBusinessRuleException() {
        // The app-level findActiveOverlapping SELECT passes (simulating a concurrent second
        // request winning the race), but the database-level exclusion constraint added in
        // V153__external_product_mapping_overlap_exclusion.sql rejects the INSERT.
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusDays(10);

        when(externalProductMappingRepository.findActiveOverlapping(
                        eq("TERM_LOAN"), eq("ENCORE"), eq("ACTIVE"), eq(from), eq(to), isNull()))
                .thenReturn(List.of());
        when(externalProductMappingRepository.save(any(ExternalProductMapping.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "insert failed",
                        new RuntimeException(
                                "ERROR: conflicting key value violates exclusion constraint "
                                        + "\"excl_external_product_mapping_active_no_overlap\"")));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.createMapping(
                new ExternalProductMappingAdminService.CreateMappingRequest(
                        "TERM_LOAN", "ENCORE", "CODE_NEW", 2, "ACTIVE", from, to, Map.of())));

        assertEquals("LMS_PRODUCT_MAPPING_AMBIGUOUS_ACTIVE_OVERLAP", ex.getReason());
    }

    @Test
    void createMapping_unrelatedIntegrityViolation_isNotSwallowed() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusDays(10);

        when(externalProductMappingRepository.findActiveOverlapping(
                        eq("TERM_LOAN"), eq("ENCORE"), eq("ACTIVE"), eq(from), eq(to), isNull()))
                .thenReturn(List.of());
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "insert failed", new RuntimeException("ERROR: null value in column \"external_product_code\""));
        when(externalProductMappingRepository.save(any(ExternalProductMapping.class)))
                .thenThrow(unrelated);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.createMapping(new ExternalProductMappingAdminService.CreateMappingRequest(
                        "TERM_LOAN", "ENCORE", "CODE_NEW", 2, "ACTIVE", from, to, Map.of())));
        assertSame(unrelated, thrown);
    }

    @Test
    void patchStatus_deactivate_isAllowedWithoutOverlapCheck() {
        UUID mappingId = UUID.randomUUID();

        ExternalProductMapping existing = ExternalProductMapping.builder()
                .id(mappingId)
                .losProductCode("TERM_LOAN")
                .externalSystem("ENCORE")
                .externalProductCode("CODE_OLD")
                .version(1)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.now().minusDays(10))
                .effectiveTo(LocalDate.now().plusDays(10))
                .build();

        when(externalProductMappingRepository.findById(eq(mappingId)))
                .thenReturn(java.util.Optional.of(existing));

        when(externalProductMappingRepository.save(any(ExternalProductMapping.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ExternalProductMapping updated = service.patchStatus(mappingId, "INACTIVE");
        assertEquals("INACTIVE", updated.getStatus());
    }
}

