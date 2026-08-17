package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUILT_IN_CODE rows count as definitionPresent for UI even though they are not spine formulas.
 */
class BuiltInCodeDefinitionPresentTest {

    private static final String ID = "bureau.cc_overdue_amount";
    private static final String HOW = "MAX of credit-card overdue amounts (not SUM).";

    private AuthoredDerivedCalculationSupport support;
    private CiGacatDerivedCalculationDefinition row;

    @BeforeEach
    void setUp() {
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "BUILT_IN_CODE");
        expr.put("executor", "BureauMetricService");
        expr.put("calculationType", "BUILT_IN_CODE");
        expr.put("metricCode", ID);
        row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(ID)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .calculationType(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE)
                .expressionJson(expr)
                .dependencyIds(List.of("bureau.tradeline.overdue_amount"))
                .description(HOW)
                .versionNo(1)
                .metadata(Map.of("executionAuthority", "BureauMetricService",
                        "producer", "BuiltInBureauMetricProducer"))
                .build();

        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.of(row));
        support = new AuthoredDerivedCalculationSupport(definitions);
        support.register();
    }

    @AfterEach
    void tearDown() {
        support.unregister();
    }

    @Test
    void builtInCode_countsAsDefinitionPresent_notSpineExecutable() {
        Optional<Map<String, Object>> meta = AuthoredDerivedCalculationSupport.latestDefinitionPresent(ID);
        assertThat(meta).isPresent();
        assertThat(meta.get().get("definitionId")).isEqualTo(row.getId().toString());
        assertThat(meta.get().get("versionNo")).isEqualTo(1);
        assertThat(meta.get().get("calculationType"))
                .isEqualTo(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE);
        assertThat(meta.get().get("executor")).isEqualTo("BureauMetricService");
        assertThat(meta.get().get("status")).isEqualTo(DerivedCalculationDefinitionService.STATUS_TESTED);

        assertThat(AuthoredDerivedCalculationSupport.isBuiltInCodeDefinition(row.getExpressionJson())).isTrue();
        assertThat(AuthoredDerivedProducer.isSpineExecutableDefinition(ID, row.getExpressionJson())).isFalse();
        assertThat(AuthoredDerivedCalculationSupport.latestExecutableHow(ID)).contains(HOW);
    }

    @Test
    void overlayTreatsBuiltInCodeAsDefinedForUi() {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("parameterId", ID);
        AuthoredDerivedCalculationSupport.overlayOperand(face);
        assertThat(face.get("calculationDefined")).isEqualTo(true);
        assertThat(face.get("calculationRequired")).isEqualTo(false);
        assertThat(face.get("howCalculated")).asString().contains("MAX");
    }
}
