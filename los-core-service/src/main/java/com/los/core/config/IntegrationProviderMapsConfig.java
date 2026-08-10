package com.los.core.config;

import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.integration.providers.IKycProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Binds all Spring beans implementing provider interfaces to stable lookup keys
 * ({@code Provider#getProviderName()}) for use by {@code IntegrationRouterServiceImpl}.
 */
@Configuration
public class IntegrationProviderMapsConfig {

    @Bean(name = "kycProvidersByName")
    public Map<String, IKycProvider> kycProvidersByName(List<IKycProvider> providers) {
        return providers.stream()
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getProviderName()),
                        Function.identity(),
                        (a, b) -> a));
    }

    @Bean(name = "bureauProvidersByName")
    public Map<String, IBureauProvider> bureauProvidersByName(List<IBureauProvider> providers) {
        return providers.stream()
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getProviderName()),
                        Function.identity(),
                        (a, b) -> a));
    }

    @Bean(name = "eSignProvidersByName")
    public Map<String, IESignProvider> eSignProvidersByName(List<IESignProvider> providers) {
        return providers.stream()
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getProviderName()),
                        Function.identity(),
                        (a, b) -> a));
    }

    private static String normalizeKey(String name) {
        return name == null ? "UNKNOWN" : name.trim().toUpperCase();
    }
}
