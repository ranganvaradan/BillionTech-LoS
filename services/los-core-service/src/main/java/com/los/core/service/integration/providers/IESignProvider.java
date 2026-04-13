package com.los.core.service.integration.providers;

import java.util.Map;

public interface IESignProvider {

    ESignInitResult initiateSigning(String documentType, byte[] documentBytes, Map<String, Object> signerInfo);

    ESignStatusResult checkStatus(String signingRequestId);

    byte[] downloadSignedDocument(String signingRequestId);

    String getProviderName();

    record ESignInitResult(
            String signingRequestId,
            String signingUrl,
            String status,
            String errorMessage
    ) {}

    record ESignStatusResult(
            String signingRequestId,
            String status,
            String signedDocumentUrl,
            String errorMessage
    ) {}
}
