package com.billiontech.bankstatement.security;

import com.billiontech.bankstatement.repository.ApiKeyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter implements Filter {

    private final ApiKeyRepository apiKeyRepository;

    @Value("${bankstatement.security.api-key-header:X-API-Key}")
    private String apiKeyHeader;

    @Value("${bankstatement.security.api-keys:}")
    private String configuredApiKeys;

    @Value("${bankstatement.security.trust-gateway-headers:false}")
    private boolean trustGatewayHeaders;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/swagger-ui", "/v3/api-docs", "/actuator", "/api/v1/bank-statements/supported-banks"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (isPublicPath(path) || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Check X-User-Id header (from API Gateway — LoS mode)
        // Only trusted when explicitly enabled (i.e., running behind the gateway)
        if (trustGatewayHeaders) {
            String userId = httpRequest.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Check API key (standalone mode)
        String apiKey = httpRequest.getHeader(apiKeyHeader);
        if (apiKey == null || apiKey.isBlank()) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
            return;
        }

        if (isValidApiKey(apiKey)) {
            chain.doFilter(request, response);
        } else {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isValidApiKey(String apiKey) {
        // Check configured static keys first (for dev/simple deployments)
        if (configuredApiKeys != null && !configuredApiKeys.isBlank()) {
            List<String> keys = Arrays.stream(configuredApiKeys.split(",")).map(String::trim).toList();
            if (keys.contains(apiKey.trim())) {
                return true;
            }
        }
        // Check database-stored keys (also verify expiration)
        String hash = hashKey(apiKey);
        return apiKeyRepository.findByKeyHashAndIsActiveTrue(hash)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    public static String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
