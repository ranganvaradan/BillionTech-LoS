package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationFlowServiceRetryKycTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private IKycOrchestrationService kycOrchestrationService;
    @Mock
    private WorkflowExecutionCoordinator workflowExecutionCoordinator;
    @Mock
    private ICreditDecisionService creditDecisionService;
    @Mock
    private KfsService kfsService;
    @Mock
    private KfsPdfGenerationService kfsPdfGenerationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoanApplicationFlowService flowService;

    @Test
    void retryKyc_transitionsKycFailedToKycInProgress() {
        UUID id = UUID.randomUUID();
        LoanApplication app = buildApp(id, ApplicationStatus.KYC_FAILED);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse res = flowService.retryKyc(id);

        assertEquals(ApplicationStatus.KYC_IN_PROGRESS, res.getStatus());
        ArgumentCaptor<LoanApplication> saveCaptor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applicationRepository).save(saveCaptor.capture());
        assertEquals(ApplicationStatus.KYC_IN_PROGRESS, saveCaptor.getValue().getStatus());
        verify(auditService).logEvent(eq(id), eq("FLOW"), eq("KYC_RETRY"),
                isNull(), any(), anyMap(), anyString());
    }

    @Test
    void retryKyc_rejectsWhenNotKycFailed() {
        UUID id = UUID.randomUUID();
        LoanApplication app = buildApp(id, ApplicationStatus.KYC_IN_PROGRESS);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> flowService.retryKyc(id));
        assertEquals("KYC_RETRY_NOT_ALLOWED", ex.getReason());
    }

    private static LoanApplication buildApp(UUID id, ApplicationStatus status) {
        LoanApplication app = new LoanApplication();
        app.setId(id);
        app.setApplicationNumber("APP-1");
        app.setCustomerId(UUID.randomUUID());
        app.setBorrowerType(BorrowerType.INDIVIDUAL);
        app.setLoanProduct("PERSONAL");
        app.setStatus(status);
        return app;
    }
}
