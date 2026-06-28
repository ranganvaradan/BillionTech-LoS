package com.los.lms.config;

import com.los.lms.dto.RepaymentCallbackRequest;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Optional HMAC verification for LMS repayment callbacks.
 * When {@code hmacSecret} is blank, signatures are not required (backward compatible).
 */
@Data
@Component
@ConfigurationProperties(prefix = "los.lms.callback")
public class LmsCallbackSecurityProperties {

    /**
     * Shared secret; when set, {@code X-LMS-Signature} header must be hex(SHA-256(secret + "|" + canonical)).
     */
    private String hmacSecret = "";

    public boolean verifySignature(RepaymentCallbackRequest callback, String signatureHeader) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String canonical = buildCanonical(callback);
        String expectedHex = sha256Hex(hmacSecret + "|" + canonical);
        return constantTimeEquals(expectedHex, signatureHeader.trim());
    }

    private static String buildCanonical(RepaymentCallbackRequest r) {
        String utr = r.getUtrNumber() != null ? r.getUtrNumber() : "";
        String pd = r.getPaymentDate() != null ? r.getPaymentDate().toString() : "";
        return r.getApplicationNumber()
                + "|" + r.getInstallmentNumber()
                + "|" + r.getPaidAmount().toPlainString()
                + "|" + utr
                + "|" + pd;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aa = a.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (aa.length != bb.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < aa.length; i++) {
            diff |= aa[i] ^ bb[i];
        }
        return diff == 0;
    }
}
