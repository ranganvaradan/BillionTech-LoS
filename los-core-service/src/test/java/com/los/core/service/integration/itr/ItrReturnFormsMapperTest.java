package com.los.core.service.integration.itr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItrReturnFormsMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapMetrics_usesTotalRevenueAsItrIncome_fromLatestFinancialYear() throws Exception {
        String json = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
                  "generalInformation": { "entityName": "ACME PVT LTD", "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2024-25",
                      "financialYear": "2023-24",
                      "profitAndLoss": { "totalRevenue": 9000, "profitAfterTax": -100, "ebitda": 50, "interestExpense": 10 },
                      "balanceSheet": { "totalLiability": 1, "totalEquity": 2 },
                      "ratios": { "liquidityRatios": { "interestCoverage": 0.1 }, "solvencyRatios": { "debtEquity": 9 } }
                    },
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": {
                        "totalRevenue": 28921624,
                        "profitAfterTax": 1026039,
                        "ebitda": 8620512,
                        "interestExpense": 7498893
                      },
                      "balanceSheet": { "totalLiability": 200309002, "totalEquity": 68162682 },
                      "ratios": {
                        "liquidityRatios": { "interestCoverage": 1.136 },
                        "solvencyRatios": { "debtEquity": 2.938 }
                      }
                    }
                  ],
                  "itrFilled": [
                    { "annualYear": "2025-26", "fillingDate": "2025-10-22", "itrForm": "ITR-6", "pan": "AAHCB0052H" }
                  ],
                  "pdfDownloadLink": "https://example.com/a.pdf",
                  "excelReportLink": "https://example.com/a.xlsx"
                }
                """;
        JsonNode result = mapper.readTree(json);
        Map<String, Object> metrics = ItrReturnFormsMapper.mapMetrics(result);

        assertThat(metrics.get("itrIncome")).isEqualTo(new BigDecimal("28921624"));
        assertThat(metrics.get("grossTotalIncome")).isEqualTo(new BigDecimal("28921624"));
        assertThat(metrics.get("pat")).isEqualTo(new BigDecimal("1026039"));
        assertThat(metrics.get("ebitda")).isEqualTo(new BigDecimal("8620512"));
        assertThat(metrics.get("debtService")).isEqualTo(new BigDecimal("7498893"));
        assertThat(metrics.get("tol")).isEqualTo(new BigDecimal("200309002"));
        assertThat(metrics.get("tnw")).isEqualTo(new BigDecimal("68162682"));
        assertThat(metrics.get("panNumber")).isEqualTo("AAHCB0052H");
        assertThat(metrics.get("entityName")).isEqualTo("ACME PVT LTD");
        assertThat(metrics.get("assessmentYear")).isEqualTo("2025-26");
        assertThat(metrics.get("interestCoverage")).isEqualTo(new BigDecimal("1.136"));

        List<Map<String, String>> links = ItrReturnFormsMapper.collectDownloadLinks(result, "AAHCB0052H", "req-1");
        assertThat(links).hasSizeGreaterThanOrEqualTo(2);
        assertThat(links.stream().anyMatch(l -> l.get("url").contains("a.pdf"))).isTrue();
        assertThat(links.stream().anyMatch(l -> l.get("url").contains("a.xlsx"))).isTrue();
    }

    @Test
    void mapMetrics_handlesEmptyItrFilledAndMissingFinancialYears() throws Exception {
        String emptyFilled = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
                  "generalInformation": { "entityName": "NO FILINGS PVT LTD", "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": { "totalRevenue": 1000, "profitAfterTax": 100, "ebitda": 200, "interestExpense": 50 },
                      "balanceSheet": { "totalLiability": 5, "totalEquity": 10 },
                      "ratios": { "liquidityRatios": { "interestCoverage": 2.5 }, "solvencyRatios": { "debtEquity": 0.5 } }
                    }
                  ],
                  "itrFilled": []
                }
                """;
        Map<String, Object> withEmptyFilled = ItrReturnFormsMapper.mapMetrics(mapper.readTree(emptyFilled));
        assertThat(withEmptyFilled.get("panNumber")).isEqualTo("AAHCB0052H");
        assertThat(withEmptyFilled.get("itrIncome")).isEqualTo(new BigDecimal("1000"));
        assertThat(withEmptyFilled.get("interestCoverage")).isEqualTo(new BigDecimal("2.5"));

        String noArrays = """
                {
                  "formDetails": { "assessmentYear": "2024-25", "formName": "ITR-3" },
                  "generalInformation": { "entityName": "SPARSE", "entityPan": "ABCDE1234F" }
                }
                """;
        Map<String, Object> sparse = ItrReturnFormsMapper.mapMetrics(mapper.readTree(noArrays));
        assertThat(sparse.get("panNumber")).isEqualTo("ABCDE1234F");
        assertThat(sparse.get("entityName")).isEqualTo("SPARSE");
        assertThat(sparse).doesNotContainKey("itrIncome");
    }

    @Test
    void maskPasswordInJson_redactsPasswordField() {
        String raw = "{\"username\":\"ABCDE0012F\",\"password\":\"super-secret\",\"consent\":\"Y\"}";
        String masked = KarzaItrReturnFormsClient.maskPasswordInJson(raw);
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).doesNotContain("super-secret");
        assertThat(masked).contains("ABCDE0012F");
    }
}
