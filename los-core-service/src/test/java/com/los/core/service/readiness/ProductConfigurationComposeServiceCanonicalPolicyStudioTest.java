package com.los.core.service.readiness;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.AssignmentRuleSetRepository;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.lms.repository.ExternalProductMappingRepository;
import com.los.lms.service.ExternalProductMappingPinningService;
import com.los.lms.service.LmsApplicationConfigResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductConfigurationComposeServiceCanonicalPolicyStudioTest {

    @Mock
    private WorkflowConfigRepository workflowConfigRepository;

    @Mock
    private UnderwritingRuleSetRepository ruleSetRepository;

    @Mock
    private UnderwritingScorecardRepository scorecardRepository;

    @Mock
    private AssignmentRuleSetRepository assignmentRuleSetRepository;

    @Mock
    private ProductReadinessValidator readinessValidator;

    @Mock
    private PolicyRequiredParameterExtractor parameterExtractor;

    @Mock
    private ProductRoutingConvergenceService routingConvergenceService;

    @Mock
    private LmsApplicationConfigResolver lmsApplicationConfigResolver;

    @Mock
    private ExternalProductMappingPinningService externalProductMappingPinningService;

    @Mock
    private ExternalProductMappingRepository externalProductMappingRepository;

    @InjectMocks
    private ProductConfigurationComposeService composeService;

    @Test
    void whenPolicyStudioSelected_liveRuleSetIsNotRequiredAndPolicyStudioIsCanonical() {
        UUID wfId = UUID.randomUUID();

        WorkflowConfig wf = WorkflowConfig.builder()
                .id(wfId)
                .name("WF")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .lmsProductCode("LEGACY_LMS_CODE")
                .lmsTenureUnit("Month")
                .intakeSegment("BORROWER")
                .steps(List.of(Map.of("step", "PAN_VERIFY")))
                .active(true)
                .bureauEnabled(true)
                .autoPullBureauAfterKycSuccess(true)
                .version(1)
                .workflowFamilyId(wfId)
                .publicationStatus("ACTIVE")
                .build();

        when(workflowConfigRepository.findById(eq(wfId))).thenReturn(Optional.of(wf));
        when(workflowConfigRepository.findAll()).thenReturn(List.of());
        when(ruleSetRepository.findAll()).thenReturn(List.of());
        when(scorecardRepository.findAll()).thenReturn(List.of());

        // Readiness is mocked here; canonical behavior is asserted separately in validator tests.
        when(readinessValidator.validate(anyString(), anyString(), any(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(Map.of(
                        "requiredParameters", List.of(),
                        "gaps", List.of(),
                        "ready", true,
                        "status", "READY",
                        "workflowSuppliesRequiredAutomaticData", true,
                        "scopeCompatible", true,
                        "workflowProvidesParameterIds", List.of(),
                        "checks", Map.of()));

        when(routingConvergenceService.compareSelectionToRuntime(anyString(), anyString(), anyString(), any(), eq(wfId), isNull(), isNull()))
                .thenReturn(Map.of(
                        "runtime", Map.of(
                                "workflow", Map.of("id", wfId, "version", 1, "name", "WF", "match", "EXACTLY_ONE", "resolvedByPriority", false),
                                "liveRuleSet", Map.of("id", UUID.randomUUID(), "match", "NO_MATCH"),
                                "scorecard", Map.of("id", UUID.randomUUID(), "match", "NO_MATCH")),
                        "uniqueness", Map.of(
                                "workflow", "EXACTLY_ONE",
                                "liveRuleSet", "NO_MATCH",
                                "scorecard", "NO_MATCH"),
                        "productConfigMatchesRuntime", true,
                        "mismatchKeys", List.of(),
                        "messages", List.of()));

        doThrow(new BusinessRuleException("force lms missing"))
                .when(externalProductMappingPinningService).pinEncoreMappingIfNeeded(any());
        when(lmsApplicationConfigResolver.resolveEncoreProductMapping(any()))
                .thenReturn(Optional.empty());

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("borrowerType", "COMPANY");
        body.put("loanProduct", "TERM_LOAN");
        body.put("intakeSegment", "BORROWER");
        body.put("workflowId", wfId);
        body.put("policyDocumentId", UUID.randomUUID().toString());

        Map<String, Object> out = composeService.compose(body);
        Map<String, Object> compose = (Map<String, Object>) out.get("compose");

        Map<String, Object> policyStudio = (Map<String, Object>) compose.get("policyStudio");
        assertThat((Boolean) policyStudio.get("notProductionAuthority")).isEqualTo(false);

        assertThat(compose.get("liveRuleSet")).isNull();
    }
}

