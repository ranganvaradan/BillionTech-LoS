package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassCibilAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SurePassCibilAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SurePassCibilAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SurePassCibilAdapter(mapper);
    }

    @Test
    void loadsFixture_supportsAndExtracts() throws Exception {
        JsonNode payload = load("provider-fixtures/surepass/cibil/cibil_minimal.json");
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts().isEmpty() && result.observations().isEmpty()).isFalse();
        assertThat(result.parserVersion()).isNotBlank();
    }

    private ExtractionRequest req() {
        return new ExtractionRequest(UUID.randomUUID(), UUID.randomUUID(), null, Map.of());
    }

    private JsonNode load(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return mapper.readTree(in);
        }
    }
}
