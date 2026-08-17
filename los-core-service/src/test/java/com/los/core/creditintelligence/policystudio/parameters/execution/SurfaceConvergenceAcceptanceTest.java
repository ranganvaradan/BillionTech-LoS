package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleLifecycleProjection;
import com.los.core.creditintelligence.policystudio.sourceintegration.PlatformNormalizedRawFieldCatalog;
import com.los.core.service.readiness.DataParametersCapabilitySemantics;
import com.los.core.service.underwriting.ScorecardConvergenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SURFACE-CONVERGENCE-1 — D&amp;P / Gate3 / inventory-shaped projection / scorecard picker
 * agree with spine POLICY_TEST; designable remains independent of executable.
 */
class SurfaceConvergenceAcceptanceTest {

    private CanonicalParameterCapabilityParityService parity;
    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        var definitions = mock(com.los.core.creditintelligence.policystudio.parameters.derived
                .DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());
        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);
        parity = new CanonicalParameterCapabilityParityService(spine);
    }

    @Test
    void fullCatalogue_dpPsSpine_executionParity() {
        Map<String, Object> report = parity.runParityCheck();
        assertThat(report.get("catalogueCount")).isEqualTo(GacatCatalogueSeed.all().size());
        assertThat(report.get("dataParametersVsPolicyStudioDisagreementCount")).isEqualTo(0);
        assertThat(report.get("surfaceVsSpineDisagreementCount")).isEqualTo(0);
        assertThat(report.get("parityPass")).isEqualTo(true);
        assertThat(report.get("spineAligned")).isEqualTo(true);
    }

    /**
     * Disposition B — GOLDEN_12 is a historical disagreement inventory, not a freeze that
     * these IDs must stay not-executable. Equifax-normalized RAW fields
     * ({@link PlatformNormalizedRawFieldCatalog}) are correctly spine-capable via
     * RawFactProducer. Remaining IDs without a producer stay not-executable.
     * Surfaces must still agree with spine for every ID.
     */
    @Test
    void golden12_allAgreeWithSpine_notExecutable() {
        for (String id : CanonicalParameterCapabilityParityService.GOLDEN_12_DISAGREEMENT_IDS) {
            boolean spineCap = spine.hasExecutionCapability(id,
                    EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build());
            Map<String, Object> gate3 = ParameterExecutabilitySupport.evaluate(id);
            Map<String, Object> dp = DataParametersCapabilitySemantics.project(
                    PolicyStudioConvergencePresenter.registry().findById(id).orElseThrow());
            boolean structurallyMappedRaw = PlatformNormalizedRawFieldCatalog.isStructurallyMapped(id);
            if (structurallyMappedRaw) {
                assertThat(spineCap).as(id + " spine RAW via PlatformNormalizedRawFieldCatalog").isTrue();
            } else {
                assertThat(spineCap).as(id + " spine no producer").isFalse();
            }
            assertThat(gate3.get("policyTestReady")).as(id + " PS").isEqualTo(spineCap);
            assertThat(dp.get("policyTestReady")).as(id + " DP").isEqualTo(spineCap);
            assertThat(dp.get("policyDesign") instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("available")))
                    .as(id + " still designable").isTrue();
        }
    }

    @Test
    void implementedWithoutProducer_notExecutableOnSurfaces() {
        // Remaining Equifax ID without a BuiltIn producer
        String id = "bureau.thin_file_indicator";
        var def = PolicyStudioConvergencePresenter.registry().findById(id).orElseThrow();
        assertThat(def.capability().implemented()).isFalse();
        assertThat(ParameterExecutabilitySupport.evaluate(def).get("policyTestReady")).isEqualTo(false);
        assertThat(DataParametersCapabilitySemantics.project(def).get("policyTestReady")).isEqualTo(false);
        assertThat(CanonicalParameterCapabilityProjection.project(def).get("productionReady")).isEqualTo(false);
    }

    @Test
    void productionReadyCatalogueClaim_notCertified() {
        var def = PolicyStudioConvergencePresenter.registry().findById("bureau.score").orElseThrow();
        assertThat(def.capability().productionReady()).isTrue();
        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project(def);
        assertThat(proj.get("productionReady")).isEqualTo(false);
        assertThat(proj.get("productionCertified")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> cert = (Map<String, Object>) proj.get("productionCertification");
        assertThat(cert.get("status")).isEqualTo(CanonicalParameterCapabilityProjection.PROD_CERT_NOT_ESTABLISHED);
    }

    @Test
    void designableButNotExecutable_remainsSelectable() {
        String id = "financial.revenue";
        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project(id);
        assertThat(proj.get("designable")).isEqualTo(true);
        assertThat(proj.get("policyTestReady")).isEqualTo(false);
        Map<String, Object> dp = DataParametersCapabilitySemantics.project(
                PolicyStudioConvergencePresenter.registry().findById(id).orElseThrow());
        assertThat(((Map<?, ?>) dp.get("policyDesign")).get("available")).isEqualTo(true);
    }

    @Test
    void ruleReadyToTest_requiresAllOperandsSpineCapable() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("includedForActivation", true);
        card.put("authoringComplete", true);
        card.put("operands", List.of(
                Map.of("canonicalParameterId", "bureau.score", "unresolved", false),
                Map.of("canonicalParameterId", "bureau.thin_file_indicator", "unresolved", false)
        ));
        Map<String, Object> meta = Map.of("disposition", "ACCEPTED");
        var facts = PolicyRuleLifecycleProjection.factsFromCard(card, meta);
        assertThat(facts.policyTestReady()).isFalse();

        card.put("operands", List.of(
                Map.of("canonicalParameterId", "bureau.score", "unresolved", false),
                Map.of("canonicalParameterId", "bureau.recent_inquiries_90d", "unresolved", false)
        ));
        facts = PolicyRuleLifecycleProjection.factsFromCard(card, meta);
        assertThat(facts.policyTestReady()).isTrue();
    }

    @Test
    void scorecardPicker_showsSpineCapability_notCatalogueProduction() throws Exception {
        Method m = ScorecardConvergenceService.class.getDeclaredMethod(
                "toPickerItem",
                com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition.class);
        m.setAccessible(true);
        var def = PolicyStudioConvergencePresenter.registry().findById("bureau.score").orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) m.invoke(null, def);
        assertThat(item.get("policyTestReady")).isEqualTo(true);
        assertThat(item.get("productionReady")).isEqualTo(false);
        assertThat(item.get("designable")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> adv = (Map<String, Object>) item.get("advanced");
        assertThat(adv.get("scorecardRuntimeAuthority")).isEqualTo("CanonicalParameterExecutionService");
    }

    @Test
    void vikasam13_surfaceParityTable() {
        List<String> ids = Vikasam13ExecutionAcceptanceTest.VIKASAM_13;
        for (String id : ids) {
            boolean spineCap = spine.hasExecutionCapability(id,
                    EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).build());
            Map<String, Object> gate3 = ParameterExecutabilitySupport.evaluate(id);
            Map<String, Object> dp = DataParametersCapabilitySemantics.project(
                    PolicyStudioConvergencePresenter.registry().findById(id).orElseThrow());
            assertThat(gate3.get("policyTestReady")).as(id + " PS").isEqualTo(spineCap);
            assertThat(dp.get("policyTestReady")).as(id + " DP").isEqualTo(spineCap);
            assertThat(dp.get("productionReady")).as(id + " cert").isEqualTo(false);
        }
    }
}
