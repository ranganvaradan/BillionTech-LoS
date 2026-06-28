package com.los.core.service.workflow;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LoanApplicationRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowNotificationResolverServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private ActiveWorkflowConfigService activeWorkflowConfigService;

    @InjectMocks
    private WorkflowNotificationResolverService service;

    @Test
    void prefersProcessLevelMappingWhenConfigured() {
        UUID appId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(
                        Map.of("step", "VIDEO_KYC", "notifications", List.of(
                                Map.of("eventType", "VKYC_LINK", "channel", "EMAIL", "templateCode", "LEGACY_STEP_TEMPLATE")
                        ))
                ))
                .processNotificationMappings(List.of(
                        Map.of("processCode", "VKYC", "eventType", "VKYC_LINK", "channel", "EMAIL", "templateCode", "PROCESS_TEMPLATE")
                ))
                .build();

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(activeWorkflowConfigService.findActiveForApplication(any(LoanApplication.class)))
                .thenReturn(Optional.of(wf));

        var actions = service.resolveForApplication(
                appId,
                "VIDEO_KYC",
                "VKYC_LINK",
                List.of("a@b.com"),
                "DEFAULT_TEMPLATE",
                "EMAIL");

        assertFalse(actions.isEmpty());
        assertEquals("PROCESS_TEMPLATE", actions.get(0).getTemplateCode());
    }

    @Test
    void fallsBackToStepLevelWhenProcessMappingMissing() {
        UUID appId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(
                        Map.of("step", "VIDEO_KYC", "notifications", List.of(
                                Map.of("eventType", "VKYC_LINK", "channel", "EMAIL", "templateCode", "LEGACY_STEP_TEMPLATE")
                        ))
                ))
                .build();

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(activeWorkflowConfigService.findActiveForApplication(any(LoanApplication.class)))
                .thenReturn(Optional.of(wf));

        var actions = service.resolveForApplication(
                appId,
                "VIDEO_KYC",
                "VKYC_LINK",
                List.of("a@b.com"),
                "DEFAULT_TEMPLATE",
                "EMAIL");

        assertFalse(actions.isEmpty());
        assertEquals("LEGACY_STEP_TEMPLATE", actions.get(0).getTemplateCode());
    }
}
