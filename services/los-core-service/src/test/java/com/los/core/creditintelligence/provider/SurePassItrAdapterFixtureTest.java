package com.los.core.creditintelligence.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassItrAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SurePassItrAdapterFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SurePassItrAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SurePassItrAdapter(mapper);
    }

    @Test
    void loadsFixture_incomeHeadsRemainDistinct() throws Exception {
        JsonNode payload = load("provider-fixtures/surepass/itr/itr_income_heads_distinct.json");
        assertThat(adapter.supports(payload)).isTrue();
        assertThat(adapter.parserVersion()).isNotBlank();

        ProviderExtractionResult result = adapter.extract(payload, req());
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.parserVersion()).isNotBlank();

        Map<?, ?> filing = result.facts().stream()
                .filter(f -> "ITR_FILING".equals(f.get("factType")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) filing.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> heads = (Map<String, Object>) data.get("incomeHeads");

        Object gti = heads.get("grossTotalIncome");
        Object totalIncome = heads.get("totalIncome");
        Object revenue = heads.get("totalRevenueFromOperations");
        assertThat(gti).isNotNull();
        assertThat(totalIncome).isNotNull();
        assertThat(revenue).isNotNull();
        assertThat(gti).isNotEqualTo(totalIncome);
        assertThat(gti).isNotEqualTo(revenue);
        assertThat(totalIncome).isNotEqualTo(revenue);
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
