package com.los.core.creditintelligence.provider.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.los.core.creditintelligence.domain.SourceType;

public interface ProviderAdapter {

    String providerCode();

    SourceType sourceType();

    String schemaVersion();

    boolean supports(JsonNode payload);

    ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req);

    String parserVersion();

    String normalizerVersion();
}
