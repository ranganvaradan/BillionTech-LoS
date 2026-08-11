package com.los.core.controller;

import com.los.core.security.DataRetentionService;
import com.los.core.security.EncryptionService;
import com.los.core.security.PiiMaskingService;
import com.los.core.security.StaffAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security & Data Protection", description = "Encryption, PII masking, and data retention management")
public class SecurityController {

    private final EncryptionService encryptionService;
    private final PiiMaskingService piiMaskingService;
    private final DataRetentionService dataRetentionService;
    private final StaffAccessGuard staffAccessGuard;

    @PostMapping("/encrypt")
    @Operation(summary = "Encrypt a value using AES-256-GCM")
    public ResponseEntity<Map<String, String>> encrypt(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.requireAdmin(userId, userRole);
        String encrypted = encryptionService.encrypt(request.get("value"));
        return ResponseEntity.ok(Map.of("encrypted", encrypted));
    }

    @PostMapping("/decrypt")
    @Operation(summary = "Decrypt an AES-256-GCM encrypted value")
    public ResponseEntity<Map<String, String>> decrypt(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.requireAdmin(userId, userRole);
        String decrypted = encryptionService.decrypt(request.get("value"));
        return ResponseEntity.ok(Map.of("decrypted", decrypted));
    }

    @PostMapping("/mask")
    @Operation(summary = "Mask PII fields in a data map")
    public ResponseEntity<Map<String, Object>> maskPii(
            @RequestBody Map<String, Object> data,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.requireAdmin(userId, userRole);
        return ResponseEntity.ok(piiMaskingService.maskSensitiveFields(data));
    }

    @GetMapping("/retention-policy")
    @Operation(summary = "Get data retention policy")
    public ResponseEntity<Map<String, Object>> retentionPolicy(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.requireAdmin(userId, userRole);
        return ResponseEntity.ok(dataRetentionService.getRetentionPolicy());
    }

    @PostMapping("/erasure-request/{customerId}")
    @Operation(summary = "Process right-to-erasure request (DPDP Act)")
    public ResponseEntity<Map<String, Object>> erasureRequest(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        staffAccessGuard.requireAdmin(userId, userRole);
        return ResponseEntity.ok(dataRetentionService.processErasureRequest(customerId));
    }
}
