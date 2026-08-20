package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationParameterOverrideServiceTest {

    @Mock ApplicationParameterManualOverrideRepository overrideRepository;
    @Mock CiBureauReportRepository bureauReportRepository;

    private ApplicationParameterOverrideService service;
    private final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApplicationParameterOverrideService(overrideRepository, bureauReportRepository);
        lenient().when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void nonAllowListedParameter_isRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createOverride(appId, "bureau.score", "700", "test", "rm1"));
        assertThat(ex.getReason()).isEqualTo(ApplicationParameterOverrideService.PARAMETER_NOT_OVERRIDABLE);
    }

    @Test
    void realBureauReportAlreadyExists_isRejected() {
        when(bureauReportRepository.findByApplicationId(appId))
                .thenReturn(List.of(CiBureauReport.builder().id(UUID.randomUUID()).build()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createOverride(appId, "bureau.recent_inquiries_90d", "3", "test", "rm1"));
        assertThat(ex.getReason()).isEqualTo(ApplicationParameterOverrideService.REAL_SOURCE_ALREADY_AVAILABLE);
    }

    @Test
    void noBureauReport_overrideCreated() {
        when(bureauReportRepository.findByApplicationId(appId)).thenReturn(List.of());
        when(overrideRepository.findByApplicationIdAndCanonicalParameterIdAndActiveTrue(
                appId, "bureau.recent_inquiries_90d")).thenReturn(Optional.empty());

        ApplicationParameterManualOverride created =
                service.createOverride(appId, "bureau.recent_inquiries_90d", "3", "Bureau unavailable", "rm1");

        assertThat(created.getCanonicalParameterId()).isEqualTo("bureau.recent_inquiries_90d");
        assertThat(created.getValueText()).isEqualTo("3");
        assertThat(created.isActive()).isTrue();
        assertThat(created.getEnteredBy()).isEqualTo("rm1");
    }

    @Test
    void creatingOverride_supersedesExistingActiveOne() {
        when(bureauReportRepository.findByApplicationId(appId)).thenReturn(List.of());
        ApplicationParameterManualOverride existing = ApplicationParameterManualOverride.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .canonicalParameterId("bureau.recent_inquiries_90d")
                .valueText("5")
                .active(true)
                .build();
        when(overrideRepository.findByApplicationIdAndCanonicalParameterIdAndActiveTrue(
                appId, "bureau.recent_inquiries_90d")).thenReturn(Optional.of(existing));

        service.createOverride(appId, "bureau.recent_inquiries_90d", "3", "Corrected", "rm2");

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getSupersededAt()).isNotNull();
        verify(overrideRepository).save(existing);
    }
}
