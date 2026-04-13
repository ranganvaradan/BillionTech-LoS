package com.los.core.service.integration.providers;

import java.util.Map;
import java.util.UUID;

public interface IESignProvider {

    ESignInitResult initiateSigning(UUID applicationId, String documentStorageKey, Map<String, Object> signerInfo);

    ESignStatusResult checkStatus(String eSignTransactionId);

    byte[] downloadSignedDocument(String eSignTransactionId);

    record ESignInitResult(boolean success, String transactionId, String signingUrl, String errorMessage) {}

    record ESignStatusResult(String status, String transactionId, Map<String, Object> signerDetails, String errorMessage) {}
}
