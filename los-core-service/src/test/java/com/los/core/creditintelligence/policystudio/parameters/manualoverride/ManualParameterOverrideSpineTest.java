package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualParameterOverrideSpineTest {

    @Mock ApplicationParameterManualOverrideRepository repository;

    private ManualParameterOverrideSpine spine;

    @BeforeEach
    void setUp() {
        spine = new ManualParameterOverrideSpine(repository);
    }

    @Test
    void bindsActiveOverrideAsNumericValueWithProvenance() {
        UUID appId = UUID.randomUUID();
        ApplicationParameterManualOverride row = ApplicationParameterManualOverride.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .canonicalParameterId("bureau.recent_inquiries_90d")
                .valueText("3")
                .reason("Bureau unavailable in staging")
                .enteredBy("rm.vignesh")
                .enteredAt(Instant.parse("2026-08-20T00:00:00Z"))
                .active(true)
                .build();
        when(repository.findByApplicationIdAndActiveTrue(appId)).thenReturn(List.of(row));

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 20));
        spine.bindExactApplication(b, appId);
        EvaluationContext ctx = b.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> values =
                (Map<String, Object>) ctx.entities().get(ManualParameterOverrideSpine.MANUAL_PARAMETER_OVERRIDES);
        assertThat(values.get("bureau.recent_inquiries_90d")).isEqualTo(new BigDecimal("3"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prov = (Map<String, Object>)
                ctx.entities().get(ManualParameterOverrideSpine.MANUAL_PARAMETER_OVERRIDE_PROVENANCE);
        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) prov.get("bureau.recent_inquiries_90d");
        assertThat(row0.get("enteredBy")).isEqualTo("rm.vignesh");
        assertThat(row0.get("sourceType")).isEqualTo(ManualParameterOverrideSpine.SOURCE_MANUAL_PARAMETER_OVERRIDE);
    }

    @Test
    void noActiveOverrides_bindsEmptyMaps() {
        UUID appId = UUID.randomUUID();
        when(repository.findByApplicationIdAndActiveTrue(appId)).thenReturn(List.of());

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 20));
        spine.bindExactApplication(b, appId);
        EvaluationContext ctx = b.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> values =
                (Map<String, Object>) ctx.entities().get(ManualParameterOverrideSpine.MANUAL_PARAMETER_OVERRIDES);
        assertThat(values).isEmpty();
    }
}
