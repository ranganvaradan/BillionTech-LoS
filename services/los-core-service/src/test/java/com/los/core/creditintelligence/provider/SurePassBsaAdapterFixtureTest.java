package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassBsaAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SurePassBsaAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SurePassBsaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SurePassBsaAdapter(mapper);
    }

    @Test
    void loadsFixture_supportsExtract_observationsAndHashedAccount() throws Exception {
        JsonNode payload = load("provider-fixtures/surepass/bsa/bsa_minimal.json");
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.observations()).isNotEmpty();
        assertThat(result.parserVersion()).isNotBlank();

        Map<?, ?> accountFact = result.facts().stream()
                .filter(f -> "BANK_ACCOUNT".equals(f.get("factType")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) accountFact.get("data");
        assertThat(data).containsKey("accountNumberHash");
        assertThat(data).doesNotContainKey("account_number");
        assertThat(result.observations().stream()
                .anyMatch(o -> o.containsKey("observationCode") || o.containsKey("value")
                        || "analysis_result".equals(String.valueOf(o.get("observationCode"))))).isTrue();
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
