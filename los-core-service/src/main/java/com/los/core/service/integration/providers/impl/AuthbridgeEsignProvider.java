package com.los.core.service.integration.providers.impl;

import com.los.core.service.integration.providers.IESignProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AuthBridge eSign — matrix fallback after {@link EmsignerESignProvider}. Delegates to the Emsigner
 * client so environments without AuthBridge eSign API still complete demo flows; provider name
 * remains distinct for audit / routing.
 */
@Component("authbridgeEsignProvider")
@RequiredArgsConstructor
public class AuthbridgeEsignProvider implements IESignProvider {

    private final EmsignerESignProvider emsigner;

    @Override
    public String getProviderName() {
        return "AUTHBRIDGE_ESIGN";
    }

    @Override
    public ESignInitResult initiateSigningRequest(ESignInitRequest request) {
        return emsigner.initiateSigningRequest(request);
    }

    @Override
    public String getSigningUrl(String signingRequestId, String returnUrl) {
        return emsigner.getSigningUrl(signingRequestId, returnUrl);
    }

    @Override
    public ESignStatusResult getSigningStatus(String signingRequestId) {
        return emsigner.getSigningStatus(signingRequestId);
    }

    @Override
    public byte[] downloadSignedDocument(String signingRequestId) {
        return emsigner.downloadSignedDocument(signingRequestId);
    }
}
