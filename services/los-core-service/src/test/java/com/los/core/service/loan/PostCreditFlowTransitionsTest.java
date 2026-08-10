package com.los.core.service.loan;

import com.los.core.creditintelligence.service.CreditIntelligenceFoundationService;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.assignment.AssignmentRuleApplicationService;
import com.los.core.service.cam.CreditAppraisalService;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import com.los.core.service.underwriting.UnderwritingEvaluationService;
import com.los.core.service.underwriting.UnderwritingRuleEngine;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCreditFlowTransitionsTest {

    @Mock private LoanApplicationRepository applicationRepository;
    @Mock private IKycOrchestrationService kycOrchestrationService;
    @Mock private WorkflowExecutionCoordinator workflowExecutionCoordinator;
    @Mock private ICreditDecisionService creditDecisionService;
    @Mock private KfsService kfsService;
    @Mock private KfsPdfGenerationService kfsPdfGenerationService;
    @Mock private AuditService auditService;
    @Mock private UnderwritingRuleEngine underwritingRuleEngine;
    @Mock private ScorecardPolicyEngine scorecardPolicyEngine;
    @Mock private CreditAppraisalService creditAppraisalService;
    @Mock private SanctionRecordRepository sanctionRecordRepository;
    @Mock private CreditControlService creditControlService;
    @Mock private UnderwritingEvaluationService underwritingEvaluationService;
    @Mock private AssignmentRuleApplicationService assignmentRuleApplicationService;
    @Mock private CreditIntelligenceFoundationService creditIntelligenceFoundationService;
    @Mock private com.los.core.service.vkyc.VkycWorkflowService vkycWorkflowService;

    @InjectMocks
    private LoanApplicationFlowService flowService;

    @Test
    void proceedToSanctionPending_fromCamReviewed() {
        UUID id = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(id);
        app.setApplicationNumber("A-1");
        app.setStatus(ApplicationStatus.CAM_REVIEWED);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationResponse res = flowService.proceedToSanctionPending(id);

        assertEquals(ApplicationStatus.SANCTION_PENDING, res.getStatus());
        verify(auditService).logEvent(
                any(UUID.class), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void rejectAfterCamReview_fromSanctionPending() {
        UUID id = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(id);
        app.setStatus(ApplicationStatus.SANCTION_PENDING);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationResponse res = flowService.rejectAfterCamReview(id, "not eligible");

        assertEquals(ApplicationStatus.REJECTED, res.getStatus());
    }

    @Test
    void proceedToSanctionPending_rejectsWhenNotCamReviewed() {
        UUID id = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(id);
        app.setStatus(ApplicationStatus.CAM_READY);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        assertThrows(BusinessRuleException.class, () -> flowService.proceedToSanctionPending(id));
    }
}
