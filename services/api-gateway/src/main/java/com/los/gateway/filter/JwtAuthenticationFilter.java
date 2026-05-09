package com.los.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    void init() {
        log.info("JWT filter initialized — pure JCA, secret length: {}", jwtSecret != null ? jwtSecret.length() : 0);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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

        String token = authHeader.substring(7).trim();

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Verify signature
            String headerPayload = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            byte[] key256 = new byte[32];
            System.arraycopy(keyBytes, 0, key256, 0, Math.min(keyBytes.length, 32));
            mac.init(new SecretKeySpec(key256, "HmacSHA256"));
            byte[] expectedSig = mac.doFinal(headerPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSig);

            if (!expectedSigB64.equals(parts[2])) {
                log.warn("JWT rejected: signature mismatch");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Decode payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);

            // Check expiry
            Object expObj = claims.get("exp");
            if (expObj != null) {
                long exp = ((Number) expObj).longValue();
                if (System.currentTimeMillis() / 1000 > exp) {
                    log.warn("JWT rejected: expired");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }

            String userId = claims.get("sub") != null ? claims.get("sub").toString() : "";
            String roles = claims.get("roles") != null ? claims.get("roles").toString() : "";
            log.info("JWT accepted: userId={}", userId);

            // Forward user identity as HTTP headers to downstream services.
            // ServerHttpRequest.mutate() throws UnsupportedOperationException in
            // Spring Cloud Gateway's reactive Netty context. Use a decorator instead
            // to overlay extra headers without mutating the original request object.
            ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(super.getHeaders());
                    headers.set("X-User-Id", userId);
                    headers.set("X-User-Roles", roles);
                    return headers;
                }
            };

            return chain.filter(exchange.mutate().request(decorated).build());

        } catch (Exception e) {
            log.warn("JWT validation failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private String padBase64(String s) {
        switch (s.length() % 4) {
            case 2: return s + "==";
            case 3: return s + "=";
            default: return s;
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
