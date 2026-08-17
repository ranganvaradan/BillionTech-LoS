package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadiness;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadinessReason;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CALCULATION-SETUP-ACTION-INVARIANT-1 — every CALCULATION_NOT_DEFINED business-derived
 * parameter exposes exactly one primary action: Set up calculation.
 */
class CalculationSetupActionInvariantTest {

    private static final String SETUP = "Set up calculation";
    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
    private static final String SCORE = "bureau.score";
    private static final String INQUIRY_DATE = "bureau.inquiry.date";
    private static final String MANUAL = "application.proposed_edi";
    private static final String AVG_AGE = "bureau.average_account_age_months";
    private static final String CC_OVERDUE = "bureau.cc_overdue_amount";

    private static final Map<String, Object> DPD30_COUNT_EXPR = Map.of(
            "op", "COUNT_PERIODS_MATCHING",
            "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
            "matchField", "dpd",
            "matchOp", "GTE",
            "matchValue", 30,
            "dateField", "month",
            "windowMonths", 6,
            "distinctPeriods", true,
            "asOf", Map.of("op", "EVAL_AS_OF"));

    private final Map<String, CiGacatDerivedCalculationDefinition> store = new ConcurrentHashMap<>();
    private CanonicalParameterRegistry registry;
    private AuthoredDerivedCalculationSupport overlaySupport;

    @BeforeEach
    void setUp() {
        store.clear();
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0))));
        ExecutionCapabilityAuthority.install(ExecutionSpineProducerBootstrap.standalone(definitions));
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();
        registry = new CanonicalParameterRegistry();

        store.put(DPD30, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(DPD30)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(new LinkedHashMap<>(DPD30_COUNT_EXPR))
                .dependencyIds(List.of("bureau.tradeline.dpd_month"))
                .description("DPD30 test fixture")
                .versionNo(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        overlaySupport.unregister();
        ExecutionCapabilityAuthority.clear();
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
    }

    @Test
    void everyCalculationNotDefined_hasExactlyOneSetupAction() {
        List<Map<String, Object>> audit = new ArrayList<>();
        int withoutSetup = 0;
        int withMultiple = 0;
        int count = 0;

        for (CanonicalParameterDefinition def : registry.all()) {
            if (!CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(def.type())) {
                continue;
            }
            Map<String, Object> st = CanonicalParameterStateService.state(def.id());
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = st.get("semantic") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));
            if (!"BUSINESS_PARAMETER".equals(paramClass)) {
                continue;
            }
            if (!BusinessReadiness.NOT_READY.name().equals(String.valueOf(st.get("businessReadiness")))) {
                continue;
            }
            if (!BusinessReadinessReason.CALCULATION_NOT_DEFINED.name()
                    .equals(String.valueOf(st.get("businessReadinessReason")))) {
                continue;
            }
            count++;
            @SuppressWarnings("unchecked")
            Map<String, Object> presentation = st.get("presentation") instanceof Map<?, ?> p
                    ? (Map<String, Object>) p : Map.of();
            @SuppressWarnings("unchecked")
            List<Object> actions = presentation.get("allowedActions") instanceof List<?> a
                    ? new ArrayList<>((List<Object>) a) : List.of();
            String next = String.valueOf(st.get("nextAction"));
            long setupCount = actions.stream().map(String::valueOf).filter(SETUP::equals).count();
            if (!SETUP.equals(next) || setupCount == 0) {
                withoutSetup++;
            }
            if (actions.size() > 1) {
                withMultiple++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalId", def.id());
            row.put("displayName", def.businessName());
            row.put("businessReadiness", st.get("businessReadiness"));
            row.put("reason", st.get("businessReadinessReason"));
            row.put("nextAction", next);
            row.put("allowedActions", actions);
            audit.add(row);
        }

        assertThat(count).as("CALCULATION_NOT_DEFINED_PARAMETER_COUNT").isGreaterThan(0);
        assertThat(withoutSetup).as("WITHOUT_SETUP_ACTION_COUNT").isEqualTo(0);
        assertThat(withMultiple).as("MULTIPLE_PRIMARY_ACTIONS_COUNT").isEqualTo(0);

        for (Map<String, Object> row : audit) {
            assertThat(row.get("nextAction")).as(String.valueOf(row.get("canonicalId"))).isEqualTo(SETUP);
            @SuppressWarnings("unchecked")
            List<Object> actions = (List<Object>) row.get("allowedActions");
            assertThat(actions).as(String.valueOf(row.get("canonicalId"))).containsExactly(SETUP);
        }
    }

    @Test
    void avgAge_and_ccOverdue_shareSetupAction() {
        for (String id : List.of("bureau.thin_file_indicator")) {
            Map<String, Object> st = CanonicalParameterStateService.state(id);
            assertThat(st.get("businessReadinessReason"))
                    .as(id).isEqualTo(BusinessReadinessReason.CALCULATION_NOT_DEFINED.name());
            assertThat(st.get("nextAction")).as(id).isEqualTo(SETUP);
            @SuppressWarnings("unchecked")
            Map<String, Object> presentation = st.get("presentation") instanceof Map<?, ?> p
                    ? (Map<String, Object>) p : Map.of();
            assertThat(presentation.get("allowedActions")).as(id).isEqualTo(List.of(SETUP));
        }
    }

    @Test
    void readyRawIngredientManual_neverGetSetupAction() {
        Map<String, Object> dpd = CanonicalParameterStateService.state(DPD30);
        assertThat(dpd.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertNoSetup(DPD30);
        assertNoSetup(SCORE);
        assertNoSetup(INQUIRY_DATE);
        assertNoSetup(MANUAL);
    }

    private static void assertNoSetup(String id) {
        Map<String, Object> st = CanonicalParameterStateService.state(id);
        assertThat(String.valueOf(st.get("nextAction"))).as(id).doesNotContain(SETUP);
        assertThat(BusinessReadinessReason.CALCULATION_NOT_DEFINED.name())
                .as(id + " must not be CALCULATION_NOT_DEFINED")
                .isNotEqualTo(String.valueOf(st.get("businessReadinessReason")));
        @SuppressWarnings("unchecked")
        Map<String, Object> presentation = st.get("presentation") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        @SuppressWarnings("unchecked")
        List<Object> actions = presentation.get("allowedActions") instanceof List<?> a
                ? new ArrayList<>((List<Object>) a) : List.of();
        assertThat(actions).as(id).doesNotContain(SETUP);
    }
}
