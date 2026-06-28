package com.los.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NotificationRestClientConfig {

    @Bean
    @Qualifier("notificationRestClient")
    RestClient notificationRestClient(RestClient.Builder builder, IntegrationProperties integrationProperties) {
        String base = integrationProperties.getNotification().getBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base == null || base.isBlank()) {
            base = "http://localhost:8084";
        }
        return builder.baseUrl(base).build();
    }
}
