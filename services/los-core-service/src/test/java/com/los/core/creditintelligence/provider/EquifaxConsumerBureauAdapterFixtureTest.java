package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.equifax.EquifaxConsumerBureauAdapter;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquifaxConsumerBureauAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private EquifaxConsumerBureauAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EquifaxConsumerBureauAdapter(mapper);
    }

    @Test
    void loadsXmlFixture_supportsAndExtracts() throws Exception {
        String xml;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("provider-fixtures/equifax/equifax_accounts_minimal.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonNode payload = mapper.valueToTree(Map.of("xml", xml));
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.parserVersion()).isNotBlank();
        assertThat(result.facts().stream().anyMatch(f -> "TRADELINE".equals(f.get("factType"))
                || "BUREAU_REPORT".equals(f.get("factType")))).isTrue();
    }

    private ExtractionRequest req() {
        return new ExtractionRequest(UUID.randomUUID(), UUID.randomUUID(), null, Map.of());
    }
}
