package com.los.core.creditintelligence.provider.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry of {@link ProviderAdapter}s. Adapters are only considered active when
 * {@code credit-intelligence.provider-spi.enabled=true}.
 */
@Component
@RequiredArgsConstructor
public class ProviderAdapterRegistry {

    private final List<ProviderAdapter> adapters;
    private final CreditIntelligenceProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return properties.getProviderSpi().isEnabled();
    }

    public List<ProviderAdapter> activeAdapters() {
        if (!isEnabled()) {
            return List.of();
        }
        return List.copyOf(adapters != null ? adapters : List.of());
    }

    public Optional<ProviderAdapter> findByProviderCode(String providerCode) {
        if (!isEnabled() || providerCode == null) {
            return Optional.empty();
        }
        return activeAdapters().stream()
                .filter(a -> providerCode.equalsIgnoreCase(a.providerCode()))
                .findFirst();
    }

    public Optional<ProviderAdapter> findBySourceType(SourceType sourceType) {
        if (!isEnabled() || sourceType == null) {
            return Optional.empty();
        }
        return activeAdapters().stream()
                .filter(a -> sourceType == a.sourceType())
                .findFirst();
    }

    public Optional<ProviderAdapter> findSupporting(JsonNode payload) {
        if (!isEnabled() || payload == null) {
            return Optional.empty();
        }
        List<ProviderAdapter> matches = new ArrayList<>();
        for (ProviderAdapter a : activeAdapters()) {
            if (a.supports(payload)) {
                matches.add(a);
            }
        }
        return matches.stream().findFirst();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
