package com.los.core.service.esign;

import com.los.core.model.entity.schema.los2.EsignRequest;
import com.los.core.repository.schema.los2.EsignRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EsignRequestTrackingServiceTest {

    @Mock
    private EsignRequestRepository esignRequestRepository;

    @InjectMocks
    private EsignRequestTrackingService esignRequestTrackingService;

    @Test
    void recordInitiationSuccess_persistsPendingWithProviderAndUrls() {
        UUID app = UUID.randomUUID();
        esignRequestTrackingService.recordInitiationSuccess(
                app, "KFS_AGREEMENT", "EMSIGNER", "ES-TX-1", "https://sign.example/x",
                Map.of("name", "Borrower A", "aadhaarLast4", "1234"));
        verify(esignRequestRepository).save(argThat((EsignRequest e) ->
                app.equals(e.getApplicationId())
                        && "KFS_AGREEMENT".equals(e.getDocumentType())
                        && "EMSIGNER".equals(e.getProvider())
                        && "ES-TX-1".equals(e.getProviderRequestId())
                        && "https://sign.example/x".equals(e.getSigningUrl())
                        && EsignRequestStatuses.INITIATED.equals(e.getStatus())
                        && "Borrower A".equals(e.getSignerName())
                        && "1234".equals(e.getSignerAadhaarLast4())));
    }

    @Test
    void updateFromAgreementCallback_marksSignedWithPayload() {
        UUID app = UUID.randomUUID();
        EsignRequest row = EsignRequest.builder()
                .id(UUID.randomUUID())
                .applicationId(app)
                .documentType("KFS_AGREEMENT")
                .provider("EMSIGNER")
                .providerRequestId("ES-TX-1")
                .status(EsignRequestStatuses.INITIATED)
                .build();
        when(esignRequestRepository.findTopByApplicationIdAndProviderRequestIdOrderByCreatedAtDesc(app, "ES-TX-1"))
                .thenReturn(Optional.of(row));
        Map<String, Object> payload = Map.of("status", "completed", "signedDocumentUrl", "https://doc/1");
        esignRequestTrackingService.updateFromAgreementCallback(app, "ES-TX-1", "completed", payload);
        ArgumentCaptor<EsignRequest> cap = ArgumentCaptor.forClass(EsignRequest.class);
        verify(esignRequestRepository).save(cap.capture());
        assertEquals(EsignRequestStatuses.SIGNED, cap.getValue().getStatus());
        assertNotNull(cap.getValue().getSignedAt());
        assertEquals("https://doc/1", cap.getValue().getSignedDocumentUrl());
    }

    @Test
    void updateFromAgreementCallback_ignoresUnmappedStatus() {
        esignRequestTrackingService.updateFromAgreementCallback(UUID.randomUUID(), "ES-TX-1", "in_progress", Map.of());
        verify(esignRequestRepository, never()).findTopByApplicationIdAndProviderRequestIdOrderByCreatedAtDesc(any(), any());
        verify(esignRequestRepository, never()).save(any());
    }
}
