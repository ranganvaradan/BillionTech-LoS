package com.los.core.service.integration.webhook;

import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.service.audit.AuditService;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.loan.LoanApplicationFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationCallbackProcessorEsignTest {

    @Mock
    private AuditService auditService;
    @Mock
    private LoanApplicationFlowService flowService;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;

    @Test
    void agreementLike_completed_runsEsignTableUpdateAfterComplete() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("webhookKind", "KFS");
        payload.put("applicationId", appId.toString());
        payload.put("transactionId", "TX-C");
        payload.put("status", "completed");

        when(flowService.completeESign(appId, "TX-C")).thenReturn(ApplicationResponse.builder().build());

        IntegrationCallbackProcessor p = new IntegrationCallbackProcessor(
                auditService, flowService, esignRequestTrackingService);
        p.handleEsignLikeCallback("EMSIGNER", payload);

        verify(esignRequestTrackingService).updateFromAgreementCallback(eq(appId), eq("TX-C"), eq("completed"), eq(payload));
    }

    @Test
    void agreementLike_failed_doesNotCompleteFlowButUpdatesEsignRequestWhenMappable() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("webhookKind", "KFS");
        payload.put("applicationId", appId.toString());
        payload.put("transactionId", "TX-F");
        payload.put("status", "failed");

        IntegrationCallbackProcessor p = new IntegrationCallbackProcessor(
                auditService, flowService, esignRequestTrackingService);
        p.handleEsignLikeCallback("EMSIGNER", payload);

        verify(flowService, never()).completeESign(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(esignRequestTrackingService).updateFromAgreementCallback(eq(appId), eq("TX-F"), eq("failed"), eq(payload));
    }

    @Test
    void agreementLike_completeError_skipsEsignRequestUpdate() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("webhookKind", "KFS");
        payload.put("applicationId", appId.toString());
        payload.put("transactionId", "TX-E");
        payload.put("status", "completed");
        when(flowService.completeESign(appId, "TX-E"))
                .thenThrow(new com.los.core.exception.BusinessRuleException("nope", "X", "Y", Map.of()));

        IntegrationCallbackProcessor p = new IntegrationCallbackProcessor(
                auditService, flowService, esignRequestTrackingService);
        p.handleEsignLikeCallback("EMSIGNER", payload);

        verify(esignRequestTrackingService, never())
                .updateFromAgreementCallback(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
