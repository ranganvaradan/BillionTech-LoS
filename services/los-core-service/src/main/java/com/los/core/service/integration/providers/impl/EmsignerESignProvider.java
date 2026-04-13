package com.los.core.service.integration.providers.impl;

import com.los.core.service.integration.providers.IESignProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("emsignerESignProvider")
public class EmsignerESignProvider implements IESignProvider {

    @Override
    public ESignInitResult initiateSigning(UUID applicationId, String documentStorageKey, Map<String, Object> signerInfo) {
        log.info("[emsigner] Initiating eSign for application: {}", applicationId);

        String eSignTransactionId = "ESIGN-" + UUID.randomUUID().toString().substring(0, 8);
        String signingUrl = "https://esign.example.com/sign/" + eSignTransactionId;

        return new ESignInitResult(true, eSignTransactionId, signingUrl, null);
    }

    @Override
    public ESignStatusResult checkStatus(String eSignTransactionId) {
        log.info("[emsigner] Checking eSign status for: {}", eSignTransactionId);

        // Simulated status check
        return new ESignStatusResult("COMPLETED", eSignTransactionId,
                Map.of(
                        "signerName", "John Doe",
                        "signedAt", "2026-04-13T10:30:00Z",
                        "certificateSerial", "CERT-" + UUID.randomUUID().toString().substring(0, 8),
                        "aadhaarLinked", true
                ), null);
    }

    @Override
    public byte[] downloadSignedDocument(String eSignTransactionId) {
        log.info("[emsigner] Downloading signed document for: {}", eSignTransactionId);
        // In production, this would download the actual signed PDF from emsigner
        return ("Signed document placeholder for " + eSignTransactionId).getBytes();
    }
}
