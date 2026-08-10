package com.los.core.creditintelligence.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.tax.provider.KarzaItrCanonicalExtractor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Income-head semantics: totalRevenue maps to salesTurnover / business turnover only.
 * businessProfessionIncome, grossTotalIncome, totalIncome must not be copies of revenue.
 */
class ItrIncomeSemanticsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onlyTotalRevenue_leavesIncomeHeadsNull() throws Exception {
        String json = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
                  "generalInformation": { "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": { "totalRevenue": 28921624, "profitAfterTax": 100 }
                    }
                  ]
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var r = KarzaItrCanonicalExtractor.extract(result).returns().get(0);

        assertThat(r.business().salesTurnover()).isEqualByComparingTo("28921624");
        assertThat(r.business().grossReceipts()).isEqualByComparingTo("28921624");
        assertThat(r.income().businessProfessionIncome()).isNull();
        assertThat(r.income().grossTotalIncome()).isNull();
        assertThat(r.income().totalIncome()).isNull();
    }

    @Test
    void explicitIncomeHeadsRemainDistinctFromRevenue() throws Exception {
        String json = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
                  "generalInformation": { "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": {
                        "totalRevenue": 81000000,
                        "businessProfessionIncome": 5000000,
                        "grossTotalIncome": 4800000,
                        "totalIncome": 4200000,
                        "profitAfterTax": 1026039
                      }
                    }
                  ]
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        var r = KarzaItrCanonicalExtractor.extract(result).returns().get(0);

        assertThat(r.business().salesTurnover()).isEqualByComparingTo("81000000");
        assertThat(r.income().businessProfessionIncome()).isEqualByComparingTo("5000000");
        assertThat(r.income().grossTotalIncome()).isEqualByComparingTo("4800000");
        assertThat(r.income().totalIncome()).isEqualByComparingTo("4200000");
        assertThat(r.income().businessProfessionIncome())
                .isNotEqualByComparingTo(r.business().salesTurnover());
        assertThat(r.income().grossTotalIncome())
                .isNotEqualByComparingTo(r.income().totalIncome());
    }
}
