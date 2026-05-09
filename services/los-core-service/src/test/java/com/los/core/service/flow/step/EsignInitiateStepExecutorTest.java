package com.los.core.service.flow.step;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.config.EsignNotificationProperties;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.esign.EsignSigningLinkNotifier;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.IIntegrationRouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EsignInitiateStepExecutorTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private IIntegrationRouterService integrationRouter;
    @Mock
    private AuditService auditService;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;
    @Mock
    private EsignSigningLinkNotifier esignSigningLinkNotifier;
    @Mock
    private EsignNotificationProperties esignNotificationProperties;

    @InjectMocks
    private EsignInitiateStepExecutor executor;

    @Test
    void onSuccess_persistsEsignRequestAndOutputShapeUnchanged() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-9")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .status(ApplicationStatus.KFS_GENERATED)
                .build();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        Map<String, Object> signer = Map.of("name", "X");
        IIntegrationRouterService.ESignRouteResult result =
                new IIntegrationRouterService.ESignRouteResult(
                        true, "TX-9", "https://sign.example", null, "EMSIGNER");
        when(integrationRouter.routeESignRequest(eq(appId), any()))
                .thenReturn(result);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(esignNotificationProperties.isEnabled()).thenReturn(false);

        StepResult step = executor.execute(appId, Map.of("signerInfo", signer));
        assertTrue(step.success());
        assertEquals("TX-9", step.output().get("transactionId"));
        assertEquals("https://sign.example", step.output().get("signingUrl"));
        assertEquals(true, step.output().get("esignSuccess"));
        assertEquals("", step.output().get("errorMessage"));
        assertEquals("ESIGN_PENDING", step.output().get("status"));
        assertEquals("APP-9", step.output().get("applicationNumber"));
        assertEquals(appId, step.output().get("applicationId"));

        verify(esignRequestTrackingService).recordInitiationSuccess(
                eq(appId), eq("KFS_AGREEMENT"), eq("EMSIGNER"), eq("TX-9"), eq("https://sign.example"),
                any(), any(), eq("ESIGN_AGREEMENT"));
    }
}
