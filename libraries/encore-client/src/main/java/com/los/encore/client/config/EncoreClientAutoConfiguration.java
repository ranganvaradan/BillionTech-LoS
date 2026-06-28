package com.los.encore.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.encore.client.api.DefaultEncoreLmsApi;
import com.los.encore.client.api.EncoreLmsApi;
import com.los.encore.client.http.EncoreHttpTransport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(EncoreClientProperties.class)
public class EncoreClientAutoConfiguration {

    @Bean
    public EncoreHttpTransport encoreHttpTransport(EncoreClientProperties properties) {
        return new EncoreHttpTransport(properties);
    }

    @Bean
    public EncoreLmsApi encoreLmsApi(EncoreClientProperties properties,
                                     EncoreHttpTransport transport,
                                     ObjectMapper objectMapper) {
        return new DefaultEncoreLmsApi(properties, transport, objectMapper);
    }
}
