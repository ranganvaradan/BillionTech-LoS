package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassTisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SurePassTisAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SurePassTisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SurePassTisAdapter(mapper);
    }

    @Test
    void loadsFixture_reportedProcessedAcceptedDistinct() throws Exception {
        JsonNode payload = load("provider-fixtures/surepass/tis/tis_amounts_distinct.json");
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.parserVersion()).isNotBlank();

        Map<?, ?> amountRow = result.facts().stream()
                .filter(f -> "TIS_AMOUNT_ROW".equals(f.get("factType")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) amountRow.get("data");
        Object reported = data.get("reportedBySource");
        Object processed = data.get("processedBySystem");
        Object accepted = data.get("acceptedByTaxpayer");
        assertThat(reported).isNotNull();
        assertThat(processed).isNotNull();
        assertThat(accepted).isNotNull();
        // Fixture deliberately sets accepted != reported/processed
        assertThat(accepted).isNotEqualTo(reported);
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
