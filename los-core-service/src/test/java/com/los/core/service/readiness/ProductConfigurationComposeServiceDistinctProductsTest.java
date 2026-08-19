package com.los.core.service.readiness;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductConfigurationComposeServiceDistinctProductsTest {

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
    void optionsProductsAreDistinctCanonicalLoanProductCodesNotWorkflowRows() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        when(workflowConfigRepository.findAll()).thenReturn(List.of(
                workflow(id1, "Default Workflow - Individual - Term Loan", "INDIVIDUAL", "TERM_LOAN", true),
                workflow(id2, "Default Workflow - Company - Term Loan", "COMPANY", "TERM_LOAN", true),
                workflow(id3, "Default Workflow - Individual - Personal Loan", "INDIVIDUAL", "PERSONAL_LOAN", true),
                workflow(UUID.randomUUID(), "Inactive Workflow - Individual - Term Loan", "INDIVIDUAL", "TERM_LOAN", false)
        ));
        when(ruleSetRepository.findAll()).thenReturn(List.of());
        when(scorecardRepository.findAll()).thenReturn(List.of());
        when(assignmentRuleSetRepository.findAll()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) composeService.options().get("products");

        assertThat(products).hasSize(2);
        assertThat(products).extracting(m -> m.get("loanProduct")).containsExactlyInAnyOrder("PERSONAL_LOAN", "TERM_LOAN");
        assertThat(products).allSatisfy(m -> {
            assertThat(m).containsOnlyKeys("loanProduct");
            assertThat(m.get("loanProduct")).isNotNull();
        });
        assertThat(products).noneSatisfy(m -> assertThat(m).containsKey("sampleWorkflowName"));
    }

    private static WorkflowConfig workflow(UUID id, String name, String borrowerType, String loanProduct, boolean active) {
        return WorkflowConfig.builder()
                .id(id)
                .name(name)
                .borrowerType(borrowerType)
                .loanProduct(loanProduct)
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(active)
                .version(1)
                .workflowFamilyId(id)
                .publicationStatus("ACTIVE")
                .build();
    }
}
