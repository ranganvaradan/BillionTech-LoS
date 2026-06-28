package com.los.core.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * BR-19.6: OWASP Top 10 hardening filter.
 * Adds security headers, input validation, and request logging.
 */
@Slf4j
@Component
@Order(1)
public class OwaspSecurityFilter implements Filter {

    private static final Set<String> BLOCKED_PATTERNS = Set.of(
            "<script", "javascript:", "onerror=", "onload=",
            "eval(", "document.cookie", "window.location",
            "SELECT.*FROM", "INSERT.*INTO", "DELETE.*FROM", "DROP.*TABLE",
            "UNION.*SELECT", "'; --", "1=1", "OR 1=1"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // OWASP Security Headers
        httpResp.setHeader("X-Content-Type-Options", "nosniff");
        httpResp.setHeader("X-Frame-Options", "DENY");
        httpResp.setHeader("X-XSS-Protection", "1; mode=block");
        httpResp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        httpResp.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
        httpResp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResp.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        httpResp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        httpResp.setHeader("Pragma", "no-cache");

        // XSS/SQL injection check on query parameters
        String queryString = httpReq.getQueryString();
        if (queryString != null && containsMaliciousPattern(queryString)) {
            log.warn("Blocked potentially malicious request: {} from IP: {}",
                    httpReq.getRequestURI(), httpReq.getRemoteAddr());
            httpResp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request parameters");
            return;
        }

        // Request size limit (10MB)
        if (httpReq.getContentLengthLong() > 10 * 1024 * 1024) {
            log.warn("Request too large: {} bytes from IP: {}",
                    httpReq.getContentLengthLong(), httpReq.getRemoteAddr());
            httpResp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }

        // Structured request logging
        long startTime = System.currentTimeMillis();
        chain.doFilter(request, response);
        long duration = System.currentTimeMillis() - startTime;

        if (duration > 5000) {
            log.warn("Slow request: {} {} took {}ms from IP: {}",
                    httpReq.getMethod(), httpReq.getRequestURI(), duration, httpReq.getRemoteAddr());
        }
    }

    private boolean containsMaliciousPattern(String input) {
        String upper = input.toUpperCase();
        return BLOCKED_PATTERNS.stream().anyMatch(pattern -> upper.contains(pattern.toUpperCase()));
    }
}
