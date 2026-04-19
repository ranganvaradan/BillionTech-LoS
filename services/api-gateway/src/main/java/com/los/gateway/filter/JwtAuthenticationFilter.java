package com.los.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${los.jwt.secret}")
    private String jwtSecret;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/enrollment/register",
            "/api/v1/enrollment/otp",
            "/api/v1/enrollment/consent",
            "/api/v1/vkyc/webhook/",
            "/api/v1/esign/webhook/",
            "/webhook/",
            "/actuator/",
            "/swagger-ui",
            "/v3/api-docs"
    );

    /** Cached signing key — created once from the secret, reused for every request. */
    private SecretKey signingKey;

    @jakarta.annotation.PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("JWT secret is NULL or blank — all authenticated requests will fail. "
                    + "Set JWT_SECRET environment variable or los.jwt.secret property.");
        } else {
            // Explicitly use HmacSHA256 to match IAM token generation (avoids HS512 auto-detection
            // from Keys.hmacShaKeyFor when secret is longer than 64 bytes)
            this.signingKey = new SecretKeySpec(
                    jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            log.info("JWT filter initialized — secret length: {} chars, algorithm: HS256", jwtSecret.length());
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Always allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String roles = claims.get("roles", String.class);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", roles != null ? roles : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("JWT validation failed [{}]: {}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
