package com.los.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration for the API Gateway.
 * Uses Redis-backed token bucket algorithm via Spring Cloud Gateway.
 * Supports per-IP and per-API-key rate limiting (BR-18.3).
 */
@Configuration
public class RateLimitConfig {

    /**
     * Rate limit by API key header (X-Api-Key) if present, otherwise by client IP.
     * Partners with API keys get their own rate limit bucket.
     */
    @Bean
    public KeyResolver apiKeyOrIpResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
            if (apiKey != null && !apiKey.isBlank()) {
                return Mono.just("apikey:" + apiKey);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
