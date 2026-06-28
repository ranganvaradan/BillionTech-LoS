package com.los.core.service.workflow;

import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Documents that {@link WorkflowEngineServiceImpl#getActiveWorkflow} matches
 * {@code borrowerType.name()}, {@code loanProduct}, and {@code intakeSegment} (V50+).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceGetActiveWorkflowTest {

    @Mock
    private WorkflowConfigRepository workflowConfigRepository;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void getActiveWorkflow_resolvesIndidualPersonal() {
        UUID id = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
        List<Map<String, Object>> steps = List.of(
                Map.of("step", "PAN_VERIFY", "mandatory", true, "order", 1),
                Map.of("step", "AADHAAR_OTP", "mandatory", true, "order", 2),
                Map.of("step", "BUREAU_PULL", "mandatory", true, "order", 3)
        );
        WorkflowConfig config = WorkflowConfig.builder()
                .id(id)
                .name("INDIVIDUAL_PERSONAL_DISPLAY")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(steps)
                .active(true)
                .version(1)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
                "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER"))
                .thenReturn(Optional.of(config));

        WorkflowConfigResponse result = workflowEngineService.getActiveWorkflow(BorrowerType.INDIVIDUAL, "PERSONAL_LOAN");
        assertNotNull(result);
        assertEquals("PERSONAL_LOAN", result.getLoanProduct());
        assertEquals(3, result.getSteps().size());
        assertTrue(result.getSteps().stream().anyMatch(m -> "PAN_VERIFY".equals(m.get("step"))));
        assertTrue(result.getSteps().stream().anyMatch(m -> "AADHAAR_OTP".equals(m.get("step"))));
        assertTrue(result.getSteps().stream().anyMatch(m -> "BUREAU_PULL".equals(m.get("step"))));
        verify(workflowConfigRepository).findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN"), eq("BORROWER"));
    }

    @Test
    void getActiveWorkflow_throwsWhenNoRow() {
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
                "INDIVIDUAL", "UnknownProduct", "BORROWER"))
                .thenReturn(Optional.empty());
        com.los.core.exception.ResourceNotFoundException ex = assertThrows(
                com.los.core.exception.ResourceNotFoundException.class,
                () -> workflowEngineService.getActiveWorkflow(BorrowerType.INDIVIDUAL, "UnknownProduct")
        );
        assertTrue(ex.getMessage().contains("No active workflow for INDIVIDUAL/UnknownProduct"));
    }

    @Test
    void getActiveWorkflow_anchorSegment_usesTripleLookup() {
        UUID id = UUID.randomUUID();
        WorkflowConfig config = WorkflowConfig.builder()
                .id(id)
                .name("anchor")
                .borrowerType("COMPANY")
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .intakeSegment("ANCHOR")
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "order", 1)))
                .active(true)
                .version(1)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
                "COMPANY", "BUSINESS_WC_INVOICE_DISCOUNTING", "ANCHOR"))
                .thenReturn(Optional.of(config));

        WorkflowConfigResponse r = workflowEngineService.getActiveWorkflow(
                BorrowerType.COMPANY, "BUSINESS_WC_INVOICE_DISCOUNTING", IntakeSegment.ANCHOR);
        assertEquals("ANCHOR", r.getIntakeSegment());
    }
}
