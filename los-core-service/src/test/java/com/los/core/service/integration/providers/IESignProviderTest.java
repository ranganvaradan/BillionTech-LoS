package com.los.core.service.integration.providers;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies v2.0–aligned IESignProvider contract: default delegation and status alias.
 */
class IESignProviderTest {

    @Test
    void initiateSigningDelegatesToInitiateSigningRequest() {
        AtomicReference<IESignProvider.ESignInitRequest> captured = new AtomicReference<>();
        IESignProvider p = new IESignProvider() {
            @Override
            public String getProviderName() {
                return "TEST";
            }

            @Override
            public ESignInitResult initiateSigningRequest(ESignInitRequest request) {
                captured.set(request);
                return new ESignInitResult(true, "T1", "https://sign.example", null);
            }

            @Override
            public String getSigningUrl(String signingRequestId, String returnUrl) {
                return "u";
            }

            @Override
            public ESignStatusResult getSigningStatus(String signingRequestId) {
                return new ESignStatusResult("PENDING", signingRequestId, Map.of(), null);
            }

            @Override
            public byte[] downloadSignedDocument(String signingRequestId) {
                return new byte[0];
            }
        };
        UUID app = UUID.randomUUID();
        IESignProvider.ESignInitResult r = p.initiateSigning(app, "KFS", Map.of("k", "v"));
        assertTrue(r.success());
        assertNotNull(captured.get());
        assertEquals(app, captured.get().applicationId());
        assertEquals("KFS", captured.get().documentStorageKey());
        assertEquals("v", captured.get().signerInfo().get("k"));
    }

    @Test
    void checkStatusDelegatesToGetSigningStatus() {
        IESignProvider p = new IESignProvider() {
            @Override
            public String getProviderName() {
                return "T2";
            }

            @Override
            public ESignInitResult initiateSigningRequest(ESignInitRequest request) {
                return new ESignInitResult(false, null, null, null);
            }

            @Override
            public String getSigningUrl(String signingRequestId, String returnUrl) {
                return "";
            }

            @Override
            public ESignStatusResult getSigningStatus(String signingRequestId) {
                return new ESignStatusResult("SIGNED", signingRequestId, Map.of("ok", true), null);
            }

            @Override
            public byte[] downloadSignedDocument(String signingRequestId) {
                return new byte[0];
            }
        };
        IESignProvider.ESignStatusResult viaDeprecated = p.checkStatus("X1");
        assertEquals("SIGNED", viaDeprecated.status());
        assertEquals("X1", viaDeprecated.transactionId());
    }
}
