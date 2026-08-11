package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-closed gate for CI/internal endpoints (token required in production).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class CreditIntelligenceInternalTokenFilter extends OncePerRequestFilter {

    private final CreditIntelligenceInternalTokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = normalize(request);
        return !(path.startsWith("/api/v1/internal/")
                || path.startsWith("/api/internal/")
                || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            tokenService.assertToken(request.getHeader("X-Internal-Token"));
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", ex.getReason() == null ? "Unauthorized" : ex.getReason());
            body.put("reason", "INTERNAL_TOKEN_REQUIRED");
            objectMapper.writeValue(response.getOutputStream(), body);
        }
    }

    private static String normalize(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "";
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path;
    }
}
