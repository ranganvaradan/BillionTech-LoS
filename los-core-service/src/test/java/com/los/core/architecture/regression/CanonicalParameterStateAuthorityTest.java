package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleState;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FINAL-CANONICAL-PARAMETER-STATE-REFACTOR-1 — single state authority + architecture guards.
 */
class CanonicalParameterStateAuthorityTest {

    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
    private static final String CC_OVERDUE = "bureau.cc_overdue_amount";
    private static final String CLEAN = "bureau.credit_after_overdue.clean_history_months";

    @BeforeEach
    void setUp() {
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());
        ExecutionCapabilityAuthority.install(ExecutionSpineProducerBootstrap.standalone(definitions));
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void soleAuthority_exists_andStampsContract() {
        Map<String, Object> state = CanonicalParameterStateService.state(DPD30);
        assertThat(state.get("stateAuthority")).isEqualTo(CanonicalParameterStateService.AUTHORITY);
        assertThat(state.get("stateContract")).isEqualTo(CanonicalParameterStateService.CONTRACT);
        assertThat(state).containsKeys("semantic", "execution", "calculation", "presentation", "data");
    }

    @Test
    void allSurfaces_shareSamePrimaryStatus_viaStateService() {
        Map<String, Object> base = CanonicalParameterStateService.state(DPD30);
        String primary = String.valueOf(base.get("primaryStatus"));
        for (String surface : CanonicalParameterStateService.CONSUMER_SURFACES) {
            Map<String, Object> view = SurfaceCanonicalTruthFacade.forSurface(surface, DPD30);
            assertThat(view.get("primaryStatus")).as(surface).isEqualTo(primary);
            assertThat(view.get("parameterStateAuthority"))
                    .as(surface).isEqualTo(CanonicalParameterStateService.AUTHORITY);
        }
    }

    @Test
    void inventoryStamp_andDpEnrich_andScorecard_samePrimary() {
        Map<String, Object> row = new LinkedHashMap<>();
        CanonicalParameterStateService.stamp(row, CC_OVERDUE);
        Map<String, Object> state = CanonicalParameterStateService.state(CC_OVERDUE);
        assertThat(row.get("primaryStatus")).isEqualTo(state.get("primaryStatus"));
        assertThat(String.valueOf(state.get("primaryStatus"))).isEqualTo("NOT_READY");
        assertThat(String.valueOf(state.get("businessReadiness"))).isEqualTo("NOT_READY");
        assertThat(String.valueOf(state.get("businessReadinessReason"))).isEqualTo("CALCULATION_NOT_DEFINED");
        // Surface facade + stamp must agree
        Map<String, Object> dpView = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS, CC_OVERDUE);
        Map<String, Object> scView = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.SCORECARD_PICKER, CC_OVERDUE);
        assertThat(dpView.get("primaryStatus")).isEqualTo(state.get("primaryStatus"));
        assertThat(scView.get("primaryStatus")).isEqualTo(state.get("primaryStatus"));
        assertThat(scView.get("setupIncomplete")).isEqualTo(true);
    }

    @Test
    void sourceIngredient_neverCalculationNeedsSetup() {
        Optional<CanonicalParameterDefinition> ingredient = PolicyStudioConvergencePresenter.registry().all()
                .stream()
                .filter(d -> {
                    Map<String, Object> st = CanonicalParameterStateService.state(d.id());
                    Object sem = st.get("semantic");
                    return sem instanceof Map<?, ?> m && "INGREDIENT".equals(String.valueOf(m.get("parameterClass")));
                })
                .findFirst();
        assertThat(ingredient).isPresent();
        Map<String, Object> st = CanonicalParameterStateService.state(ingredient.get().id());
        assertThat(String.valueOf(st.get("primaryStatusLabel"))).doesNotContainIgnoringCase("Calculation needs setup");
        assertThat(String.valueOf(st.get("nextAction"))).doesNotContainIgnoringCase("Set up calculation");
        Object calc = st.get("calculation");
        if (calc instanceof Map<?, ?> c) {
            assertThat(c.get("setupNotApplicable")).isEqualTo(true);
        }
    }

    @Test
    void policyRuleState_isSeparateContract() {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("lenderState", "READY_FOR_CONFIRMATION");
        life.put("ruleLifecycleLabel", "Needs review");
        life.put("showAcceptRule", true);
        Map<String, Object> before = CanonicalParameterStateService.state(DPD30);
        Map<String, Object> ruleState = PolicyRuleState.fromLifecycle(
                life, "r1", List.of(DPD30), Map.of("operator", "LTE", "threshold", 0));
        assertThat(ruleState.get("authority")).isEqualTo(PolicyRuleState.AUTHORITY);
        assertThat(ruleState.get("parameterStateIndependent")).isEqualTo(true);
        assertThat(ruleState.get("needsReview")).isEqualTo(true);
        Map<String, Object> after = CanonicalParameterStateService.state(DPD30);
        // Building PolicyRuleState must not mutate CanonicalParameterState
        assertThat(after.get("primaryStatus")).isEqualTo(before.get("primaryStatus"));
        assertThat(after.get("primaryStatusLabel")).isEqualTo(before.get("primaryStatusLabel"));
    }

    @Test
    void architectureGuard_consumersMustNotIndependentlyDerivePrimaryReadiness() throws Exception {
        Path root = Path.of("src/main/java").toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            root = Path.of("los-core-service/src/main/java").toAbsolutePath().normalize();
        }
        assertThat(Files.isDirectory(root)).as("source root %s", root).isTrue();

        // Allowed to call CapabilityProjection for non-primary/diagnostic roles only in these packages
        List<String> forbiddenPatterns = List.of(
                // inventory must not project capability for readiness
                "PolicyRuleGraphService.java::CanonicalParameterCapabilityProjection.project",
                // D&P enrich must use StateService
                "DataParametersAdminService.java::CanonicalParameterTruthProjection.project",
                // Scorecard picker must use StateService
                "ScorecardConvergenceService.java::CanonicalParameterTruthProjection.project",
                // Facade must not bypass StateService
                "SurfaceCanonicalTruthFacade.java::CanonicalParameterTruthProjection.project"
        );

        int violations = 0;
        StringBuilder detail = new StringBuilder();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
            for (String guard : forbiddenPatterns) {
                String[] parts = guard.split("::");
                String fileName = parts[0];
                String needle = parts[1];
                Optional<Path> file = javaFiles.stream()
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .findFirst();
                assertThat(file).as(fileName).isPresent();
                String src = Files.readString(file.get());
                if (src.contains(needle)) {
                    violations++;
                    detail.append(guard).append('\n');
                }
            }
            // PolicyStudioTestExperienceService must not invent UNRESOLVED from calculationRequired alone
            Path testExp = javaFiles.stream()
                    .filter(p -> p.getFileName().toString().equals("PolicyStudioTestExperienceService.java"))
                    .findFirst()
                    .orElseThrow();
            String testSrc = Files.readString(testExp);
            assertThat(testSrc).contains("CanonicalParameterStateService");
            assertThat(testSrc).doesNotContain("mergeParamByCanonical(byCanonical, fromMetricPath(\"bureau.max_dpd_6m\"");
        }
        assertThat(violations)
                .as("architecture violations:\n%s", detail)
                .isZero();
    }

    @Test
    void architectureGuard_policyStudioStateAxesRemainSeparate() throws Exception {
        Path uiRoot = Path.of("../ui-service/src").toAbsolutePath().normalize();
        if (!Files.isDirectory(uiRoot)) {
            uiRoot = Path.of("ui-service/src").toAbsolutePath().normalize();
        }
        Path javaRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        if (!Files.isDirectory(javaRoot)) {
            javaRoot = Path.of("los-core-service/src/main/java").toAbsolutePath().normalize();
        }
        String rules = Files.readString(uiRoot.resolve("pages/creditIntelligence/CiPolicyRulesTab.tsx"));
        // GUARD 1 — parameter label from presentation (CPS), not rule lifecycle string as Parameter:
        assertThat(rules).contains("presentation.parameterLabel");
        assertThat(rules).contains("Parameter:");
        assertThat(rules).doesNotContain("Parameter: {String(life.ruleLifecycleLabel");
        assertThat(rules).doesNotContain("Parameter: {status}");
        // GUARD 2 — rule chip from lifecycle
        assertThat(rules).contains("data-testid=\"rule-lifecycle-status\"");
        String sim = Files.readString(uiRoot.resolve("pages/creditIntelligence/CiPolicySimulationTab.tsx"));
        // GUARD 3 — Filled automatically only via testInputDisplayLabel (value-aware)
        assertThat(sim).contains("testInputDisplayLabel");
        assertThat(sim).doesNotContain("? 'Filled automatically'");
        String landing = Files.readString(uiRoot.resolve("pages/creditIntelligence/CiCreditPoliciesLanding.tsx"));
        // GUARD 4 — Need input from backend needsInputCount
        assertThat(landing).contains("needsInputCount");
        String lifeUi = Files.readString(uiRoot.resolve("pages/creditIntelligence/CiPolicyLifecycleTab.tsx"));
        // GUARD 6 — historical lifecycle displayed separately from current execution
        assertThat(lifeUi).contains("current-execution-readiness");
        assertThat(lifeUi).contains("lifecycle-status");
        String exec = Files.readString(javaRoot.resolve(
                "com/los/core/creditintelligence/policystudio/parameters/PolicyExecutionReadiness.java"));
        assertThat(exec).contains("currentParameterBlockers");
        assertThat(exec).contains("currentExecutionReadiness");
        String versions = Files.readString(javaRoot.resolve(
                "com/los/core/creditintelligence/policystudio/lifecycle/PolicyLifecycleService.java"));
        // GUARD 5 — required parameters from evaluate()
        assertThat(versions).contains("requiredParametersResolved");
        assertThat(versions).contains("currentParameterBlockers");
        // GUARD 6 — do not put BLOCKED into businessStatus from evaluate
        assertThat(versions).doesNotContain("life.put(\"businessStatus\", currentExecution");
        String score = Files.readString(javaRoot.resolve(
                "com/los/core/service/underwriting/CanonicalScorecardValueResolver.java"));
        // GUARD 7 — scoring uses valueAvailable, not lender READY
        assertThat(score).contains("valueAvailable");
        String resolver = Files.readString(uiRoot.resolve("lib/policyStudio/policyStudioResolverState.ts"));
        // GUARD 8 — frontend maps enum→label; cannot use Needs review as parameter label
        assertThat(resolver).contains("Needs review' ? 'Needs your input'");
    }

    @Test
    void cleanHistory_andDpd_shareStateAuthority() {
        for (String id : List.of(DPD30, CLEAN, CC_OVERDUE)) {
            Map<String, Object> st = CanonicalParameterStateService.state(id);
            assertThat(st.get("stateAuthority")).isEqualTo(CanonicalParameterStateService.AUTHORITY);
            Map<String, Object> stamped = new LinkedHashMap<>();
            CanonicalParameterStateService.stamp(stamped, id);
            assertThat(stamped.get("primaryStatus")).isEqualTo(st.get("primaryStatus"));
        }
    }
}
