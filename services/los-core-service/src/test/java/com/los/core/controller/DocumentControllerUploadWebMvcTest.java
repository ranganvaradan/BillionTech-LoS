package com.los.core.controller;

import com.los.core.config.WebSecurityConfig;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.repository.DocumentRepository;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.document.storage.DocumentBlobStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentController.class)
@Import(WebSecurityConfig.class)
class DocumentControllerUploadWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IDocumentService documentService;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentBlobStore documentBlobStore;

    @Test
    void upload_acceptsFilePartAndDocumentTypeQuery() throws Exception {
        UUID app = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 test".getBytes());
        when(documentService.uploadDocument(eq(app), eq("SUPPORTING"), any(), isNull()))
                .thenReturn(DocumentResponse.builder()
                        .id(UUID.fromString("7ba7b810-9dad-11d1-80b4-00c04fd430c8"))
                        .applicationId(app)
                        .documentType("SUPPORTING")
                        .fileName("test.pdf")
                        .fileSize(5)
                        .build());

        mockMvc.perform(multipart("/api/v1/documents/{applicationId}/upload", app)
                        .file(file)
                        .param("documentType", "SUPPORTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("SUPPORTING"));
    }
}
