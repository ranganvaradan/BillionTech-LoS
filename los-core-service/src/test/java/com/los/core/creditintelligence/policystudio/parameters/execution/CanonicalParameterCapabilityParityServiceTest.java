package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalParameterCapabilityParityServiceTest {

    private CanonicalParameterCapabilityParityService parity;

    @BeforeEach
    void setUp() {
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());
        CanonicalParameterExecutionService spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);
        parity = new CanonicalParameterCapabilityParityService(spine);
    }

    @Test
    void afterConvergence_zeroDisagreementsAndSpineAligned() {
        Map<String, Object> report = parity.runParityCheck();
        assertThat(report.get("catalogueCount")).isEqualTo(169);
        assertThat(report.get("dataParametersVsPolicyStudioDisagreementCount")).isEqualTo(0);
        assertThat(report.get("surfaceVsSpineDisagreementCount")).isEqualTo(0);
        assertThat(report.get("parityPass")).isEqualTo(true);
        assertThat(report.get("spineAligned")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> golden =
                (List<Map<String, Object>>) report.get("known12DisagreementsAfter");
        assertThat(golden).hasSize(12);
        for (Map<String, Object> row : golden) {
            assertThat(row.get("policyStudioClaimsExecutable")).isEqualTo(false);
            assertThat(row.get("dataParametersClaimsExecutable")).isEqualTo(false);
            assertThat(row.get("spineHasExecutionCapability")).isEqualTo(false);
        }

        System.out.println("SPINE_POLICY_TEST_CAPABLE_COUNT=" + report.get("spineHasExecutionCapabilityCount"));
        System.out.println("SPINE_WORKFLOW_CAPABLE_COUNT=" + report.get("spineWorkflowCapableCount"));
        System.out.println("SPINE_UNDERWRITING_CAPABLE_COUNT=" + report.get("spineUnderwritingCapableCount"));
        System.out.println("DP_EXECUTABLE_COUNT=" + report.get("dataParametersClaimsExecutableCount"));
        System.out.println("PS_EXECUTABLE_COUNT=" + report.get("policyStudioClaimsExecutableCount"));
    }
}
