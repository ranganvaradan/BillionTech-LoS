package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.karza.KarzaGstAdapter;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KarzaGstAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private KarzaGstAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KarzaGstAdapter(mapper);
    }

    @Test
    void loadsFixture_supportsAndExtracts() throws Exception {
        JsonNode payload = load("provider-fixtures/karza/gst/karza_gst_minimal.json");
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts()).isNotEmpty();
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
