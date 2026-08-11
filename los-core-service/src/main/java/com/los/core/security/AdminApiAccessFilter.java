package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOS-LIVE-CUSTOMER-HARDENING-1 — fail-closed role gate for {@code /api/v1/admin/**}.
 * Does not rely on UI AdminConfigGate. Spoofable header is interim until JWT/IAM;
 * blank/missing role is denied when enabled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminApiAccessFilter extends OncePerRequestFilter {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMINISTRATOR", "CREDIT_MANAGER", "ADMIN");

    private final ObjectMapper objectMapper;
    private final boolean requireRole;

    public AdminApiAccessFilter(
            ObjectMapper objectMapper,
            @Value("${los.security.admin-api-require-role:true}") boolean requireRole) {
        this.objectMapper = objectMapper;
        this.requireRole = requireRole;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!requireRole) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        // Strip context path if present
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return !path.startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String role = firstNonBlank(
                request.getHeader("X-User-Role"),
                firstCsvRole(request.getHeader("X-User-Roles")));
        if (role == null || !ADMIN_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Administrator or Credit Manager role required for admin APIs");
            body.put("reason", "ADMIN_ROLE_REQUIRED");
            body.put("action", "Sign in with ADMINISTRATOR or CREDIT_MANAGER and retry");
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String firstCsvRole(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        for (String part : csv.split(",")) {
            if (part != null && !part.isBlank()) {
                return part.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
