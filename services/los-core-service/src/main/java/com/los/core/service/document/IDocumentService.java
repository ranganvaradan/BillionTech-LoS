package com.los.core.service.document;

import com.los.core.model.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IDocumentService {

    DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file);

    List<DocumentResponse> getDocuments(UUID applicationId);

    byte[] downloadDocument(UUID documentId);

    void deleteDocument(UUID documentId);

    boolean isDocumentChecklistComplete(UUID applicationId);
}
