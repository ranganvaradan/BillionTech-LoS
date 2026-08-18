package com.los.core.service.kyc;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.AuditEventRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.ManualKycReviewRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KycOrchestrationServiceBureauExclusionTest {

    @Mock
    private KycStepResultRepository kycStepResultRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private ManualKycReviewRepository manualKycReviewRepository;
    @Mock
    private IIntegrationRouterService integrationRouter;
    @Mock
    private ApplicationWorkflowResolver applicationWorkflowResolver;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditEventRepository auditEventRepository;

    private KycOrchestrationServiceImpl kycOrchestrationService;

    private final UUID appId = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @BeforeEach
    void setUp() {
        kycOrchestrationService = new KycOrchestrationServiceImpl(
                kycStepResultRepository,
                loanApplicationRepository,
                manualKycReviewRepository,
                integrationRouter,
                applicationWorkflowResolver,
                auditService,
                auditEventRepository
        );
    }

    @Test
    void executeWorkflow_doesNotRouteBureauAsKycStep() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("T-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .status(ApplicationStatus.KYC_IN_PROGRESS)
                .workflowId(UUID.fromString("00000000-0000-0000-0000-00000000b001"))
                .build();
        app.setId(appId);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(kycStepResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationRouter.routeKycRequest(eq(appId), eq(KycStepType.PAN_VERIFY), any(), ArgumentMatchers.isNull()))
                .thenReturn(new IIntegrationRouterService.KycRouteResult(
                        true,
                        "KARZA",
                        Map.of("confidenceScore", 0.9, "parsedData", Map.of(), "transactionId", "tx-1"),
                        null
                ));
        WorkflowConfig cfg = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "mandatory", true, "order", 1),
                        Map.of("step", "BUREAU_PULL", "mandatory", true, "order", 2, "provider", "EQUIFAX")
                ))
                .build();
        when(applicationWorkflowResolver.requireConfig(app)).thenReturn(cfg);

        var results = kycOrchestrationService.executeWorkflow(appId, Map.of());

        assertEquals(1, results.size());
        assertEquals(KycStepType.PAN_VERIFY, results.get(0).getStepType());
        verify(integrationRouter, never()).routeKycRequest(
                any(), eq(KycStepType.BUREAU_PULL), any(), any()
        );
    }

    @Test
    void computeKycOutcome_ignoresBureauRowInWorkflowJson() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("T-2")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .workflowId(UUID.fromString("00000000-0000-0000-0000-00000000b002"))
                .build();
        app.setId(appId);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(appId)).thenReturn(List.of());
        when(manualKycReviewRepository.findByApplicationIdOrderByUpdatedAtDesc(appId)).thenReturn(List.of());
        WorkflowConfig cfg = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "mandatory", true, "order", 1),
                        Map.of("step", "BUREAU_PULL", "mandatory", true, "order", 2)
                ))
                .build();
        when(applicationWorkflowResolver.requireConfig(app)).thenReturn(cfg);

        Map<String, Object> out = kycOrchestrationService.computeKycOutcome(appId);
        // Missing mandatory identity (PAN) → FAIL; BUREAU_PULL is ignored as non-identity.
        assertEquals("FAIL", out.get("outcome"));
    }

    @Test
    void computeKycOutcome_unconfiguredDraftIsPending() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("T-3")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .build();
        app.setId(appId);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        Map<String, Object> out = kycOrchestrationService.computeKycOutcome(appId);
        assertEquals("CONFIGURATION_PENDING", out.get("outcome"));
        assertEquals(true, out.get("configurationPending"));
        verify(applicationWorkflowResolver, never()).requireConfig(any());
    }
}
