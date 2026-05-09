package com.los.iam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${los.jwt.secret}")
    private String jwtSecret;

    @Value("${los.jwt.access-token-expiry:900000}")
    private long accessTokenExpiry;

    @Value("${los.jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry;

    private SecretKey getSigningKey() {
        // Use first 32 bytes (256 bits) of the secret to force HS256.
        // Keys.hmacShaKeyFor() auto-selects HS512 for secrets > 512 bits,
        // which mismatches the gateway's HS256 verifier.
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        byte[] key256 = new byte[32];
        System.arraycopy(keyBytes, 0, key256, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key256, "HmacSHA256");
    }

    public String generateAccessToken(UUID userId, String username, String roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiry)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenExpiry)))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }
}
