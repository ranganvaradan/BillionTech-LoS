package com.billiontech.bankstatement.controller;

import com.billiontech.bankstatement.model.dto.request.CreateApiKeyRequest;
import com.billiontech.bankstatement.model.dto.response.ApiKeyResponse;
import com.billiontech.bankstatement.model.entity.ApiKey;
import com.billiontech.bankstatement.repository.ApiKeyRepository;
import com.billiontech.bankstatement.security.ApiKeyAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bank-statements/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Manage API keys for standalone authentication")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    @PostMapping
    @Operation(summary = "Generate a new API key")
    public ResponseEntity<ApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        String rawKey = "bsa_" + UUID.randomUUID().toString().replace("-", "");
        String hash = ApiKeyAuthFilter.hashKey(rawKey);
        String prefix = rawKey.substring(0, 8);

        LocalDateTime expiresAt = request.getExpiresInDays() != null
                ? LocalDateTime.now().plusDays(request.getExpiresInDays())
                : null;

        ApiKey apiKey = ApiKey.builder()
                .keyHash(hash)
                .keyPrefix(prefix)
                .name(request.getName())
                .description(request.getDescription())
                .expiresAt(expiresAt)
                .build();

        apiKey = apiKeyRepository.save(apiKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiKeyResponse.builder()
                .id(apiKey.getId())
                .apiKey(rawKey)
                .keyPrefix(prefix)
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .expiresAt(expiresAt)
                .createdAt(apiKey.getCreatedAt())
                .build());
    }
}
