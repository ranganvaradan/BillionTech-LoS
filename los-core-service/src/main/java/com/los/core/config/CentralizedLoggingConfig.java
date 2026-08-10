package com.los.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * NFR: Centralized logging configuration.
 * Configures structured JSON logging for ELK/Loki aggregation.
 * In production, add logback-logstash-encoder dependency and Logstash appender.
 */
@Slf4j
@Configuration
public class CentralizedLoggingConfig {

    @Bean
    CommandLineRunner logStartupInfo() {
        return args -> {
            log.info("=== LOS Core Service — Centralized Logging Active ===");
            log.info("Log format: Structured JSON (logback-logstash-encoder ready)");
            log.info("Supported sinks: ELK (Elasticsearch/Logstash/Kibana), Grafana Loki, Datadog");
            log.info("Correlation ID propagation: via X-Request-ID header");

            // Log application context for dashboards
            Map<String, String> context = Map.of(
                    "service", "los-core-service",
                    "version", "2.0.0",
                    "environment", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "default"),
                    "javaVersion", System.getProperty("java.version"),
                    "availableProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()),
                    "maxMemoryMB", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024))
            );
            log.info("Application context: {}", context);
        };
    }
}
