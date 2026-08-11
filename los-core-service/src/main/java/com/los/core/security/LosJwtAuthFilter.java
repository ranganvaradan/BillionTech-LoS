package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LOS-PRODUCTION-HARDENING-1 — Bearer JWT authentication for /api/**.
 * <p>
 * Production: header impersonation disabled — identity comes only from verified JWT.
 * Staging/local: {@code los.security.allow-header-impersonation=true} permits legacy X-User-* for tests.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LosJwtAuthFilter extends OncePerRequestFilter {

    private final LosJwtService jwtService;
    private final ObjectMapper objectMapper;
    private final SingleTenantDeploymentGuard tenancy;
    private final boolean jwtRequired;
    private final boolean allowHeaderImpersonation;

    public LosJwtAuthFilter(
            LosJwtService jwtService,
            ObjectMapper objectMapper,
            SingleTenantDeploymentGuard tenancy,
            @Value("${los.security.jwt.required:false}") boolean jwtRequired,
            @Value("${los.security.allow-header-impersonation:true}") boolean allowHeaderImpersonation) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.tenancy = tenancy;
        this.jwtRequired = jwtRequired;
        this.allowHeaderImpersonation = allowHeaderImpersonation;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = normalize(request);
        if (path.startsWith("/actuator/")) return true;
        if (path.startsWith("/api/v1/auth/")) return true;
        if (path.startsWith("/api/v1/internal/")
                || path.startsWith("/api/internal/")
                || path.startsWith("/internal/")) {
            return true; // CreditIntelligenceInternalTokenFilter
        }
        if (path.contains("/webhook") || path.contains("/callbacks/")) return true;
        if (path.startsWith("/api/v1/payu/") || path.startsWith("/api/v1/esign/")) return true;
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        boolean hasBearer = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7);

        if (hasBearer) {
            Map<String, String> overrides;
            try {
                Map<String, Object> claims = jwtService.verifyAndParse(auth);
                String uid = str(claims.get("uid"), str(claims.get("sub"), null));
                String role = str(claims.get("role"), "OPERATIONS").toUpperCase(Locale.ROOT);
                String name = str(claims.get("name"), null);
                String email = str(claims.get("email"), null);
                String tenantClaim = str(claims.get("tenantId"), null);
                tenancy.assertJwtTenantAllowed(tenantClaim);
                String tenantId = tenancy.deploymentTenantId().toString();

                overrides = new LinkedHashMap<>();
                overrides.put("X-User-Id", uid);
                overrides.put("X-User-Role", role);
                overrides.put("X-User-Roles", role);
                if (name != null) overrides.put("X-User-Name", name);
                if (email != null) overrides.put("X-User-Email", email);
                // Always stamp deployment tenant — never trust caller X-Tenant-Id when Bearer present
                overrides.put("X-Tenant-Id", tenantId);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(uid, null, authorities);
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ResponseStatusException e) {
                writeUnauthorized(response, e.getReason() == null ? "Forbidden" : e.getReason(), "TENANT_FORBIDDEN");
                return;
            } catch (IllegalArgumentException e) {
                log.warn("JWT rejected: {}", e.getMessage());
                writeUnauthorized(response, "Invalid or expired access token", "JWT_INVALID");
                return;
            } catch (Exception e) {
                log.warn("JWT processing failed: {}", e.toString());
                writeUnauthorized(response, "Invalid or expired access token", "JWT_INVALID");
                return;
            }
            // Must not wrap chain in JWT catch — PathPattern/security errors were mislabeled JWT_INVALID
            filterChain.doFilter(new LosAuthenticatedRequest(request, overrides), response);
            return;
        }

        if (jwtRequired && !allowHeaderImpersonation) {
            writeUnauthorized(response, "Bearer access token required", "JWT_REQUIRED");
            return;
        }

        // Staging/interim: allow legacy header impersonation only when explicitly enabled
        if (!allowHeaderImpersonation
                && (hasText(request.getHeader("X-User-Role")) || hasText(request.getHeader("X-User-Id")))) {
            writeUnauthorized(response, "Header impersonation disabled — use Bearer JWT", "HEADER_IMPERSONATION_DISABLED");
            return;
        }

        // Single-tenant fail-closed: reject foreign X-Tenant-Id even on interim header paths
        if (tenancy.isSingleTenantFailClosed() && hasText(request.getHeader("X-Tenant-Id"))) {
            try {
                tenancy.bindOrRejectHeader(request.getHeader("X-Tenant-Id"));
            } catch (org.springframework.web.server.ResponseStatusException ex) {
                response.setStatus(ex.getStatusCode().value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("message", ex.getReason());
                body.put("reason", "TENANT_FORBIDDEN");
                objectMapper.writeValue(response.getOutputStream(), body);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("reason", reason);
        body.put("action", "Sign in and send Authorization: Bearer <token>");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String normalize(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && path != null && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path == null ? "" : path;
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    private static String str(Object v, String dflt) {
        if (v == null) return dflt;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? dflt : s;
    }
}
