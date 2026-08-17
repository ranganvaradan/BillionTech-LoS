package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * READY for DERIVED/BUILT_IN requires calculation.definitionPresent — capability alone is not enough.
 */
class BusinessReadinessDefinitionPresentTest {

    @Test
    void builtInCapableWithoutDefinition_isNotReady() {
        CanonicalParameterDefinition def = GacatCatalogueSeed.all().stream()
                .filter(d -> "bureau.cc_overdue_amount".equals(d.id()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> semantic = Map.of(
                "parameterClass", "BUSINESS_PARAMETER",
                "calculationMode", "BUILT_IN");
        Map<String, Object> source = Map.of("platformIntegrated", true, "notApplicable", false);
        Map<String, Object> execution = Map.of("capability", true, "valueAvailable", false);
        Map<String, Object> calculation = Map.of("required", false, "definitionPresent", false);

        Map<String, Object> out = BusinessReadinessProjector.project(
                def, semantic, source, execution, calculation);
        assertThat(out.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
        assertThat(out.get("businessReadinessReason"))
                .isEqualTo(BusinessReadinessReason.CALCULATION_NOT_DEFINED.name());
    }

    @Test
    void builtInCapableWithDefinition_andNoStructuralDeps_isReady() {
        CanonicalParameterDefinition def = GacatCatalogueSeed.all().stream()
                .filter(d -> "bureau.pan_distinct_count".equals(d.id()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> semantic = Map.of(
                "parameterClass", "BUSINESS_PARAMETER",
                "calculationMode", "BUILT_IN");
        Map<String, Object> source = Map.of("platformIntegrated", true, "notApplicable", false);
        Map<String, Object> execution = Map.of("capability", true, "valueAvailable", false);
        Map<String, Object> calculation = Map.of("required", false, "definitionPresent", true);

        Map<String, Object> out = BusinessReadinessProjector.project(
                def, semantic, source, execution, calculation);
        assertThat(out.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
    }
}
