package com.los.iam.controller;

import com.los.iam.service.SessionSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BR-14.5: Concurrent session control.
 * BR-14.7: API key management.
 * BR-19.9: IP whitelisting.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Session & Security", description = "Session limits, API keys, IP whitelisting")
public class SecurityController {

    private final SessionSecurityService securityService;

    // BR-14.5: Session management

    @PostMapping("/sessions/{userId}/enforce-limit")
    @Operation(summary = "BR-14.5: Enforce concurrent session limit")
    public ResponseEntity<Map<String, Object>> enforceSessionLimit(@PathVariable UUID userId) {
        return ResponseEntity.ok(securityService.enforceSessionLimit(userId));
    }

    @GetMapping("/sessions/{userId}")
    @Operation(summary = "BR-14.5: Get active sessions for a user")
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable UUID userId) {
        return ResponseEntity.ok(securityService.getSessionInfo(userId));
    }

    // BR-14.7: API key management

    @PostMapping("/api-keys")
    @Operation(summary = "BR-14.7: Create API key for a partner")
    public ResponseEntity<Map<String, Object>> createApiKey(@RequestBody Map<String, Object> body) {
        String partnerName = (String) body.get("partnerName");
        String description = (String) body.getOrDefault("description", "");
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) body.getOrDefault("scopes", List.of("READ"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(securityService.createApiKey(partnerName, description, scopes));
    }

    @GetMapping("/api-keys")
    @Operation(summary = "BR-14.7: List all API keys")
    public ResponseEntity<List<Map<String, Object>>> listApiKeys() {
        return ResponseEntity.ok(securityService.listApiKeys());
    }

    @DeleteMapping("/api-keys/{apiKey}")
    @Operation(summary = "BR-14.7: Revoke an API key")
    public ResponseEntity<Void> revokeApiKey(@PathVariable String apiKey) {
        securityService.revokeApiKey(apiKey);
        return ResponseEntity.noContent().build();
    }

    // BR-19.9: IP whitelisting

    @GetMapping("/ip-whitelist")
    @Operation(summary = "BR-19.9: Get whitelisted IPs")
    public ResponseEntity<Set<String>> getWhitelistedIps() {
        return ResponseEntity.ok(securityService.getWhitelistedIps());
    }

    @PostMapping("/ip-whitelist")
    @Operation(summary = "BR-19.9: Add IP to whitelist")
    public ResponseEntity<Map<String, Object>> addWhitelistedIp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(securityService.addWhitelistedIp(
                body.get("ip"), body.getOrDefault("description", "")));
    }

    @DeleteMapping("/ip-whitelist/{ip}")
    @Operation(summary = "BR-19.9: Remove IP from whitelist")
    public ResponseEntity<Map<String, Object>> removeWhitelistedIp(@PathVariable String ip) {
        return ResponseEntity.ok(securityService.removeWhitelistedIp(ip));
    }
}
