package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * LOS-PRODUCTION-HARDENING-1 — compact HS256 JWT for staff/borrower API identity.
 * No new IAM engine; production derives actor from Bearer token, not spoofable headers.
 */
@Service
public class LosJwtService {

    private final ObjectMapper objectMapper;
    private final byte[] hmacKey;
    private final boolean configured;
    private final long ttlSeconds;

    public LosJwtService(
            ObjectMapper objectMapper,
            @Value("${los.security.jwt.hmac-secret:}") String secret,
            @Value("${los.security.jwt.ttl-seconds:28800}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        String s = secret == null ? "" : secret.trim();
        this.configured = s.length() >= 32;
        this.hmacKey = configured ? s.getBytes(StandardCharsets.UTF_8) : new byte[0];
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 28800;
    }

    public boolean isConfigured() {
        return configured;
    }

    public String issueAccessToken(UUID userId, String role, String email, String name, UUID tenantId) {
        if (!configured) {
            throw new IllegalStateException("LOS JWT hmac-secret is not configured");
        }
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Instant now = Instant.now();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", userId.toString());
            claims.put("uid", userId.toString());
            claims.put("role", role == null ? "OPERATIONS" : role.trim().toUpperCase(Locale.ROOT));
            claims.put("roles", List.of(claims.get("role")));
            if (email != null) claims.put("email", email);
            if (name != null) claims.put("name", name);
            claims.put("tenantId", tenantId == null
                    ? "00000000-0000-0000-0000-000000000001"
                    : tenantId.toString());
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());
            claims.put("iss", "los-core");
            String h = b64(objectMapper.writeValueAsBytes(header));
            String p = b64(objectMapper.writeValueAsBytes(claims));
            String sig = b64(hmac((h + "." + p).getBytes(StandardCharsets.UTF_8)));
            return h + "." + p + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to issue LOS JWT: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> verifyAndParse(String token) {
        if (!configured) {
            throw new IllegalStateException("LOS JWT hmac-secret is not configured");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        String raw = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        String[] parts = raw.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = b64(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        try {
            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Object exp = claims.get("exp");
            if (exp instanceof Number n && Instant.now().getEpochSecond() > n.longValue()) {
                throw new IllegalArgumentException("JWT expired");
            }
            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT payload");
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
