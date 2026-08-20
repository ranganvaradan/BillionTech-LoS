package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binds active {@link ApplicationParameterManualOverride} rows into CPES {@link EvaluationContext}
 * by exact application id — mirrors {@code PersistedDerivedMetricSpine}'s pattern for real
 * persisted metrics, but for operator-entered scoped overrides. Does not recompute, does not
 * look up any other application.
 */
@Component
@RequiredArgsConstructor
public class ManualParameterOverrideSpine {

    public static final String MANUAL_PARAMETER_OVERRIDES = "manualParameterOverrides";
    public static final String MANUAL_PARAMETER_OVERRIDE_PROVENANCE = "manualParameterOverrideProvenance";
    public static final String SOURCE_MANUAL_PARAMETER_OVERRIDE = "MANUAL_PARAMETER_OVERRIDE";

    private final ApplicationParameterManualOverrideRepository overrideRepository;

    public void bindExactApplication(EvaluationContext.Builder builder, UUID applicationId) {
        if (builder == null || applicationId == null) {
            return;
        }
        List<ApplicationParameterManualOverride> rows =
                overrideRepository.findByApplicationIdAndActiveTrue(applicationId);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> provenance = new LinkedHashMap<>();
        for (ApplicationParameterManualOverride row : rows) {
            String id = row.getCanonicalParameterId();
            if (id == null || id.isBlank()) {
                continue;
            }
            values.put(id, parseValue(row.getValueText()));
            Map<String, Object> prov = new LinkedHashMap<>();
            prov.put("sourceType", SOURCE_MANUAL_PARAMETER_OVERRIDE);
            prov.put("overrideId", row.getId() == null ? null : row.getId().toString());
            prov.put("enteredBy", row.getEnteredBy());
            prov.put("enteredAt", row.getEnteredAt() == null ? null : row.getEnteredAt().toString());
            prov.put("reason", row.getReason());
            provenance.put(id, prov);
        }
        builder.entity(MANUAL_PARAMETER_OVERRIDES, values);
        builder.entity(MANUAL_PARAMETER_OVERRIDE_PROVENANCE, provenance);
    }

    private static Object parseValue(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return t;
        }
    }
}
