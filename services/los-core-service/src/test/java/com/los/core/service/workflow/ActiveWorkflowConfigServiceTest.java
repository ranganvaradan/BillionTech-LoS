package com.los.core.service.workflow;

import com.los.core.model.entity.LoanApplication;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveWorkflowConfigServiceTest {

    @Mock
    private WorkflowConfigRepository workflowConfigRepository;

    @InjectMocks
    private ActiveWorkflowConfigService service;

    @Test
    void findActive_defaultsToBorrowerWhenSegmentNull() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .intakeSegment(null)
                .build();
        WorkflowConfig cfg = WorkflowConfig.builder().id(UUID.randomUUID()).build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        "COMPANY", "BUSINESS_WC_INVOICE_DISCOUNTING", "BORROWER"))
                .thenReturn(List.of(cfg));
        assertTrue(service.findActiveForApplication(app).isPresent());
        verify(workflowConfigRepository).findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                eq("COMPANY"), eq("BUSINESS_WC_INVOICE_DISCOUNTING"), eq("BORROWER"));
    }

    @Test
    void findActive_passesAnchorSegmentName() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .intakeSegment(IntakeSegment.ANCHOR)
                .build();
        WorkflowConfig cfg = WorkflowConfig.builder().id(UUID.randomUUID()).build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        "COMPANY", "BUSINESS_WC_INVOICE_DISCOUNTING", "ANCHOR"))
                .thenReturn(List.of(cfg));
        assertEquals(cfg, service.findActiveForApplication(app).orElseThrow());
    }

    @Test
    void findActive_prefersBoundWorkflowId() {
        UUID boundId = UUID.randomUUID();
        WorkflowConfig bound = WorkflowConfig.builder().id(boundId).name("bound").build();
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment(IntakeSegment.BORROWER)
                .workflowId(boundId)
                .build();
        when(workflowConfigRepository.findById(boundId)).thenReturn(Optional.of(bound));
        assertEquals(bound, service.findActiveForApplication(app).orElseThrow());
        verify(workflowConfigRepository).findById(boundId);
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        any(), any(), any());
    }

    @Test
    void findActive_emptyWhenLoanProductBlank() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("  ")
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        assertTrue(service.findActiveForApplication(app).isEmpty());
        verifyNoInteractions(workflowConfigRepository);
    }
}
