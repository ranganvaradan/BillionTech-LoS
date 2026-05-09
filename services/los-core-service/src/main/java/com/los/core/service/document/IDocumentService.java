package com.los.core.service.document;

import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.enums.KycStepType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IDocumentService {

    default DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file) {
        return uploadDocument(applicationId, documentType, file, null);
    }

    DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file, KycStepType kycStepType);

    List<DocumentResponse> getDocuments(UUID applicationId);

    byte[] downloadDocument(UUID documentId);

    void deleteDocument(UUID documentId);

    boolean isDocumentChecklistComplete(UUID applicationId);
}
