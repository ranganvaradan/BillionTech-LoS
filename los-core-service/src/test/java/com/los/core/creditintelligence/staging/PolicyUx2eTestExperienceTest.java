package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * POLICY-UX-2E — Quick Test / application test on existing evaluator.
 */
class PolicyUx2eTestExperienceTest {

    private StagingPolicyStudioDemoService demo;
    private PolicyStudioTestExperienceService testExperience;
    private CreditIntelligenceProperties props;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        props = new CreditIntelligenceProperties();
        props.setDefaultTenantId(tenantId);
        props.getStagingDemo().setEnabled(true);
        props.getCutover().setAllowCanonicalAuthority(false);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        demo = new StagingPolicyStudioDemoService(props, orch, new PolicyTextExtractionService());
        StagingProspectSimulationService sim = new StagingProspectSimulationService(props, orch);
        DerivedCalculationDefinitionService defs = mock(DerivedCalculationDefinitionService.class);
        when(defs.latestFor(any(), any())).thenReturn(Optional.empty());
        CanonicalParameterExecutionService spine = ExecutionSpineProducerBootstrap.standalone(defs);
        UnderwritingScorecardRepository scorecards = mock(UnderwritingScorecardRepository.class);
        testExperience = new PolicyStudioTestExperienceService(props, orch, sim, spine, scorecards);
    }

    @Test
    void quickTestUsesDraftRulesAndDoesNotMutatePolicy() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        Map<String, Object> ctx = testExperience.testContext(docId, null);
        assertThat(ctx.get("title")).isEqualTo("Test Policy");
        assertThat(ctx.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(ctx.get("modes")).isInstanceOf(List.class);
        assertThat(ctx.get("requiredParameters")).isInstanceOf(List.class);

        Map<String, Object> result = testExperience.runQuickTest(docId, Map.of(
                "testValues", Map.of(
                        "average_daily_balance", 75000,
                        "proposed_edi", 60000,
                        "bureau_score", 720
                ),
                "product", "DIGILEAP"), null);

        assertThat(result.get("testType")).isEqualTo("QUICK");
        assertThat(result.get("policyMutated")).isEqualTo(false);
        assertThat(result.get("ruleIdsUnchanged")).isEqualTo(true);
        assertThat(result.get("simulatedDecision")).isNotNull();
        assertThat(result.get("ruleResults")).isInstanceOf(List.class);
        assertThat(result.get("scorecardIncluded")).isEqualTo(false);
        assertThat(String.valueOf(result.get("evaluationEngine"))).contains("PolicyDslInterpreterV1");
    }

    @Test
    void unresolvedEdiIsNotInventedWithoutTestValue() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        Map<String, Object> ctx = testExperience.testContext(docId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) ctx.get("requiredParameters");
        boolean ediPresent = params.stream().anyMatch(p ->
                "proposed_edi".equals(String.valueOf(p.get("parameterKey"))));
        assertThat(ediPresent).isTrue();
        Map<String, Object> edi = params.stream()
                .filter(p -> "proposed_edi".equals(String.valueOf(p.get("parameterKey"))))
                .findFirst().orElseThrow();
        // READY/MANUAL is not UNRESOLVED and must not look like a populated automatic value.
        assertThat(String.valueOf(edi.get("status")))
                .isIn("UNRESOLVED", "MANUAL_INPUT", "WAITING_FOR_DATA", "INPUT_REQUIRED");
        assertThat(String.valueOf(edi.get("status")))
                .isNotIn("VALUE_AVAILABLE", "AUTOMATIC_DERIVED");

        Map<String, Object> withoutEdi = testExperience.runQuickTest(docId, Map.of(
                "testValues", Map.of("average_daily_balance", 75000),
                "product", "DIGILEAP"), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) withoutEdi.get("ruleResults");
        boolean cannot = rules.stream().anyMatch(r ->
                "CANNOT_EVALUATE".equals(String.valueOf(r.get("resultCode")))
                        || String.valueOf(r.get("why")).toLowerCase().contains("unresolved")
                        || String.valueOf(r.get("ruleName")).toLowerCase().contains("adb"));
        assertThat(cannot || !((List<?>) withoutEdi.getOrDefault("blockers", List.of())).isEmpty()).isTrue();
    }

    @Test
    void manualTestValueIsSimulationOnly() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        Map<String, Object> result = testExperience.runQuickTest(docId, Map.of(
                "testValues", Map.of(
                        "average_daily_balance", 90000,
                        "proposed_edi", 50000),
                "product", "DIGILEAP"), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prov = (List<Map<String, Object>>) result.get("valueProvenance");
        assertThat(prov.stream().anyMatch(p ->
                "MANUAL_TEST_VALUE".equals(String.valueOf(p.get("status")))
                        && Boolean.TRUE.equals(p.get("simulationOnly")))).isTrue();
    }

    @Test
    void applicationTestDoesNotMutateApplication() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        Map<String, Object> result = testExperience.runApplicationTest(docId, Map.of(
                "applicationCode", "APP_001_STRONG_DIGILEAP"), null);
        assertThat(result.get("applicationMutated")).isEqualTo(false);
        assertThat(result.get("testType")).isEqualTo("APPLICATION");
        assertThat(result.get("mutationGuarantees")).isInstanceOf(List.class);
        assertThat(result.get("ruleResults")).isInstanceOf(List.class);
    }

    @Test
    void compoundAndCleanUnresolvedSurfacedForBureau() {
        Map<String, Object> bureau = demo.build("bureau");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) bureau.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        Map<String, Object> result = testExperience.runQuickTest(docId, Map.of(
                "testValues", Map.of("bureau_score", 720),
                "product", "DIGILEAP"), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) result.get("ruleResults");
        boolean compound = rules.stream().anyMatch(r -> Boolean.TRUE.equals(r.get("compound")));
        boolean cleanBlock = rules.stream().anyMatch(r ->
                String.valueOf(r.get("why")).toLowerCase().contains("clean")
                        || (r.get("children") instanceof List<?> ch && ch.stream().anyMatch(c ->
                        String.valueOf(((Map<?, ?>) c).get("ruleName")).toLowerCase().contains("clean"))));
        assertThat(compound || cleanBlock).isTrue();
    }

    @Test
    void historicalBatchMarkedUnavailable() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));
        Map<String, Object> ctx = testExperience.testContext(docId, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> batch = (Map<String, Object>) ctx.get("historicalBatch");
        assertThat(batch.get("available")).isEqualTo(false);
        assertThat(props.getCutover().isAllowCanonicalAuthority()).isFalse();
    }

    @Test
    void dataCalculationItemsExcludedFromRuleResultCounts() {
        Map<String, Object> banking = demo.build("banking");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) banking.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));
        Map<String, Object> result = testExperience.runQuickTest(docId, Map.of(
                "testValues", Map.of(
                        "average_daily_balance", 75000,
                        "proposed_edi", 60000),
                "product", "DIGILEAP"), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) result.get("ruleResults");
        int total = ((Number) summary.get("total")).intValue();
        assertThat(total).isEqualTo(rules.size());
        assertThat(rules.stream().noneMatch(r ->
                String.valueOf(r.get("systemRuleId")).toUpperCase().contains("DATA_CALC"))).isTrue();
    }
}
