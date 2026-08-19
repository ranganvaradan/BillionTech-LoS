package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationRepository;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluation;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluationService;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyStudioCanonicalApplicationTestTest {

    private PolicyStudioTestExperienceService testExperience;
    private CanonicalObservationalEvaluationService observational;
    private UUID docId;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setDefaultTenantId(tenantId);
        props.getStagingDemo().setEnabled(true);
        props.getCutover().setAllowCanonicalAuthority(false);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        StagingPolicyStudioDemoService demo = new StagingPolicyStudioDemoService(
                props, orch, new PolicyTextExtractionService());
        StagingProspectSimulationService sim = new StagingProspectSimulationService(props, orch);
        DerivedCalculationDefinitionService defs = mock(DerivedCalculationDefinitionService.class);
        when(defs.latestFor(any(), any())).thenReturn(Optional.empty());
        CanonicalParameterExecutionService spine = ExecutionSpineProducerBootstrap.standalone(defs);
        observational = mock(CanonicalObservationalEvaluationService.class);
        CanonicalApplicationConfigurationRepository freezeRepo = mock(CanonicalApplicationConfigurationRepository.class);
        LoanApplicationRepository loans = mock(LoanApplicationRepository.class);
        testExperience = new PolicyStudioTestExperienceService(
                props, orch, sim, spine, mock(UnderwritingScorecardRepository.class),
                observational, freezeRepo, loans);

        Map<String, Object> bureau = demo.build("bureau");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) bureau.get("policyHeader");
        docId = UUID.fromString(String.valueOf(header.get("documentId")));
        when(loans.findById(any())).thenReturn(Optional.of(LoanApplication.builder()
                .id(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
                .applicationNumber("LOS-TEST-1")
                .build()));
    }

    @Test
    void missingApplicationSelectionStill400() {
        assertThatThrownBy(() -> testExperience.runApplicationTest(docId, Map.of(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Select an application to test");
    }

    @Test
    void frozenApplicationTestUsesObservationalPipelineAndIncludesScorecard() {
        UUID appId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID policyId = docId;
        CanonicalApplicationConfiguration freeze = new CanonicalApplicationConfiguration(
                appId, null, 1, "CAT", null, 1, null, policyId, 1, "v1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), 1,
                true, false, UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "EQUIFAX", null, null, LocalDate.of(2026, 8, 19), List.of(), null, Map.of());
        CanonicalPolicyResult policy = new CanonicalPolicyResult(
                policyId.toString(), "1", "gacat", LocalDate.of(2026, 8, 19),
                CanonicalPolicyResult.OverallOutcome.DATA_INSUFFICIENT,
                List.of(), List.of(), List.of("r1"), List.of(), Map.of(), Map.of());
        Map<String, Object> scorecard = new java.util.LinkedHashMap<>();
        scorecard.put("scorecardId", freeze.scorecardId().toString());
        scorecard.put("scorecardVersion", 1);
        scorecard.put("bandOutcome", "DATA_INSUFFICIENT");
        scorecard.put("outcome", "DATA_INSUFFICIENT");
        scorecard.put("parameterResults", List.of(Map.of(
                "parameter", "bureau.score",
                "canonicalParameterId", "bureau.score",
                "valueUsed", "758",
                "dataState", "PRESENT",
                "rawWeight", 50.0,
                "pointsEarned", 100)));
        CanonicalObservationalEvaluation ev = new CanonicalObservationalEvaluation(
                "COMPLETED",
                List.of(),
                freeze,
                "hash1",
                UUID.randomUUID(),
                null,
                policy,
                List.of(Map.of("parameterId", "bureau.score", "canonicalStatus", "VALUE_AVAILABLE", "canonicalValue", 758)),
                List.of(
                        Map.of("ruleId", "CM_ONE", "participates", true, "result", "DATA_INSUFFICIENT",
                                "parameterIds", List.of("bureau.accounts.writeoff_non_cc"), "operator", "LTE",
                                "onTrue", "PASS", "reason", "DI"),
                        Map.of("ruleId", "CM_BUREAU_ACCOUNT_SOLD_COUNT_LTE", "participates", false,
                                "result", "DATA_INSUFFICIENT", "deferred", true,
                                "disposition", "DEFERRED_SOURCE_NOT_PROVEN",
                                "parameterIds", List.of(), "reason", "NON_PARTICIPATING")
                ),
                scorecard,
                new PolicyScorecardPrecedence.PrecedenceResult(
                        FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT,
                        List.of("POLICY_INSUFFICIENT_OR_REFER"),
                        "POLICY_THEN_SCORECARD"),
                "DATA_INSUFFICIENT",
                CanonicalObservationalEvaluation.zeroLookups());
        when(observational.evaluate(appId)).thenReturn(ev);

        Map<String, Object> result = testExperience.runApplicationTest(docId, Map.of("applicationId", appId.toString()), null);
        assertThat(result.get("POLICY_TEST_APPLICATION_BOUND")).isEqualTo(true);
        assertThat(result.get("POLICY_TEST_FREEZE_IDENTITY_MATCH")).isEqualTo(true);
        assertThat(result.get("evidenceKind")).isEqualTo("FROZEN_APPLICATION");
        assertThat(result.get("scorecardIncluded")).isEqualTo(true);
        assertThat(result.get("LATEST_POLICY_LOOKUP_COUNT")).isEqualTo(0);
        assertThat(result.get("LATEST_SCORECARD_LOOKUP_COUNT")).isEqualTo(0);
        assertThat(result.get("participatingRuleCount")).isEqualTo(1);
        assertThat(result.get("POLICY_TEST_ACCOUNT_SOLD_EXECUTED")).isEqualTo(false);
        assertThat(result.get("ACCOUNT_SOLD_TREATED_AS_PASS")).isEqualTo(false);
        assertThat(result.get("simulatedDecisionCode")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(String.valueOf(result.get("evaluationEngine"))).contains("CanonicalObservationalEvaluationService");
        assertThat(result.get("canonicalRuntimeUsedForLiveDecision")).isEqualTo(false);
    }
}
