package com.los.plp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class PlpRestClientConfig {

    @Bean
    @Qualifier("plpRestClient")
    RestClient plpRestClient(RestClient.Builder builder, PlpProperties plpProperties) {
        String base = plpProperties.getBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base == null || base.isBlank()) {
            base = "https://plp.billiontech.ai/plp-api";
        }
        log.info("PLP integration client base URL: {} (anchor sync POST {}/api/v1/integrations/los/anchors)",
                base, base);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, plpProperties.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(2000, plpProperties.getReadTimeoutMs()));
        return builder
                .baseUrl(base)
                .requestFactory(factory)
                .build();
    }
}
