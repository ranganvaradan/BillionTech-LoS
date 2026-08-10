package com.los.core.service.document;

import com.los.core.exception.BusinessRuleException;
import com.los.core.repository.DocumentRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.document.storage.DocumentBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.AccessDeniedException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplUploadTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentBlobStore documentBlobStore;
    @Mock AuditService auditService;

    DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(documentRepository, documentBlobStore, auditService);
    }

    @Test
    void panPdfUploadPersists() throws Exception {
        UUID appId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = service.uploadDocument(appId, "PAN_CARD", file, null);

        assertThat(res.getDocumentType()).isEqualTo("PAN_CARD");
        assertThat(res.getFileName()).isEqualTo("pan.pdf");
        verify(documentBlobStore).putObject(anyString(), any(byte[].class), anyLong(), anyString());
    }

    @Test
    void panJpegUploadPersists() throws Exception {
        UUID appId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = service.uploadDocument(appId, "PAN_CARD", file, null);
        assertThat(res.getDocumentType()).isEqualTo("PAN_CARD");
    }

    @Test
    void panUnsupportedTypeRejected() {
        UUID appId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.exe", "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.uploadDocument(appId, "PAN_CARD", file, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void storageAccessDeniedMapsToBusinessMessage() throws Exception {
        UUID appId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "pan.pdf", "application/pdf", "%PDF".getBytes());
        doThrow(new AccessDeniedException("/app/uploads"))
                .when(documentBlobStore)
                .putObject(anyString(), any(byte[].class), anyLong(), anyString());

        assertThatThrownBy(() -> service.uploadDocument(appId, "PAN_CARD", file, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Document storage unavailable");
    }
}
