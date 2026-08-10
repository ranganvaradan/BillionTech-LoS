package com.los.core.service.integration.gstanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GstAnalysisMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapMetrics_mapsTurnoverIncomeAvgGmvAndActiveFlag() throws Exception {
        String json = """
                {
                  "gstin": "29AAHCB0052H2ZV",
                  "reportType": "pdfUpload",
                  "pdfDownloadLink": "https://example.com/r.pdf",
                  "excelDownloadLink": "https://example.com/r.xlsx",
                  "profile": { "lgnm": "BILLIONLOANS", "tradeNam": "BL", "gstin": "29AAHCB0052H2ZV" },
                  "current": {
                    "financialPeriod": "072025-062026",
                    "businessSummary": {
                      "gstTurnoverCyInvVal": 46542043.96,
                      "gstTurnoverCyTaxVal": 542422
                    },
                    "averages": { "avgmonthval": 3878503.66 },
                    "transactionSummary": { "turnover": { "ttlVal": 46542043.96 } },
                    "monthWiseSummary": [
                      { "retPeriod": "042026", "gstr1": { "ttlVal": 2572330 } },
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
        JsonNode result = mapper.readTree(json);
        Map<String, Object> metrics = GstAnalysisMapper.mapMetrics(result);

        assertThat(metrics.get("gstin")).isEqualTo("29AAHCB0052H2ZV");
        assertThat(metrics.get("legalName")).isEqualTo("BILLIONLOANS");
        assertThat(((BigDecimal) metrics.get("annualGstTurnover")).compareTo(new BigDecimal("46542043.96"))).isZero();
        assertThat(((BigDecimal) metrics.get("gstIncome")).compareTo(
                new BigDecimal("3878503.66").multiply(new BigDecimal("12")).setScale(2, java.math.RoundingMode.HALF_UP))).isZero();
        assertThat(metrics.get("active90days")).isEqualTo(1);
        assertThat(metrics.get("avgGmv3m")).isNotNull();

        List<Map<String, String>> links = GstAnalysisMapper.collectDownloadLinks(result, "29AAHCB0052H2ZV", "req-1");
        assertThat(links).hasSize(2);
        assertThat(links.get(0).get("fileName")).contains("GST_ANALYSIS_");
        assertThat(links.get(0).get("url")).contains("example.com/r.pdf");
    }
}
