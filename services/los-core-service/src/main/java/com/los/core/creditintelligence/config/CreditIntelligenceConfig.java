package com.los.core.creditintelligence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(CreditIntelligenceProperties.class)
public class CreditIntelligenceConfig {

    @Bean
    public ContentHasher contentHasher(ObjectMapper objectMapper) {
        return new ContentHasher(objectMapper);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService creditIntelligenceShadowExecutor() {
        AtomicInteger idx = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "ci-shadow-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(2, factory);
    }
}
