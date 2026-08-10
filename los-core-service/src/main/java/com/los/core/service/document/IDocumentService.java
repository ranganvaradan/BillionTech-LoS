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

    /**
     * Persist raw bytes as an application document (service-side uploads such as bureau reports / signed PDFs).
     */
    DocumentResponse storeDocumentBytes(
            UUID applicationId,
            String documentType,
            String fileName,
            String contentType,
            byte[] bytes);

    List<DocumentResponse> getDocuments(UUID applicationId);

    byte[] downloadDocument(UUID documentId);

    void deleteDocument(UUID documentId);

    boolean isDocumentChecklistComplete(UUID applicationId);
}
