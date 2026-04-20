package com.los.iam.service;

import com.los.iam.entity.RefreshToken;
import com.los.iam.entity.User;
import com.los.iam.repository.RefreshTokenRepository;
import com.los.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BR-14.5: Concurrent login control / session limits.
 * BR-14.7: API key management for partners.
 * BR-19.9: IP whitelisting for admin APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSecurityService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private static final int MAX_CONCURRENT_SESSIONS = 3;

    // In-memory stores (would use Redis/DB in production)
    private final Map<String, Map<String, Object>> apiKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> whitelistedIps = java.util.Collections.synchronizedSet(new LinkedHashSet<>(
            List.of("127.0.0.1", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
    ));

    // BR-14.5: Concurrent session control

    /**
     * Enforce max concurrent sessions — revoke oldest sessions if limit exceeded.
     */
    @Transactional
    public Map<String, Object> enforceSessionLimit(UUID userId) {
        List<RefreshToken> activeSessions = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);

        if (activeSessions.size() <= MAX_CONCURRENT_SESSIONS) {
            return Map.of(
                    "userId", userId.toString(),
                    "activeSessions", activeSessions.size(),
                    "maxAllowed", MAX_CONCURRENT_SESSIONS,
                    "action", "NONE"
            );
        }

        // Sort by creation time — revoke oldest sessions beyond limit
        activeSessions.sort(Comparator.comparing(RefreshToken::getCreatedAt));
        int toRevoke = activeSessions.size() - MAX_CONCURRENT_SESSIONS;
        List<String> revokedTokens = new ArrayList<>();

        for (int i = 0; i < toRevoke; i++) {
            RefreshToken token = activeSessions.get(i);
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            revokedTokens.add(token.getToken().substring(0, 8) + "...");
        }

        log.info("Session limit enforced for user {}: revoked {} oldest sessions", userId, toRevoke);

        return Map.of(
                "userId", userId.toString(),
                "activeSessions", MAX_CONCURRENT_SESSIONS,
                "maxAllowed", MAX_CONCURRENT_SESSIONS,
                "revokedSessions", toRevoke,
                "action", "REVOKED_OLDEST"
        );
    }

    /**
     * Get active session count for a user.
     */
    public Map<String, Object> getSessionInfo(UUID userId) {
        List<RefreshToken> activeSessions = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        return Map.of(
                "userId", userId.toString(),
                "activeSessions", activeSessions.size(),
                "maxAllowed", MAX_CONCURRENT_SESSIONS,
                "sessions", activeSessions.stream().map(s -> Map.of(
                        "createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "N/A",
                        "ipAddress", s.getIpAddress() != null ? s.getIpAddress() : "N/A",
                        "userAgent", s.getUserAgent() != null ? s.getUserAgent() : "N/A"
                )).collect(Collectors.toList())
        );
    }

    // BR-14.7: API key management

    public Map<String, Object> createApiKey(String partnerName, String description, List<String> scopes) {
        String apiKey = "los-api-" + UUID.randomUUID().toString().replace("-", "");
        String apiSecret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Map<String, Object> keyData = new LinkedHashMap<>();
        keyData.put("partnerName", partnerName);
        keyData.put("description", description);
        keyData.put("scopes", scopes);
        keyData.put("active", true);
        keyData.put("createdAt", Instant.now().toString());
        keyData.put("lastUsedAt", null);
        keyData.put("requestCount", 0L);

        apiKeys.put(apiKey, keyData);

        log.info("API key created for partner: {} with scopes: {}", partnerName, scopes);

        return Map.of(
                "apiKey", apiKey,
                "apiSecret", apiSecret,
                "partnerName", partnerName,
                "scopes", scopes,
                "message", "Save the API secret — it won't be shown again"
        );
    }

    public List<Map<String, Object>> listApiKeys() {
        List<Map<String, Object>> result = new ArrayList<>();
        apiKeys.forEach((key, data) -> {
            Map<String, Object> entry = new LinkedHashMap<>(data);
            entry.put("apiKey", key.substring(0, 12) + "...");
            result.add(entry);
        });
        return result;
    }

    public void revokeApiKey(String apiKey) {
        Map<String, Object> keyData = apiKeys.get(apiKey);
        if (keyData != null) {
            keyData.put("active", false);
            log.info("API key revoked: {}...", apiKey.substring(0, 12));
        }
    }

    // BR-19.9: IP whitelisting

    public Set<String> getWhitelistedIps() {
        return new LinkedHashSet<>(whitelistedIps);
    }

    public Map<String, Object> addWhitelistedIp(String ip, String description) {
        whitelistedIps.add(ip);
        log.info("IP whitelisted: {} ({})", ip, description);
        return Map.of("ip", ip, "description", description, "action", "ADDED",
                      "totalWhitelisted", whitelistedIps.size());
    }

    public Map<String, Object> removeWhitelistedIp(String ip) {
        boolean removed = whitelistedIps.remove(ip);
        return Map.of("ip", ip, "removed", removed, "totalWhitelisted", whitelistedIps.size());
    }

    public boolean isIpWhitelisted(String ip) {
        if (whitelistedIps.contains(ip)) return true;
        // Check CIDR ranges
        return whitelistedIps.stream().anyMatch(cidr -> {
            if (!cidr.contains("/")) return false;
            try {
                String[] parts = cidr.split("/");
                int prefixLength = Integer.parseInt(parts[1]);
                int mask = prefixLength == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLength);
                int cidrAddr = ipToInt(parts[0]);
                int ipAddr = ipToInt(ip);
                return (cidrAddr & mask) == (ipAddr & mask);
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static int ipToInt(String ip) {
        String[] octets = ip.split("\\.");
        return (Integer.parseInt(octets[0]) << 24)
             | (Integer.parseInt(octets[1]) << 16)
             | (Integer.parseInt(octets[2]) << 8)
             |  Integer.parseInt(octets[3]);
    }
}
