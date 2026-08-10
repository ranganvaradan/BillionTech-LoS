package com.los.core.creditintelligence.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.tax.domain.ItrForm;
import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.provider.KarzaItrCanonicalExtractor;
import com.los.core.service.integration.itr.ItrReturnFormsMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KarzaItrCanonicalExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String ITR6_MULTI_YEAR = """
            {
              "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
              "generalInformation": { "entityName": "ACME PVT LTD", "entityPan": "AAHCB0052H" },
              "financialInformation": [
                {
                  "assessmentYear": "2024-25",
                  "financialYear": "2023-24",
                  "profitAndLoss": { "totalRevenue": 9000, "profitAfterTax": -100, "ebitda": 50, "interestExpense": 10 },
                  "balanceSheet": { "totalLiability": 1, "totalEquity": 2 }
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
                  "balanceSheet": { "totalLiability": 200309002, "totalEquity": 68162682 }
                }
              ],
              "itrFilled": [
                { "annualYear": "2025-26", "fillingDate": "2025-10-22", "itrForm": "ITR-6", "pan": "AAHCB0052H" }
              ]
            }
            """;

    @Test
    void extractsItr6FullMultiYear_withoutInventingZeros() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(ITR6_MULTI_YEAR, Map.class);
        var extracted = KarzaItrCanonicalExtractor.extract(Map.of("result", result));

        assertThat(extracted.parserVersion()).isEqualTo(TaxConstants.KARZA_ITR_PARSER_V1);
        assertThat(extracted.returns()).hasSize(2);
        assertThat(extracted.aisAvailable()).isFalse();
        assertThat(extracted.form26AsAvailable()).isFalse();

        var latest = extracted.returns().stream()
                .filter(r -> "2025-26".equals(r.assessmentYear()))
                .findFirst().orElseThrow();
        assertThat(latest.itrForm()).isEqualTo(ItrForm.ITR_6);
        assertThat(latest.panLast4()).isEqualTo("052H");
        assertThat(latest.panHash()).isNotBlank();
        assertThat(latest.business().salesTurnover()).isEqualByComparingTo("28921624");
        assertThat(latest.business().profitAfterTax()).isEqualByComparingTo("1026039");
        assertThat(latest.business().ebitda()).isEqualByComparingTo("8620512");
        assertThat(latest.business().financeCost()).isEqualByComparingTo("7498893");
        assertThat(latest.business().totalLiabilities()).isEqualByComparingTo("200309002");
        assertThat(latest.business().netWorth()).isEqualByComparingTo("68162682");
        // totalRevenue → salesTurnover only; income heads stay null unless explicit
        assertThat(latest.income().businessProfessionIncome()).isNull();
        assertThat(latest.income().grossTotalIncome()).isNull();
        assertThat(latest.income().totalIncome()).isNull();
        assertThat(latest.income().salaryIncome()).isNull();

        // Production mapper still works on same shape
        assertThat(ItrReturnFormsMapper.mapMetrics(mapper.readTree(ITR6_MULTI_YEAR)).get("itrIncome"))
                .isEqualTo(new BigDecimal("28921624"));
    }

    @Test
    void extractsItr4Presumptive() throws Exception {
        String json = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-4" },
                  "generalInformation": { "entityPan": "ABCDE1234F" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": { "totalRevenue": 500000 }
                    }
                  ],
                  "itrFilled": [
                    { "annualYear": "2025-26", "itrForm": "ITR-4", "pan": "ABCDE1234F", "section": "44AD" }
                  ]
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var extracted = KarzaItrCanonicalExtractor.extract(result);
        assertThat(extracted.returns()).hasSize(1);
        var r = extracted.returns().get(0);
        assertThat(r.presumptive()).isTrue();
        assertThat(r.presumptiveIncome()).isNotNull();
        assertThat(r.presumptiveIncome().applicableSection()).contains("44AD");
        assertThat(r.business().balanceSheetPresent()).isFalse();
        assertThat(r.business().totalLiabilities()).isNull();
    }

    @Test
    void missingBalanceSheet_leavesBsNull() throws Exception {
        String json = """
                {
                  "formDetails": { "formName": "ITR-6" },
                  "generalInformation": { "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": { "totalRevenue": 1000, "profitAfterTax": 100, "ebitda": 200 }
                    }
                  ]
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var r = KarzaItrCanonicalExtractor.extract(result).returns().get(0);
        assertThat(r.business().salesTurnover()).isEqualByComparingTo("1000");
        assertThat(r.business().netWorth()).isNull();
        assertThat(r.business().totalLiabilities()).isNull();
        assertThat(r.business().balanceSheetPresent()).isFalse();
    }

    @Test
    void extractsEmptyAisWhenPresent() throws Exception {
        String json = """
                {
                  "formDetails": { "formName": "ITR-6", "assessmentYear": "2025-26" },
                  "generalInformation": { "entityPan": "AAHCB0052H" },
                  "financialInformation": [],
                  "ais": {}
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var extracted = KarzaItrCanonicalExtractor.extract(result);
        assertThat(extracted.aisAvailable()).isTrue();
        assertThat(extracted.aisSummaries()).isNotEmpty();
    }
}
