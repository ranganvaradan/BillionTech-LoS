package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kyc.IKycOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BureauPullStepExecutorTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private KycStepResultRepository kycStepResultRepository;
    @Mock
    private IKycOrchestrationService kycOrchestrationService;
    @Mock
    private CreditControlService creditControlService;
    @Mock
    private IIntegrationRouterService integrationRouter;
    @Mock
    private AuditService auditService;
    @Mock
    private com.los.core.service.workflow.ActiveWorkflowConfigService activeWorkflowConfigService;
    @Mock
    private com.los.core.creditintelligence.bureau.service.BureauIngestionService bureauIngestionService;

    @InjectMocks
    private BureauPullStepExecutor executor;

    @Test
    void execute_blocksWhenEffectiveKycNotPass() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = baseApp(appId);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(activeWorkflowConfigService.findActiveForApplication(any()))
                .thenReturn(Optional.of(WorkflowConfig.builder()
                        .id(UUID.fromString("00000000-0000-0000-0000-00000000bf01"))
                        .name("pinned")
                        .borrowerType("INDIVIDUAL")
                        .loanProduct("PERSONAL")
                        .active(true)
                        .bureauEnabled(true)
                        .build()));
        when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(appId))
                .thenReturn(Map.of("outcome", "FAIL", "stepSummary", java.util.List.of()));
        when(creditControlService.resolveEffective(eq(app), eq("FAIL")))
                .thenReturn(new com.los.core.service.credit.EffectiveUnderwritingContext(
                        0, false, null, null, null, null, "PROVIDER", "PROVIDER", "PROVIDER", Map.of()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> executor.execute(appId, Map.of()));
        assertEquals("KYC_OUTCOME_NOT_PASS", ex.getReason());
    }

    @Test
    void execute_allowsBureauPullAfterManualKycOverride() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = baseApp(appId);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(activeWorkflowConfigService.findActiveForApplication(any()))
                .thenReturn(Optional.of(WorkflowConfig.builder()
                        .id(UUID.fromString("00000000-0000-0000-0000-00000000bf01"))
                        .name("pinned")
                        .borrowerType("INDIVIDUAL")
                        .loanProduct("PERSONAL")
                        .active(true)
                        .bureauEnabled(true)
                        .build()));
        when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(appId))
                .thenReturn(Map.of("outcome", "FAIL", "stepSummary", java.util.List.of()));
        when(creditControlService.resolveEffective(eq(app), eq("FAIL")))
                .thenReturn(new com.los.core.service.credit.EffectiveUnderwritingContext(
                        0, true, null, null, null, null, "PROVIDER", "PROVIDER", "MANUAL", Map.of()));
        when(integrationRouter.routeBureauPull(any()))
                .thenReturn(new IIntegrationRouterService.BureauRouteResult(
                        true, 710, Map.of("creditScore", 710), "TX-1", null));
        when(kycStepResultRepository.save(any())).thenAnswer(inv -> {
            var r = inv.getArgument(0);
            try {
                var idField = r.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                if (idField.get(r) == null) {
                    idField.set(r, UUID.randomUUID());
                }
            } catch (Exception ignored) {
                /* builder may already set */
            }
            return r;
        });
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bureauIngestionService.isEnabledFor(any())).thenReturn(false);

        StepResult step = executor.execute(appId, Map.of());
        assertTrue(step.success());
        assertEquals(710, step.output().get("creditScore"));
    }

    private static LoanApplication baseApp(UUID appId) {
        return LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .workflowId(UUID.fromString("00000000-0000-0000-0000-00000000bf01"))
                .status(ApplicationStatus.KYC_IN_PROGRESS)
                .personalInfo(Map.of("pan", "ABCDE1234F"))
                .build();
    }
}
