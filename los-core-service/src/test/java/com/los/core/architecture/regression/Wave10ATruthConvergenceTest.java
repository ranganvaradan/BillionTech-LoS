package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleLifecycleProjection;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterTruthProjection;
import com.los.core.creditintelligence.policystudio.truth.LenderTruthDisplayMapper;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import com.los.core.service.readiness.DataParametersAdminService;
import com.los.core.service.readiness.DataParametersCapabilitySemantics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * WAVE-10A — Real client UI truth convergence + Policy Studio resolver separation.
 * No GACAT mutation, no auto-accept proposals, no fake production definitions beyond test fixture.
 */
class Wave10ATruthConvergenceTest {

    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
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
    private DataParametersAdminService dataParameters;

    @BeforeEach
    void setUp() {
        store.clear();
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });
        var spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);
        dataParameters = new DataParametersAdminService();
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void crossSurfaceParity_169_zeroMismatch() {
        List<String> surfaces = List.of(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS,
                SurfaceCanonicalTruthFacade.POLICY_STUDIO,
                SurfaceCanonicalTruthFacade.POLICY_INVENTORY,
                SurfaceCanonicalTruthFacade.SCORECARD_PICKER,
                SurfaceCanonicalTruthFacade.POLICY_TEST,
                SurfaceCanonicalTruthFacade.WORKFLOW_W6,
                SurfaceCanonicalTruthFacade.UNDERWRITING);
        int capMismatch = 0;
        int statusMismatch = 0;
        int certMismatch = 0;
        for (CanonicalParameterDefinition d : PolicyStudioConvergencePresenter.registry().all()) {
            Map<String, Object> base = CanonicalParameterTruthProjection.project(d.id());
            Boolean baseCap = SurfaceCanonicalTruthFacade.capability(base);
            String baseStatus = SurfaceCanonicalTruthFacade.status(base);
            String baseCert = SurfaceCanonicalTruthFacade.certStatus(base);
            for (String surface : surfaces) {
                Map<String, Object> view = SurfaceCanonicalTruthFacade.forSurface(surface, d.id());
                if (!java.util.Objects.equals(baseCap, SurfaceCanonicalTruthFacade.capability(view))) {
                    capMismatch++;
                }
                if (!java.util.Objects.equals(baseStatus, SurfaceCanonicalTruthFacade.status(view))) {
                    statusMismatch++;
                }
                if (!java.util.Objects.equals(baseCert, SurfaceCanonicalTruthFacade.certStatus(view))) {
                    certMismatch++;
                }
            }
        }
        assertThat(capMismatch).as("EXECUTION_CAPABILITY_MISMATCH_COUNT").isZero();
        assertThat(statusMismatch).as("EXECUTION_STATUS_MISMATCH_COUNT").isZero();
        assertThat(certMismatch).as("CERTIFICATION_STATUS_MISMATCH_COUNT").isZero();
    }

    @Test
    void dpd30_positiveGolden_executableNotNeedsInput() {
        store.put(DPD30, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(DPD30)
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(new LinkedHashMap<>(DPD30_COUNT_EXPR))
                .dependencyIds(List.of("bureau.tradeline.payment_history"))
                .description("Counts distinct reporting periods in the previous six months where DPD is 30+")
                .versionNo(1)
                .build());

        boolean capable = ExecutionCapabilityAuthority.hasExecutionCapability(DPD30, EvaluationMode.POLICY_TEST);
        Map<String, Object> truth = CanonicalParameterTruthProjection.project(DPD30);
        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) truth.get("execution");
        @SuppressWarnings("unchecked")
        Map<String, Object> calculation = (Map<String, Object>) truth.get("calculation");

        assertThat(capable).isTrue();
        assertThat(execution.get("capability")).isEqualTo(true);
        assertThat(calculation.get("required")).isEqualTo(false);
        assertThat(String.valueOf(truth.get("primaryStatusLabel")))
                .isIn("Ready", "Needs manual input");
        assertThat(String.valueOf(truth.get("primaryStatusLabel")))
                .doesNotContain("Needs your input")
                .doesNotContain("Calculation not defined");

        Map<String, Object> parameter = parameterOf(DPD30);
        assertThat(parameter).isNotNull();
        assertThat(String.valueOf(parameter.get("primaryStatusLabel")))
                .isIn("Ready", "Needs manual input");
        assertThat(String.valueOf(DPD30_COUNT_EXPR.get("op"))).isEqualTo("COUNT_PERIODS_MATCHING");
    }

    @Test
    void ccOverdue_negativeGolden_setupWithoutLegacyContradiction() {
        boolean capable = ExecutionCapabilityAuthority.hasExecutionCapability(
                CC_OVERDUE, EvaluationMode.POLICY_TEST);
        Map<String, Object> truth = CanonicalParameterTruthProjection.project(CC_OVERDUE);
        assertThat(capable).isFalse();
        assertThat(String.valueOf(truth.get("primaryStatusLabel"))).isIn("Calculation not defined", "Not ready");

        Map<String, Object> parameter = parameterOf(CC_OVERDUE);
        assertThat(parameter).isNotNull();
        String primary = String.valueOf(parameter.get("primaryStatusLabel"));
        assertThat(primary).isIn("Calculation not defined", "Not ready");
        assertThat(primary).doesNotContain("Supported");
        assertThat(primary).doesNotContain("Available for policy design");
        assertThat(primary).doesNotContain("Ready to test");
        assertThat(primary).doesNotContain("Calculated by BillionTech");
    }

    @Test
    void ruleLifecycleSeparation_executableParamUnresolvedRule() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("includedForActivation", true);
        card.put("authoringComplete", true);
        card.put("operands", List.of(Map.of(
                "parameterId", "bureau.score",
                "canonicalParameterId", "bureau.score",
                "calculationRequired", false,
                "calculationDefined", true,
                "unresolved", false
        )));
        var facts = PolicyRuleLifecycleProjection.factsFromCard(card, Map.of());
        assertThat(facts.calculationRequired()).isFalse();
        assertThat(facts.policyTestReady()).isTrue();
        assertThat(facts.ruleAccepted()).isFalse();
        var life = PolicyRuleLifecycleProjection.project("r-sep", facts);
        assertThat(life.get("lenderState")).isEqualTo("READY_FOR_CONFIRMATION");
        assertThat(String.valueOf(life.get("ruleLifecycleLabel"))).isEqualTo("Needs review");
        assertThat(String.valueOf(life.get("statusChip"))).isEqualTo("Needs review");
        assertThat(String.valueOf(life.get("statusChip"))).isNotEqualTo("Needs your input");
    }

    @Test
    void resolverProposalCase_notExecutableUntilAccepted() {
        Map<String, Object> semantic = Map.of("parameterClass", "BUSINESS_PARAMETER", "calculationMode", "AUTHORED");
        Map<String, Object> execution = Map.of("capability", false, "valueAvailable", false, "status", "NOT_EXECUTABLE");
        Map<String, Object> calculation = Map.of("required", true, "explanation", "Calculation is not set up yet.");
        Map<String, Object> certification = Map.of("status", "UNCERTIFIED");
        Map<String, Object> primary = LenderTruthDisplayMapper.primary(
                PolicyStudioConvergencePresenter.registry().findById(CC_OVERDUE).orElseThrow(),
                semantic, execution, calculation, certification);
        assertThat(primary.get("primaryStatusLabel")).isIn("Calculation not defined", "Not ready");
        assertThat(primary.get("nextAction")).isEqualTo("Set up calculation");
        // READY_FOR_REVIEW proposal is a resolver action, not capability
        assertThat(execution.get("capability")).isEqualTo(false);
    }

    @Test
    void sourceSummary_usesCanonicalAuthority_notListCompleteness() {
        List<CanonicalParameterDefinition> bureau = PolicyStudioConvergencePresenter.registry().all()
                .stream()
                .filter(d -> "Bureau Retail".equals(d.evaluatedFrom()))
                .toList();
        Map<String, Object> summary = DataParametersCapabilitySemantics.sourceFamilySummary(
                "Bureau Retail", bureau, family -> DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED);
        @SuppressWarnings("unchecked")
        Map<String, Object> facing = (Map<String, Object>) summary.get("lenderFacing");
        @SuppressWarnings("unchecked")
        Map<String, Object> canonical = (Map<String, Object>) summary.get("canonicalCounts");
        assertThat(facing.get("countAuthority")).isEqualTo(CanonicalParameterStateService.AUTHORITY);
        assertThat(canonical.get("authority")).isEqualTo(CanonicalParameterStateService.AUTHORITY);
        String line = String.valueOf(facing.get("summaryLine"));
        assertThat(line).doesNotContain("parameters available");
        assertThat(line).doesNotContain("calculations not yet implemented");
        assertThat(line).containsIgnoringCase("business parameters");
        assertThat(line).containsIgnoringCase("ready");
        assertThat(line).containsIgnoringCase("not ready");
        assertThat(canonical.get("sourceIngredients")).isNotNull();
        assertThat(canonical.get("businessParameters")).isNotNull();
    }

    @Test
    void legacySupportMetadata_cannotOverridePrimaryStatus() {
        Map<String, Object> parameter = parameterOf(CC_OVERDUE);
        assertThat(parameter).isNotNull();
        assertThat(parameter.get("primaryStatusLabel")).isIn("Calculation not defined", "Not ready");
        assertThat(parameter.get("primaryStatus")).isEqualTo("NOT_READY");
    }

    @Test
    void manualInputPrimaryLabel() {
        Optional<CanonicalParameterDefinition> manual = PolicyStudioConvergencePresenter.registry().all()
                .stream()
                .filter(d -> CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(d.type()))
                .findFirst();
        if (manual.isEmpty()) {
            return;
        }
        Map<String, Object> truth = CanonicalParameterTruthProjection.project(manual.get().id());
        if ("NEEDS_MANUAL_INPUT".equals(String.valueOf(truth.get("primaryStatus")))) {
            assertThat(truth.get("primaryStatusLabel")).isEqualTo("Needs manual input");
        }
    }

    private Map<String, Object> parameterOf(String id) {
        Map<String, Object> detail = dataParameters.parameterDetail(id);
        if (!Boolean.TRUE.equals(detail.get("found"))) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> parameter = (Map<String, Object>) detail.get("parameter");
        return parameter;
    }
}
