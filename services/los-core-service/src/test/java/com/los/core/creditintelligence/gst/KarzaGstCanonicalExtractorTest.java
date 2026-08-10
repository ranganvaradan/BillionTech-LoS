package com.los.core.creditintelligence.gst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.gst.domain.GstReturnType;
import com.los.core.creditintelligence.gst.provider.KarzaGstCanonicalExtractor;
import com.los.core.service.integration.gstanalysis.GstAnalysisMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KarzaGstCanonicalExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsRegistrationPeriodsAndGstr3bFromSampleLikeMapperTest() throws Exception {
        String json = """
                {
                  "gstin": "29AAHCB0052H2ZV",
                  "reportType": "pdfUpload",
                  "profile": { "lgnm": "BILLIONLOANS", "tradeNam": "BL", "gstin": "29AAHCB0052H2ZV", "sts": "Active" },
                  "current": {
                    "financialPeriod": "072025-062026",
                    "monthWiseSummary": [
                      { "retPeriod": "042026", "gstr1": { "ttlVal": 2572330 }, "gstr3b": { "ttlVal": 2500000 } },
                      { "retPeriod": "052026", "gstr1": { "ttlVal": 1450507 } },
                      { "retPeriod": "062026", "gstr1": { "ttlVal": 673431 } }
                    ],
                    "filingStatus": [
                      {
                        "retPeriod": "062026",
                        "status": [
                          { "rtntype": "GSTR1", "status": "Filed" },
                          { "rtntype": "GSTR3B", "status": "Filed" }
                        ]
                      }
                    ]
                  }
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        Map<String, Object> parsed = Map.of("fullResponse", Map.of("result", result), "gstin", "29AAHCB0052H2ZV");

        var extracted = KarzaGstCanonicalExtractor.extract(parsed);
        assertThat(extracted.parserVersion()).isEqualTo(KarzaGstCanonicalExtractor.PARSER_VERSION);
        assertThat(extracted.registrations()).hasSize(1);
        assertThat(extracted.registrations().get(0).gstin()).isEqualTo("29AAHCB0052H2ZV");
        assertThat(extracted.registrations().get(0).legalName()).isEqualTo("BILLIONLOANS");
        assertThat(extracted.registrations().get(0).registrationStatus()).isEqualTo("ACTIVE");

        assertThat(extracted.periods()).isNotEmpty();
        assertThat(extracted.periods().stream()
                .anyMatch(p -> GstReturnType.GSTR1.name().equals(p.returnType())
                        && "2026-04".equals(p.periodYyyyMm())
                        && p.turnoverPresent()
                        && p.taxableTurnover().compareTo(new BigDecimal("2572330")) == 0)).isTrue();
        assertThat(extracted.periods().stream()
                .anyMatch(p -> GstReturnType.GSTR3B.name().equals(p.returnType())
                        && "2026-04".equals(p.periodYyyyMm())
                        && p.turnoverPresent())).isTrue();

        // Production mapper still works on same shape
        assertThat(GstAnalysisMapper.mapMetrics(mapper.readTree(json)).get("gstin"))
                .isEqualTo("29AAHCB0052H2ZV");
    }

    @Test
    void doesNotInventAmountsWhenTtlValAbsent() throws Exception {
        String json = """
                {
                  "gstin": "29AAHCB0052H2ZV",
                  "profile": { "gstin": "29AAHCB0052H2ZV" },
                  "current": {
                    "monthWiseSummary": [
                      { "retPeriod": "042026", "gstr1": { } }
                    ],
                    "filingStatus": []
                  }
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var extracted = KarzaGstCanonicalExtractor.extract(Map.of("result", result));
        assertThat(extracted.periods().stream()
                .filter(p -> "2026-04".equals(p.periodYyyyMm()))
                .noneMatch(KarzaGstCanonicalExtractor.ExtractedPeriod::turnoverPresent)).isTrue();
    }
}
